package com.moakiee.ae2lt.packaged.probe;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingOutputBuffer;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs only in the separate ownership-test server, never shipped in the product JAR. */
@Mod("ownership_probe")
public class ProviderOwnershipProbe {
    private int tick;
    private final BlockPos pos = new BlockPos(0, 80, 0);
    private final BlockPos power = new BlockPos(0, 80, 1);
    private final boolean reload = Boolean.getBoolean("ownershipProbeReload");

    public ProviderOwnershipProbe() { NeoForge.EVENT_BUS.addListener(this::tick); }

    private void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var level = server.overworld();
        ++tick;
        try {
            if (reload) {
                if (tick == 60) {
                    var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
                    check(pending(provider).snapshot().equals(List.of(gold(64))), "disk reload pending quantities");
                    check(provider.getLogic().getReturnInv().isEmpty(), "unpowered buffer stayed pending");
                    level.setBlockAndUpdate(power, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    log("disk reload preserved exact queued output");
                }
                if (tick == 140) {
                    var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
                    check(!pending(provider).hasPending(), "recovered queue drained");
                    check(provider.getLogic().getReturnInv().getAmount(0) == 64, "delivered once after restart");
                    log("PASS restart and power restoration");
                    server.halt(false);
                }
                return;
            }
            if (tick == 20) {
                level.setChunkForced(0, 0, true);
                level.setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
            }
            if (tick == 60) {
                var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
                AdapterRefactorProbe.run(level);
                var expected = new ArrayList<>(testDispatch());
                expected.add(gold(64));
                expected.add(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000));
                enqueue(provider, expected);
                var nbt = provider.saveWithFullMetadata(level.registryAccess());
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
                provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
                provider.loadWithComponents(nbt, level.registryAccess());
                check(pending(provider).snapshot().equals(expected), "block NBT reload item and fluid amounts");
                var old = new CompoundTag();
                provider.getLogic().readFromNBT(old, level.registryAccess());
                check(!pending(provider).hasPending(), "old NBT without queue remains valid");
                enqueue(provider, List.of(gold(64)));
                var drops = new ArrayList<ItemStack>();
                provider.getLogic().addDrops(drops);
                check(drops.stream().filter(s -> s.is(Items.GOLD_INGOT)).mapToLong(ItemStack::getCount).sum() == 64,
                        "pending items included in block drops");
                check(pending(provider).hasPending(), "drop collection is non-destructive");
                provider.getLogic().clearContent();
                check(!pending(provider).hasPending(), "clear content clears queue");
                var inv = provider.getLogic().getReturnInv();
                inv.insert(0, AEItemKey.of(Items.GOLD_INGOT), Long.MAX_VALUE - 3, Actionable.MODULATE);
                check(inv.insert(0, AEItemKey.of(Items.GOLD_INGOT), 10, Actionable.SIMULATE) == 3, "overflow simulation");
                check(inv.insert(0, AEItemKey.of(Items.GOLD_INGOT), 10, Actionable.MODULATE) == 3, "overflow receipt");
                check(inv.getAmount(0) == Long.MAX_VALUE, "overflow does not wrap");
                inv.clear();
                var fillers = BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR && i != Items.GOLD_INGOT)
                        .limit(inv.size()).toList();
                for (var item : fillers) inv.insert(0, AEItemKey.of(item), 1, Actionable.MODULATE);
                enqueue(provider, List.of(gold(64)));
                level.setBlockAndUpdate(power, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                log("dispatch, NBT, legacy load, drops and overflow passed");
            }
            if (tick == 160) {
                var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
                check(provider.getMainNode().isActive(), "full buffer test has powered network");
                check(pending(provider).snapshot().equals(List.of(gold(64))), "full return inventory retains outputs");
                provider.getLogic().getReturnInv().setStack(0, null);
                log("full return inventory retained queued output");
            }
            if (tick == 200) {
                var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
                check(!pending(provider).hasPending(), "free slot drains pending queue");
                check(provider.getLogic().getReturnInv().getAmount(0) == 64, "free slot received exact quantity");
                provider.getLogic().getReturnInv().clear();
                level.setBlockAndUpdate(power, Blocks.AIR.defaultBlockState());
            }
            if (tick == 260) {
                var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
                check(!provider.getMainNode().isActive(), "power loss reached inactive state");
                enqueue(provider, List.of(gold(64)));
            }
            if (tick == 320) {
                var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
                check(pending(provider).snapshot().equals(List.of(gold(64))), "power loss preserves queue");
                server.saveEverything(false, true, true);
                log("PASS prepare persistent restart case");
                server.halt(false);
            }
        } catch (Throwable failure) {
            log("FAIL tick=" + tick);
            failure.printStackTrace();
            server.halt(false);
        }
    }

    private List<GenericStack> testDispatch() {
        var calls = new AtomicInteger();
        var commits = new AtomicInteger();
        var refunds = new ArrayList<GenericStack>();
        var stacks = List.of(gold(10), new GenericStack(AEItemKey.of(Items.IRON_INGOT), 20),
                new GenericStack(AEItemKey.of(Items.DIAMOND), 30));
        BiFunction<GenericStack, Actionable, Long> insertion = (stack, mode) -> {
            if (mode == Actionable.SIMULATE) return stack.amount();
            int call = calls.incrementAndGet();
            return call == 1 ? stack.amount() : 7L;
        };
        var later = new GenericStack(AEItemKey.of(Items.EMERALD), 40);
        var plan = new DispatchPlan(List.of(target(stacks, insertion), target(List.of(later), (stack, mode) -> {
            if (mode == Actionable.MODULATE) throw new AssertionError("unvisited target was inserted");
            return stack.amount();
        })), commits::incrementAndGet);
        check(DispatchExecutor.execute(plan, null, refunds::addAll).success(), "partial ownership accepted");
        check(refunds.equals(List.of(new GenericStack(stacks.get(1).what(), 13), stacks.get(2), later)), "all trailing refunds");
        check(commits.get() == 0 && calls.get() == 2, "partial plan never commits or visits later inputs");
        var partialRefunds = List.copyOf(refunds);
        refunds.clear();
        var simulatedRejection = new DispatchPlan(List.of(target(stacks, (s, mode) -> {
            if (mode == Actionable.MODULATE) throw new AssertionError("simulation rejection reached execution");
            return 0L;
        })), commits::incrementAndGet);
        check(DispatchExecutor.execute(simulatedRejection, null, refunds::addAll).failure(), "preflight rejection");
        var rejected = new DispatchPlan(List.of(target(stacks, (s, mode) -> mode == Actionable.SIMULATE ? s.amount() : 0L)), null);
        check(DispatchExecutor.execute(rejected, null, refunds::addAll).failure() && refunds.isEmpty(), "zero acceptance leaves ownership to CPU");
        var complete = new DispatchPlan(List.of(target(stacks, (s, mode) -> s.amount())), commits::incrementAndGet);
        check(DispatchExecutor.execute(complete, null, refunds::addAll).success() && commits.get() == 1, "full commit once");
        var brokenHook = new DispatchPlan(complete.targets(), () -> { throw new IllegalStateException("probe expected hook failure"); });
        check(DispatchExecutor.execute(brokenHook, null, refunds::addAll).success(), "post-commit failure cannot refund transferred inputs");
        return partialRefunds;
    }

    private TargetSlot target(List<GenericStack> stacks, BiFunction<GenericStack, Actionable, Long> insert) {
        return new TargetSlot(null, pos, null, stacks, InsertionStrategy.CUSTOM, insert);
    }
    private static GenericStack gold(long amount) { return new GenericStack(AEItemKey.of(Items.GOLD_INGOT), amount); }
    private static PendingOutputBuffer pending(PackagedPatternProviderBlockEntity provider) throws Exception {
        var field = PackagedPatternProviderLogic.class.getDeclaredField("pendingOutputs");
        field.setAccessible(true);
        return (PendingOutputBuffer) field.get(provider.getLogic());
    }
    private static void enqueue(PackagedPatternProviderBlockEntity provider, List<GenericStack> stacks) throws Exception {
        var method = PackagedPatternProviderLogic.class.getDeclaredMethod("enqueuePendingOutputs", List.class);
        method.setAccessible(true);
        method.invoke(provider.getLogic(), stacks);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void log(String message) {
        System.out.println("OWNERSHIP_PROBE " + message);
        if (message.startsWith("PASS") || message.startsWith("FAIL")) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("ownership-result.txt"), message);
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }
    }
}
