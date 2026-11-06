package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

class PendingBatchInputsTest {
    private static Field keyRegistryField;
    private static Object previousKeyRegistry;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void bootstrap() throws ReflectiveOperationException {
        MinecraftTestBootstrap.initialize();
        // Old GenericStack NBT decodes through AE2's key registry. Plain JUnit does
        // not run AE2's NewRegistryEvent, so supply real item/fluid key types here.
        keyRegistryField = appeng.api.stacks.AEKeyTypesInternal.class.getDeclaredField("registry");
        keyRegistryField.setAccessible(true);
        previousKeyRegistry = keyRegistryField.get(null);
        var constructor = net.minecraftforge.registries.ForgeRegistry.class.getDeclaredConstructor(
                net.minecraftforge.registries.RegistryManager.class, ResourceLocation.class,
                net.minecraftforge.registries.RegistryBuilder.class);
        constructor.setAccessible(true);
        var registry = (net.minecraftforge.registries.ForgeRegistry<appeng.api.stacks.AEKeyType>)
                constructor.newInstance(net.minecraftforge.registries.RegistryManager.ACTIVE,
                        ResourceLocation.tryParse("ae2ltpp_test:pending_input_key_types"),
                        new net.minecraftforge.registries.RegistryBuilder<appeng.api.stacks.AEKeyType>());
        registry.register(appeng.api.stacks.AEKeyType.items().getId(), appeng.api.stacks.AEKeyType.items());
        registry.register(appeng.api.stacks.AEKeyType.fluids().getId(), appeng.api.stacks.AEKeyType.fluids());
        java.util.function.Supplier<net.minecraftforge.registries.IForgeRegistry<appeng.api.stacks.AEKeyType>>
                supplier = () -> registry;
        keyRegistryField.set(null, supplier);
    }

    @AfterAll
    static void restoreKeyRegistry() throws IllegalAccessException {
        if (keyRegistryField != null) keyRegistryField.set(null, previousKeyRegistry);
    }

    private static PendingBatchInputs.Delivery delivery(GenericStack stack) {
        return new PendingBatchInputs.Delivery(new ResourceLocation("ae2ltpp", "mekmm"),
                new ResourceLocation("minecraft", "overworld"), new BlockPos(1, 2, 3),
                Direction.NORTH, new ResourceLocation("minecraft", "stone"), stack);
    }

    private static PendingBatchInputs.Delivery item(long amount) {
        return delivery(new GenericStack(AEItemKey.of(Items.IRON_INGOT), amount));
    }

    @Test
    void partialReceiptsRetryOnlyTheRemainder() {
        var queue = new PendingBatchInputs();
        queue.adopt(List.of(item(100)));
        queue.deliver(d -> 30);
        assertEquals(List.of(item(70)), queue.snapshot());
        queue.deliver(d -> 0);
        assertEquals(List.of(item(70)), queue.snapshot());
        queue.deliver(d -> d.stack().amount());
        assertFalse(queue.hasPending());
    }

    @Test
    void itemsAndFluidsRetainTheirRouteAcrossReloads() {
        var deliveries = List.of(item(130), delivery(new GenericStack(AEFluidKey.of(Fluids.WATER), 10000)));
        var queue = new PendingBatchInputs();
        queue.adopt(deliveries);
        var tag = new CompoundTag();
        queue.writeToNBT(tag);
        var restored = new PendingBatchInputs();
        restored.readFromNBT(tag);
        assertEquals(deliveries, restored.snapshot());
        restored.deliver(d -> d.stack().amount());
        restored.writeToNBT(tag);
        queue.readFromNBT(tag);
        assertFalse(queue.hasPending());
    }

    @Test
    void ambiguousInsertionIsNeverReplayedAndOtherPortsRemainOwned() {
        var queue = new PendingBatchInputs();
        var remaining = delivery(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000));
        queue.adopt(List.of(item(64), remaining));
        assertThrows(IllegalStateException.class, () -> queue.deliver(d -> {
            throw new IllegalStateException("injected failure after insertion");
        }));
        assertEquals(List.of(remaining), queue.snapshot());
        assertEquals(List.of(item(64)), queue.uncertainSnapshot());
        var tag = new CompoundTag();
        queue.writeToNBT(tag);
        var restored = new PendingBatchInputs();
        restored.readFromNBT(tag);
        assertEquals(queue.uncertainSnapshot(), restored.uncertainSnapshot());
        restored.deliver(d -> d.stack().amount());
        assertTrue(restored.snapshot().isEmpty());
        assertTrue(restored.hasPending(), "Uncertain transfers must still prevent a new craft");
        assertThrows(IllegalStateException.class, () -> restored.adopt(List.of(item(1))));
    }

    @Test
    void controllerOwnershipSurvivesPartialDeliveryAndReload() {
        var queue = new PendingBatchInputs();
        var owner = new PendingBatchInputs.Ownership(new ResourceLocation("ae2ltpp:mekmm"),
                new ResourceLocation("minecraft:overworld"), new BlockPos(9, 4, 2),
                new ResourceLocation("mekmm:electrolytic_separator"));
        queue.adopt(List.of(item(100)), owner);
        queue.deliver(d -> 20);
        var tag = new CompoundTag();
        queue.writeToNBT(tag);
        var restored = new PendingBatchInputs();
        restored.readFromNBT(tag);
        assertEquals(owner, restored.ownership());
        assertEquals(List.of(item(80)), restored.snapshot());
        restored.deliver(d -> d.stack().amount());
        assertNull(restored.ownership());
        restored.writeToNBT(tag);
        queue.readFromNBT(tag);
        assertNull(queue.ownership());
        assertFalse(queue.hasPending());
    }

    @Test
    void portPlanMustExactlyMatchPaidInputs() {
        var paid = new appeng.api.stacks.KeyCounter();
        paid.add(AEItemKey.of(Items.IRON_INGOT), 2);
        var inputs = new appeng.api.stacks.KeyCounter[]{paid};
        assertTrue(PendingBatchInputs.matchesInputs(List.of(item(2)), inputs));
        assertFalse(PendingBatchInputs.matchesInputs(List.of(item(1)), inputs));
        assertFalse(PendingBatchInputs.matchesInputs(List.of(item(3)), inputs));
        assertFalse(PendingBatchInputs.matchesInputs(List.of(delivery(
                new GenericStack(AEFluidKey.of(Fluids.WATER), 2))), inputs));
        assertFalse(PendingBatchInputs.matchesInputs(List.of(), inputs));
        assertFalse(PendingBatchInputs.matchesInputs(List.of(item(Long.MAX_VALUE), item(1)), inputs));
    }

    @Test
    void rejectsDuplicateOwnershipAndOverflowBeforeAdoption() {
        var queue = new PendingBatchInputs();
        queue.adopt(List.of(item(10)));
        assertThrows(IllegalStateException.class, () -> queue.adopt(List.of(item(20))));
        assertEquals(List.of(item(10)), queue.snapshot());
        assertThrows(ArithmeticException.class, () -> PendingBatchInputs.scale(List.of(item(Long.MAX_VALUE)), 2));
        assertTrue(PendingBatchInputs.scale(List.of(item(1)), 0).isEmpty());
        assertEquals(List.of(item(130)), PendingBatchInputs.scale(List.of(item(1)), 130));
    }

    @Test
    void randomizedBackpressureAndReloadsConservePhysicalInputs() {
        int trials = Integer.getInteger("ae2ltpp.stress.trials", 10000);
        int seeds = Integer.getInteger("ae2ltpp.stress.seeds", 1);
        for (int seed = 0; seed < seeds; seed++) {
            var random = new Random(0x580e3e5L + seed);
            var queue = new PendingBatchInputs();
            long produced = 0;
            var delivered = new AtomicLong();
            for (int i = 0; i < trials; i++) {
                if (!queue.hasPending()) {
                    long amount = 1 + random.nextInt(100000);
                    queue.adopt(List.of(item(amount)));
                    produced += amount;
                }
                queue.deliver(d -> {
                    long accepted = random.nextInt(4) == 0 ? 0
                            : Math.min(d.stack().amount(), 1 + random.nextInt(8192));
                    delivered.addAndGet(accepted);
                    return accepted;
                });
                if (i % 97 == 0) {
                    var tag = new CompoundTag();
                    queue.writeToNBT(tag);
                    var restored = new PendingBatchInputs();
                    restored.readFromNBT(tag);
                    queue = restored;
                }
                long pending = queue.snapshot().stream().mapToLong(d -> d.stack().amount()).sum();
                assertEquals(produced, delivered.get() + pending, "seed=" + seed + ", trial=" + i);
            }
            queue.deliver(d -> { delivered.addAndGet(d.stack().amount()); return d.stack().amount(); });
            assertFalse(queue.hasPending());
            assertEquals(produced, delivered.get());
        }
    }

    @Test
    void oldAlphaQueuesPreserveKnownAndUncertainInputsAndNormalizeMekRoute() {
        var known = item(130);
        var uncertain = delivery(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000));
        var legacy = legacyAlphaQueue(known, uncertain);
        var restored = new PendingBatchInputs();
        restored.readFromNBT(legacy);
        var adapter = com.moakiee.ae2lt.packaged.item.AdapterIds.MEKMM;
        assertEquals(List.of(new PendingBatchInputs.Delivery(adapter, known.dimension(), known.pos(),
                known.face(), known.block(), known.stack())), restored.snapshot());
        assertEquals(List.of(new PendingBatchInputs.Delivery(adapter, uncertain.dimension(), uncertain.pos(),
                uncertain.face(), uncertain.block(), uncertain.stack())), restored.uncertainSnapshot());
        assertEquals(adapter, restored.ownership().adapter());
        restored.deliver(d -> d.stack().amount());
        assertTrue(restored.snapshot().isEmpty());
        assertTrue(restored.hasPending(), "Ambiguous old transfers cannot be replayed or refunded");
        restored.writeToNBT(legacy);
        assertFalse(legacy.contains("batch_inputs"));
        var again = new PendingBatchInputs();
        again.readFromNBT(legacy);
        assertTrue(again.snapshot().isEmpty());
        assertEquals(restored.uncertainSnapshot(), again.uncertainSnapshot());
    }

    @Test
    void currentQueueWinsOverStaleAlphaDataAndClearingCannotResurrectInputs() {
        var tag = legacyAlphaQueue(item(130), null);
        var oldData = tag.getCompound("batch_inputs").copy();
        var current = new PendingBatchInputs();
        current.adopt(List.of(item(5)));
        current.writeToNBT(tag);
        tag.put("batch_inputs", oldData);
        var restored = new PendingBatchInputs();
        restored.readFromNBT(tag);
        assertEquals(List.of(item(5)), restored.snapshot());
        restored.clear();
        restored.writeToNBT(tag);
        assertFalse(tag.contains("batch_inputs"));
        current.readFromNBT(tag);
        assertFalse(current.hasPending(), "Cleared inputs must not reappear from a stale legacy tag");
    }

    private static CompoundTag legacyAlphaQueue(PendingBatchInputs.Delivery known,
                                                PendingBatchInputs.Delivery uncertain) {
        var data = new CompoundTag();
        var ports = new net.minecraft.nbt.ListTag();
        ports.add(legacyAlphaDelivery(known));
        data.put("ports", ports);
        var unknown = new net.minecraft.nbt.ListTag();
        if (uncertain != null) unknown.add(legacyAlphaDelivery(uncertain));
        data.put("uncertain", unknown);
        data.putBoolean("initialized", true);
        var owner = new CompoundTag();
        owner.putString("adapter", "ae2ltpp:mekmm");
        owner.putString("dimension", known.dimension().toString());
        owner.putLong("pos", known.pos().asLong());
        owner.putString("block", known.block().toString());
        data.put("owner", owner);
        var parent = new CompoundTag();
        parent.put("batch_inputs", data);
        return parent;
    }

    private static CompoundTag legacyAlphaDelivery(PendingBatchInputs.Delivery delivery) {
        var tag = new CompoundTag();
        tag.putString("adapter", delivery.adapter().toString());
        tag.putString("dimension", delivery.dimension().toString());
        tag.putLong("pos", delivery.pos().asLong());
        tag.putInt("face", delivery.face().get3DDataValue());
        tag.putString("block", delivery.block().toString());
        tag.put("stack", GenericStack.writeTag(delivery.stack()));
        return tag;
    }
}
