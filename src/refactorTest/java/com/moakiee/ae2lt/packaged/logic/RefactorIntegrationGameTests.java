package com.moakiee.ae2lt.packaged.logic;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingBatchInputs;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingOutputBuffer;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Full-profile checks for the interaction between the ownership refactor and ordinary batching. */
@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class RefactorIntegrationGameTests {
    @GameTest(template = "mek_routing_test")
    public static void legacyBatchNbtMigratesOnceWithComponentsAndPendingInputs(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        var logic = logic(level, pos);
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("preserve exact legacy component"));
        var outputs = List.of(new GenericStack(AEItemKey.of(named), 17),
                new GenericStack(AEFluidKey.of(Fluids.WATER), 1234));
        var legacyOutputs = new ListTag();
        outputs.forEach(stack -> legacyOutputs.add(GenericStack.writeTag(level.registryAccess(), stack)));
        var legacy = new CompoundTag();
        legacy.put("ae2ltpp_virtual_outputs", legacyOutputs);
        var pending = new PendingBatchInputs();
        pending.adopt(List.of(new PendingBatchInputs.Delivery(id("ae2ltpp:mekmm"),
                level.dimension().location(), pos.east(), Direction.NORTH, id("mekanism:bounding_block"),
                item("minecraft:gold_ingot", 23))));
        legacy.put("ae2ltpp_batch_inputs", pending.writeToNBT(level.registryAccess()));
        logic.readFromNBT(legacy, level.registryAccess());
        helper.assertTrue(pendingOutputs(logic).snapshot().equals(outputs), "legacy output keys, components and amounts migrate exactly");
        helper.assertTrue(logic.isBusy(), "old batch input ownership remains pending");
        var drops = new ArrayList<ItemStack>(); logic.addDrops(drops);
        helper.assertTrue(drops.stream().filter(s -> ItemStack.isSameItemSameComponents(s, named)).mapToInt(ItemStack::getCount).sum() == 17,
                "migrated pending drop keeps its component-bearing key");
        helper.assertTrue(drops.stream().filter(s -> s.is(Items.GOLD_INGOT)).mapToInt(ItemStack::getCount).sum() == 23,
                "old pending batch inputs remain recoverable through removal");
        logic.writeToNBT(legacy, level.registryAccess());
        helper.assertTrue(!legacy.contains("ae2ltpp_virtual_outputs"), "saving a reused tag removes the obsolete queue");
        // A stale old field must not double the canonical queue when both keys are present.
        legacy.put("ae2ltpp_virtual_outputs", legacyOutputs);
        logic.readFromNBT(legacy, level.registryAccess());
        logic.readFromNBT(legacy, level.registryAccess());
        helper.assertTrue(pendingOutputs(logic).snapshot().equals(outputs), "canonical outputs win and repeated loads never duplicate");
        logic.clearContent();
        helper.assertTrue(!pendingOutputs(logic).hasPending() && !logic.isBusy(), "clear releases both pending queues");
        System.out.println("PP_REFACTOR_MIGRATION_PASS components=item+fluid pendingInputs=23 canonical=once");
        helper.succeed();
    }

    @GameTest(template = "mek_routing_test")
    public static void throwingInsertionNeverRefundsPossiblyAcceptedInputs(GameTestHelper helper) {
        var stacks = List.of(item("minecraft:iron_ingot", 10), item("minecraft:gold_ingot", 20), item("minecraft:diamond", 30));
        var calls = new AtomicInteger();
        var commits = new AtomicInteger();
        var retained = new ArrayList<GenericStack>();
        var machineContents = new ArrayList<GenericStack>();
        var plan = new DispatchPlan(List.of(new TargetSlot(null, BlockPos.ZERO, null, stacks, InsertionStrategy.CUSTOM,
                (stack, mode) -> {
                    if (mode == Actionable.SIMULATE) return stack.amount();
                    machineContents.add(stack);
                    if (calls.incrementAndGet() == 2) throw new IllegalStateException("injected failure after accepting second stack");
                    return stack.amount();
                })), commits::incrementAndGet);
        var result = DispatchExecutor.execute(plan, null, retained::addAll);
        helper.assertTrue(result.success(), "first accepted stack transfers dispatch ownership away from the CPU");
        helper.assertTrue(retained.equals(stacks.subList(2, 3)) && calls.get() == 2 && commits.get() == 0,
                "only definitely unvisited inputs are retained; throwing insertion is never refunded");
        helper.assertTrue(machineContents.equals(stacks.subList(0, 2)), "first and throwing insertions remain in the machine exactly once");
        retained.clear();
        machineContents.clear();
        var rejected = new DispatchPlan(List.of(new TargetSlot(null, BlockPos.ZERO, null, stacks,
                InsertionStrategy.CUSTOM, (stack, mode) -> {
                    if (mode == Actionable.SIMULATE) return stack.amount();
                    machineContents.add(stack);
                    throw new IllegalStateException("injected failure after accepting first stack");
                })), commits::incrementAndGet);
        helper.assertTrue(DispatchExecutor.execute(rejected, null, retained::addAll).success()
                        && retained.equals(stacks.subList(1, 3)) && machineContents.equals(stacks.subList(0, 1)),
                "a throwing first insertion also prevents CPU refunds while retaining every unvisited stack");
        System.out.println("PP_REFACTOR_DISPATCH_FAILURE_PASS throwAfterWrite=first+second duplicates=0 commits=0");
        helper.succeed();
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 160)
    public static void mekPendingInputsResumeAfterProviderReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var main = helper.absolutePos(new BlockPos(5, 2, 5));
        var pos = main.north();
        level.setBlockAndUpdate(main, BuiltInRegistries.BLOCK.get(id("mekmm:large_chemical_infuser")).defaultBlockState());
        level.setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        provider.getAdapterInv().setItemDirect(0, PPItems.MEKMM_ADAPTER.toStack());
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        var oxygen = new GenericStack(me.ramidzkh.mekae2.ae2.MekanismKey.of(mekanism.common.registries.MekanismChemicals.OXYGEN.asStack(32)), 32);
        var chlorine = new GenericStack(me.ramidzkh.mekae2.ae2.MekanismKey.of(mekanism.common.registries.MekanismChemicals.CHLORINE.asStack(32)), 32);
        var acid = new GenericStack(me.ramidzkh.mekae2.ae2.MekanismKey.of(mekanism.common.registries.MekanismChemicals.SULFURIC_ACID.asStack(64)), 64);
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(List.of(oxygen, chlorine), List.of(acid)));
        var pattern = logic.getAvailablePatterns().getFirst();
        var adapter = new com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekanismMoreMachinesAdapter();
        var binding = adapter.bind(level, main, pattern);
        var inputs = new KeyCounter[]{new KeyCounter(), new KeyCounter()};
        inputs[0].add(oxygen.what(), 32); inputs[1].add(chlorine.what(), 32);
        var plan = adapter.planWithBinding(level, main, pattern, inputs, binding.handle(), null);
        for (var port : plan.targets()) {
            level.setBlockAndUpdate(port.pos(), BuiltInRegistries.BLOCK.get(id("mekanism:bounding_block")).defaultBlockState());
            ((mekanism.common.tile.TileEntityBoundingBlock)level.getBlockEntity(port.pos())).setMainLocation(main, false);
        }
        var deliveries = PendingBatchInputs.prepare(plan, adapter);
        var first = deliveries.getFirst();
        helper.assertTrue(adapter.insertBatchInput(level, first.pos(), first.face(),
                new GenericStack(first.stack().what(), 5), Actionable.MODULATE, null) == 5,
                "five units really entered the first native tank before the provider was saved");
        var pending = new PendingBatchInputs();
        pending.adopt(List.of(first.withAmount(27), deliveries.get(1)));
        var saved = provider.saveWithFullMetadata(level.registryAccess());
        saved.put("ae2ltpp_batch_inputs", pending.writeToNBT(level.registryAccess()));
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        var reloaded = (PackagedPatternProviderBlockEntity)level.getBlockEntity(pos);
        reloaded.loadWithComponents(saved, level.registryAccess());
        helper.assertTrue(reloaded.getLogic().isBusy(), "reloaded provider owns the unfinished physical batch");
        level.setBlockAndUpdate(pos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.runAfterDelay(50, () -> {
            try {
                var machine = level.getBlockEntity(main);
                var left = (mekanism.api.chemical.IChemicalTank)machine.getClass().getField("leftTank").get(machine);
                var right = (mekanism.api.chemical.IChemicalTank)machine.getClass().getField("rightTank").get(machine);
                helper.assertTrue(left.getStored() == 32 && right.getStored() == 32, "native tanks received exactly 32 each across partial insertion and provider reload");
                helper.assertTrue(!reloaded.getLogic().isBusy(), "resumed batch has no outstanding inputs");
                System.out.println("PP_REFACTOR_MEK_RELOAD_PASS before=5 pending=27+32 after=32+32");
                helper.succeed();
            } catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
        });
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 160)
    public static void avaritiaTableBatchPreservesContainers(GameTestHelper helper) {
        assertVirtualBatch(helper, "avaritia:extreme_crafting_table", PPItems.AVARITIA_EXTREME_TABLE_ADAPTER.toStack(),
                List.of(item("minecraft:milk_bucket", 1), item("minecraft:iron_ingot", 1)), item("minecraft:emerald", 1), true);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 160)
    public static void avaritiaSmithingNativeRecipeBatches(GameTestHelper helper) {
        assertVirtualBatch(helper, "avaritia:extreme_smithing_table", PPItems.AVARITIA_EXTREME_SMITHING_ADAPTER.toStack(),
                List.of(item("avaritia:upgrade_smithing_template", 1), item("minecraft:clock", 1),
                        item("minecraft:enchanted_golden_apple", 1), item("avaritia:enhancement_core", 1),
                        item("avaritia:eternal_singularity", 1)), item("avaritia:infinity_clock", 1), false);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 160)
    public static void spiritFireNativeRecipeBatches(GameTestHelper helper) {
        assertVirtualBatch(helper, "occultism:spirit_fire", PPItems.OCCULTISM_SPIRIT_FIRE_ADAPTER.toStack(),
                List.of(item("occultism:chalk_orange_impure", 1)), item("occultism:chalk_orange", 1), false);
    }

    private static void assertVirtualBatch(GameTestHelper helper, String target, ItemStack core,
            List<GenericStack> inputs, GenericStack output, boolean buckets) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(pos.east().below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos.east(), BuiltInRegistries.BLOCK.get(id(target)).defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        provider.getAdapterInv().setItemDirect(0, core);
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(output)));
        helper.runAfterDelay(50, () -> {
            logic.updatePatterns();
            var pattern = logic.getAvailablePatterns().getFirst();
            var counters = new KeyCounter[inputs.size()];
            for (int i = 0; i < inputs.size(); i++) { counters[i] = new KeyCounter(); counters[i].add(inputs.get(i).what(), inputs.get(i).amount()); }
            boolean workbench = target.startsWith("avaritia:");
            long accepted = workbench ? 70 : 64;
            helper.assertTrue(logic.getBatchCapacity(pattern) == (workbench ? Long.MAX_VALUE : 64), "batch lane recognized: " + target);
            helper.assertTrue(logic.pushBatch(pattern, counters, 70) == 70 - accepted, "expected copies accepted on " + target);
            for (int i = 0; i < counters.length; i++) helper.assertTrue(counters[i].get(inputs.get(i).what()) == inputs.get(i).amount(), "borrowed template unchanged");
            var owned = pendingOutputs(logic).snapshot();
            helper.assertTrue(owned.contains(new GenericStack(output.what(), accepted * output.amount())), "exact batch output for " + target);
            if (buckets) helper.assertTrue(owned.contains(item("minecraft:bucket", accepted)), "Avaritia returns exact native container remainders");
            System.out.println("PP_REFACTOR_VIRTUAL_PASS target=" + target + " accepted=" + accepted + " leftover=" + (70 - accepted) + " containers=" + buckets);
            helper.succeed();
        });
    }

    private static PackagedPatternProviderLogic logic(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        return (PackagedPatternProviderLogic)((PackagedPatternProviderBlockEntity)level.getBlockEntity(pos)).getLogic();
    }
    private static PendingOutputBuffer pendingOutputs(PackagedPatternProviderLogic logic) {
        try {
            var field = PackagedPatternProviderLogic.class.getDeclaredField("pendingOutputs"); field.setAccessible(true);
            return (PendingOutputBuffer)field.get(logic);
        } catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
    }
    private static ResourceLocation id(String name) { return ResourceLocation.parse(name); }
    private static GenericStack item(String name, long amount) {
        var item = BuiltInRegistries.ITEM.get(id(name));
        if (item == Items.AIR) throw new AssertionError("missing test item: " + name);
        return new GenericStack(AEItemKey.of(item), amount);
    }
}
