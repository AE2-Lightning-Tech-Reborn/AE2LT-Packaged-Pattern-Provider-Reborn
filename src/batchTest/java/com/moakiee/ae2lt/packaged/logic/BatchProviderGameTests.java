package com.moakiee.ae2lt.packaged.logic;

import java.util.ArrayList;
import java.util.List;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BatchCopies;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp_batch")
@PrefixGameTestTemplate(false)
public final class BatchProviderGameTests {
    @GameTest(templateNamespace = "ae2ltpp_batch", template = "adapter_conn", timeoutTicks = 160)
    public static void ecBatchPreservesRemaindersAndSurvivesReload(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(2,2,2));
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), BuiltInRegistries.BLOCK.get(id("extendedcrafting:basic_table")).defaultBlockState());
        var be = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        be.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.EC_BASIC_ADAPTER.get()));
        var logic = (PackagedPatternProviderLogic) be.getLogic();
        var input = List.of(stack(Items.MILK_BUCKET,1), stack(Items.IRON_INGOT,1));
        logic.getPatternInv().setItemDirect(0, encode(input, List.of(stack(Items.DIAMOND,1))));
        helper.runAfterDelay(50, () -> {
            logic.updatePatterns();
            IPatternDetails pattern = logic.getAvailablePatterns().get(0);
            var template = counters(input);
            helper.assertTrue(logic.getBatchCapacity(pattern) >= 70, "native EC keeps its current unbounded virtual capacity");
            helper.assertTrue(logic.pushBatch(pattern, template, 70) == 0, "one native EC dispatch accepts all 70 copies");
            helper.assertTrue(template[0].get(AEItemKey.of(Items.MILK_BUCKET)) == 1
                    && template[1].get(AEItemKey.of(Items.IRON_INGOT)) == 1, "borrowed input template stays unchanged");
            helper.assertTrue(logic.getBatchCapacity(pattern) >= 70,
                    "native EC does not inherit the bounded physical lane limit");
            var saved = new CompoundTag();
            logic.writeToNBT(saved);
            logic.clearContent();
            var before = logic.drainVirtualBatch();
            helper.assertTrue(countPending(before, Items.DIAMOND) == 70 && countPending(before, Items.BUCKET) == 70,
                    "clearing pattern slots preserves all owned products and per-copy bucket remainders");
            helper.assertTrue(logic.drainVirtualBatch().isEmpty(), "drained outputs cannot be dropped a second time");
            logic.readFromNBT(saved);
            logic.updatePatterns();
            var after = logic.drainVirtualBatch();
            helper.assertTrue(countPending(after, Items.DIAMOND) == 70 && countPending(after, Items.BUCKET) == 70,
                    "reload preserves exact products and remainders");
            helper.runAfterDelay(1, () -> {
                var livePattern = logic.getAvailablePatterns().get(0);
                helper.assertTrue(logic.getBatchCapacity(livePattern) >= 70, "next tick preserves native EC capacity for control checks");
                logic.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
                helper.assertTrue(logic.getBatchCapacity(livePattern) <= 1, "locking disables multi-copy dispatch");
                long lockedLeft = logic.pushBatch(livePattern, template, 8);
                helper.assertTrue(lockedLeft >= 7 && lockedLeft <= 8, "locking accepts at most one copy");
                var lockedOutputs = logic.drainVirtualBatch();
                helper.assertTrue(countPending(lockedOutputs, Items.DIAMOND) == 8 - lockedLeft
                        && countPending(lockedOutputs, Items.BUCKET) == 8 - lockedLeft,
                        "locked single-copy outputs have exact ownership");
                logic.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
                logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
                long blockedLeft = logic.pushBatch(livePattern, template, 8);
                helper.assertTrue(blockedLeft >= 7 && blockedLeft <= 8, "blocking accepts at most one copy");
                var blockedOutputs = logic.drainVirtualBatch();
                helper.assertTrue(countPending(blockedOutputs, Items.DIAMOND) == 8 - blockedLeft
                        && countPending(blockedOutputs, Items.BUCKET) == 8 - blockedLeft,
                        "blocking never scales a single-copy result");
                logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.NO);
                helper.assertTrue(logic.getBatchCapacity(livePattern) >= 70, "controls preserve the native unbounded capacity");
                logic.clearContent();
                helper.assertTrue(logic.drainVirtualBatch().isEmpty(), "already-drained products are not resurrected");
            System.out.println("PP_BATCH_EC_PASS copies=70 leftover=0 buckets=70 template=unchanged reload=exact controls=single");
            helper.succeed();
            });
        });
    }



    @GameTest(templateNamespace = "ae2ltpp_batch", template = "adapter_conn", timeoutTicks = 160)
    public static void thunderboltDispatchesOrdinaryPpAsOneBatch(GameTestHelper helper) {
        runCpuBatch(helper, false);
    }

    @GameTest(templateNamespace = "ae2ltpp_batch", template = "adapter_conn", timeoutTicks = 160)
    public static void laterLaneFailureDoesNotRefundAcceptedCopies(GameTestHelper helper) {
        runCpuBatch(helper, true);
    }

    private static void runCpuBatch(GameTestHelper helper, boolean injectFailure) {
        var pos = helper.absolutePos(new BlockPos(2,2,2));
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), BuiltInRegistries.BLOCK.get(id("extendedcrafting:basic_table")).defaultBlockState());
        var be = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        be.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.EC_BASIC_ADAPTER.get()));
        var logic = (PackagedPatternProviderLogic) be.getLogic();
        logic.getPatternInv().setItemDirect(0, encode(
                List.of(stack(Items.MILK_BUCKET,1),stack(Items.IRON_INGOT,1)), List.of(stack(Items.DIAMOND,1))));
        helper.runAfterDelay(50, () -> {
            var pattern = logic.getAvailablePatterns().get(0);
            var failureReached = installNativeLanes(logic, level, pos, pattern, injectFailure);
            var tasks = new java.util.HashMap<IPatternDetails,long[]>();
            long[] remaining = {70}; tasks.put(pattern, remaining);
            var waiting = new appeng.crafting.inv.ListCraftingInventory(key -> {});
            var inventory = new appeng.crafting.inv.ListCraftingInventory(key -> {});
            inventory.insert(AEItemKey.of(Items.MILK_BUCKET),70,appeng.api.config.Actionable.MODULATE);
            inventory.insert(AEItemKey.of(Items.IRON_INGOT),70,appeng.api.config.Actionable.MODULATE);
            var job = new com.moakiee.thunderbolt.core.crafting.batch.DefaultBatchJobView(level,
                    java.util.UUID.randomUUID(),tasks,waiting, t -> ((long[])t)[0], (t,n) -> ((long[])t)[0]=n,
                    new Object(), (tracker,count,type) -> {});
            var grid = be.getMainNode().getGrid();
            var result = com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor.runBatchOnly(1,
                    com.moakiee.thunderbolt.core.crafting.batch.BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH,
                    (appeng.me.service.CraftingService)grid.getCraftingService(),grid.getEnergyService(),job,inventory,
                    new java.util.HashMap<>(),be::saveChanges);
            helper.assertTrue(result.dispatchedCopies() == 64 && result.consumedCpuOps() == 1, "Thunderbolt resolves PP and dispatches 64 copies in one CPU operation");
            if (injectFailure) {
                // Current Thunderbolt visits one provider once per CPU pass. A
                // second pass in this same tick reaches the remaining lane while
                // the first lane's 64 copies and its exhausted budget stay owned.
                var rejected = com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor.runBatchOnly(1,
                        com.moakiee.thunderbolt.core.crafting.batch.BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH,
                        (appeng.me.service.CraftingService)grid.getCraftingService(),grid.getEnergyService(),job,inventory,
                        new java.util.HashMap<>(),be::saveChanges);
                helper.assertTrue(failureReached.get(), "the later lane actually threw after the first lane accepted its batch");
                helper.assertTrue(rejected.dispatchedCopies() == 0 && rejected.consumedCpuOps() == 0,
                        "failed preparation accepts no copies and consumes no additional CPU operation");
                var owned = logic.drainVirtualBatch();
                helper.assertTrue(countPending(owned, Items.DIAMOND) == 64
                        && countPending(owned, Items.BUCKET) == 64,
                        "later preparation failure preserves exactly the previously accepted products and remainders");
            }
            helper.assertTrue(remaining[0] == 6 && inventory.list.get(AEItemKey.of(Items.MILK_BUCKET)) == 6
                    && inventory.list.get(AEItemKey.of(Items.IRON_INGOT)) == 6, "CPU keeps exact leftover task and material quantities");
            helper.assertTrue(waiting.list.get(AEItemKey.of(Items.DIAMOND)) == 64, "CPU registers exactly 64 expected outputs");
            System.out.println((injectFailure ? "PP_BATCH_CPU_FAILURE_PASS" : "PP_BATCH_CPU_PASS")
                    + " dispatches=1 copies=64 remaining=6 waiting=64");
            helper.succeed();
        });
    }

    private static java.util.concurrent.atomic.AtomicBoolean installNativeLanes(PackagedPatternProviderLogic logic,
            net.minecraft.server.level.ServerLevel level, BlockPos pos, IPatternDetails pattern, boolean injectFailure) {
        // Use actual EC recipe execution through a bounded adapter view for the lane-budget regression.
        var ec = new com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingTableAdapter();
        var ecBinding = ec.bind(level, pos.east(), pattern);
        var type = com.moakiee.ae2lt.packaged.logic.multiblock.LinearVirtualCraftingAdapter.class;
        var bounded = (com.moakiee.ae2lt.packaged.logic.multiblock.LinearVirtualCraftingAdapter)
                java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                    try {
                        return method.invoke(ec, args);
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        var failureReached = new java.util.concurrent.atomic.AtomicBoolean();
        var candidates = new ArrayList<com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneCandidate>();
        candidates.add(new com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneCandidate(
                new com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneKey.FaceLane(Direction.EAST),
                bounded, ecBinding.handle(), com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode.VIRTUAL));
        if (injectFailure) {
            var broken = (com.moakiee.ae2lt.packaged.logic.multiblock.LinearVirtualCraftingAdapter)
                    java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                        if (method.getName().equals("recognizesMain")) return true;
                        if (method.getName().equals("planVirtualWithBinding")) {
                            failureReached.set(true);
                            throw new IllegalStateException("injected later-lane failure");
                        }
                        throw new AssertionError("unexpected adapter call: " + method.getName());
                    });
            candidates.add(new com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneCandidate(
                    new com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneKey.FaceLane(Direction.UP),
                    broken, new Object(), com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode.VIRTUAL));
        }
        try {
            var field = PackagedPatternProviderLogic.class.getDeclaredField("bindingTable");
            field.setAccessible(true);
            var table = (com.moakiee.ae2lt.packaged.logic.multiblock.binding.PatternBindingTable)field.get(logic);
            table.put(pattern, new com.moakiee.ae2lt.packaged.logic.multiblock.binding.PatternBinding(candidates, level.getGameTime()));
        } catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
        return failureReached;
    }



    private static ItemStack encode(List<GenericStack> input, List<GenericStack> output) {
        return PatternDetailsHelper.encodeProcessingPattern(input.toArray(GenericStack[]::new), output.toArray(GenericStack[]::new));
    }

    private static ResourceLocation id(String id) { return new ResourceLocation(id); }
    private static GenericStack stack(net.minecraft.world.item.Item item, long amount) { return new GenericStack(AEItemKey.of(item),amount); }
    private static KeyCounter[] counters(List<GenericStack> stacks) {
        var result = new KeyCounter[stacks.size()];
        for (int i=0;i<result.length;i++) { result[i]=new KeyCounter(); result[i].add(stacks.get(i).what(),stacks.get(i).amount()); }
        return result;
    }
    private static long countPending(List<GenericStack> stacks, net.minecraft.world.item.Item item) { return stacks.stream().filter(s -> s.what().equals(AEItemKey.of(item))).mapToLong(GenericStack::amount).sum(); }
}
