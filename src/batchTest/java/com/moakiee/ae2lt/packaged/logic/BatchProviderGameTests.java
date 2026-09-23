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
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingBatchInputs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class BatchProviderGameTests {
    @GameTest(template = "mek_routing_test", timeoutTicks = 160)
    public static void ecBatchPreservesRemaindersAndSurvivesReload(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(2,2,2));
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), BuiltInRegistries.BLOCK.get(id("extendedcrafting:basic_table")).defaultBlockState());
        var be = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        be.getAdapterInv().setItemDirect(0, PPItems.EC_BASIC_ADAPTER.toStack());
        var logic = (PackagedPatternProviderLogic) be.getLogic();
        var input = List.of(stack(Items.MILK_BUCKET,1), stack(Items.IRON_INGOT,1));
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(input, List.of(stack(Items.DIAMOND,1))));
        helper.runAfterDelay(50, () -> {
            logic.updatePatterns();
            IPatternDetails pattern = logic.getAvailablePatterns().getFirst();
            var template = counters(input);
            helper.assertTrue(logic.getBatchCapacity(pattern) == 64, "real PP advertises 64-copy EC batch capacity");
            helper.assertTrue(logic.pushBatch(pattern, template, 70) == 6, "one dispatch accepts 64 and returns six leftovers");
            helper.assertTrue(template[0].get(AEItemKey.of(Items.MILK_BUCKET)) == 1
                    && template[1].get(AEItemKey.of(Items.IRON_INGOT)) == 1, "borrowed input template stays unchanged");
            helper.assertTrue(logic.getBatchCapacity(pattern) == 0 && !logic.pushPattern(pattern, BatchCopies.copy(template)),
                    "single and batch calls share the 64-copy lane limit");
            var saved = new CompoundTag();
            logic.writeToNBT(saved, level.registryAccess());
            var before = new ArrayList<ItemStack>(); logic.addDrops(before);
            helper.assertTrue(count(before, Items.DIAMOND) == 64 && count(before, Items.BUCKET) == 64,
                    "all batch products and per-copy bucket remainders are owned before flush");
            logic.clearContent();
            logic.readFromNBT(saved, level.registryAccess());
            var after = new ArrayList<ItemStack>(); logic.addDrops(after);
            helper.assertTrue(count(after, Items.DIAMOND) == 64 && count(after, Items.BUCKET) == 64, "reload preserves exact products and remainders");
            logic.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
            helper.assertTrue(logic.getBatchCapacity(pattern) <= 1 && logic.pushBatch(pattern, template, 8) == 8, "lock-until-result keeps single-copy dispatch");
            logic.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
            logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
            helper.assertTrue(logic.pushBatch(pattern, template, 8) == 8, "blocking keeps single-copy dispatch");
            logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.NO);
            logic.clearContent();
            var empty = new ArrayList<ItemStack>(); logic.addDrops(empty);
            helper.assertTrue(count(empty, Items.DIAMOND) == 0 && count(empty, Items.BUCKET) == 0, "clear does not duplicate pending drops");
            System.out.println("PP_BATCH_EC_PASS copies=64 leftover=6 buckets=64 template=unchanged reload=exact controls=single");
            helper.succeed();
        });
    }

    @GameTest(template = "mek_routing_test")
    public static void partialInsertionRetainsEveryPortAndReloads(GameTestHelper helper) {
        var input = new PendingBatchInputs();
        var a = new PendingBatchInputs.Delivery(id("ae2ltpp:mekmm"), id("minecraft:overworld"), new BlockPos(1,2,3), Direction.NORTH, id("minecraft:stone"), stack(Items.IRON_INGOT,16));
        var b = new PendingBatchInputs.Delivery(a.adapter(), a.dimension(), new BlockPos(3,2,1), Direction.SOUTH, a.block(), stack(Items.GOLD_INGOT,32));
        input.adopt(List.of(a,b));
        input.deliver(d -> d.stack().what().equals(a.stack().what()) ? 5 : 0);
        helper.assertTrue(input.snapshot().get(0).stack().amount() == 11 && input.snapshot().get(1).stack().amount() == 32,
                "partial first port retains its remainder and the entire unaccepted second port");
        var recovered = new PendingBatchInputs();
        recovered.readFromNBT(input.writeToNBT(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(recovered.snapshot().equals(input.snapshot()), "pending positions/faces/keys/amounts survive NBT");
        long[] delivered = {5};
        recovered.deliver(d -> { delivered[0] += d.stack().amount(); return d.stack().amount(); });
        helper.assertTrue(!recovered.hasPending() && delivered[0] == 48, "retry delivers exactly 48 total inputs");
        recovered.deliver(d -> { throw new AssertionError("completed batch must not replay"); });
        System.out.println("PP_BATCH_INPUTS_PASS partial=5 remaining=11+32 reload=exact total=48");
        helper.succeed();
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 160)
    public static void thunderboltDispatchesOrdinaryPpAsOneBatch(GameTestHelper helper) {
        runCpuBatch(helper, false);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 160)
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
        be.getAdapterInv().setItemDirect(0, PPItems.EC_BASIC_ADAPTER.toStack());
        var logic = (PackagedPatternProviderLogic) be.getLogic();
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(stack(Items.MILK_BUCKET,1),stack(Items.IRON_INGOT,1)), List.of(stack(Items.DIAMOND,1))));
        helper.runAfterDelay(50, () -> {
            var pattern = logic.getAvailablePatterns().getFirst();
            var failureReached = injectFailure ? installFailingSecondLane(logic, level, pos, pattern) : null;
            var tasks = new java.util.HashMap<IPatternDetails,long[]>();
            long[] remaining = {70}; tasks.put(pattern, remaining);
            var waiting = new appeng.crafting.inv.ListCraftingInventory(key -> {});
            var inventory = new appeng.crafting.inv.ListCraftingInventory(key -> {});
            inventory.insert(AEItemKey.of(Items.MILK_BUCKET),70,appeng.api.config.Actionable.MODULATE);
            inventory.insert(AEItemKey.of(Items.IRON_INGOT),70,appeng.api.config.Actionable.MODULATE);
            var job = new com.moakiee.thunderbolt.core.crafting.batch.DefaultBatchJobView(level,
                    java.util.UUID.randomUUID(),tasks,waiting, t -> ((long[])t)[0], (t,n) -> ((long[])t)[0]=n,
                    new Object(), (tracker,count,type) -> {});
            var grid = logic.getGrid();
            var result = com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor.runBatchOnly(1,
                    com.moakiee.thunderbolt.core.crafting.batch.BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH,
                    (appeng.me.service.CraftingService)grid.getCraftingService(),grid.getEnergyService(),job,inventory,
                    new java.util.HashMap<>(),be::saveChanges);
            helper.assertTrue(result.dispatchedCopies() == 64 && result.consumedCpuOps() == 1, "Thunderbolt resolves PP and dispatches 64 copies in one CPU operation");
            helper.assertTrue(remaining[0] == 6 && inventory.list.get(AEItemKey.of(Items.MILK_BUCKET)) == 6
                    && inventory.list.get(AEItemKey.of(Items.IRON_INGOT)) == 6, "CPU keeps exact leftover task and material quantities");
            helper.assertTrue(waiting.list.get(AEItemKey.of(Items.DIAMOND)) == 64, "CPU registers exactly 64 expected outputs");
            if (injectFailure) helper.assertTrue(failureReached.get(), "the second lane actually threw after the first lane accepted its batch");
            System.out.println((injectFailure ? "PP_BATCH_CPU_FAILURE_PASS" : "PP_BATCH_CPU_PASS")
                    + " dispatches=1 copies=64 remaining=6 waiting=64");
            helper.succeed();
        });
    }

    private static java.util.concurrent.atomic.AtomicBoolean installFailingSecondLane(PackagedPatternProviderLogic logic,
            net.minecraft.server.level.ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        // Keep a real EC lane first, then inject an adapter failure after that lane owns 64 outputs.
        // This exercises Thunderbolt's actual refund boundary, rather than only the provider return value.
        var ec = new com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingTableAdapter();
        var ecBinding = ec.bind(level, pos.east(), pattern);
        var type = com.moakiee.ae2lt.packaged.logic.multiblock.LinearVirtualCraftingAdapter.class;
        var failureReached = new java.util.concurrent.atomic.AtomicBoolean();
        var broken = (com.moakiee.ae2lt.packaged.logic.multiblock.LinearVirtualCraftingAdapter)
                java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                    if (method.getName().equals("recognizesMain")) return true;
                    if (method.getName().equals("planVirtualWithBinding")) {
                        failureReached.set(true);
                        throw new IllegalStateException("injected second-lane failure");
                    }
                    throw new AssertionError("unexpected adapter call: " + method.getName());
                });
        var candidates = List.of(
                new com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneCandidate(
                        new com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneKey.FaceLane(Direction.EAST),
                        ec, ecBinding.handle(), com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode.VIRTUAL),
                new com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneCandidate(
                        new com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneKey.FaceLane(Direction.UP),
                        broken, new Object(), com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode.VIRTUAL));
        try {
            var field = PackagedPatternProviderLogic.class.getDeclaredField("bindingTable");
            field.setAccessible(true);
            var table = (com.moakiee.ae2lt.packaged.logic.multiblock.binding.PatternBindingTable)field.get(logic);
            table.put(pattern, new com.moakiee.ae2lt.packaged.logic.multiblock.binding.PatternBinding(candidates, level.getGameTime()));
        } catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
        return failureReached;
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 160)
    public static void nativeMekTanksLimitBatchWithoutConsumingTemplate(GameTestHelper helper) {
        var level = helper.getLevel();
        var main = helper.absolutePos(new BlockPos(5,2,5));
        var pos = main.north();
        level.setBlockAndUpdate(main,BuiltInRegistries.BLOCK.get(id("mekmm:large_chemical_infuser")).defaultBlockState());
        level.setBlockAndUpdate(pos,PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(pos.north(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var be = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        be.getAdapterInv().setItemDirect(0,PPItems.MEKMM_ADAPTER.toStack());
        var logic = (PackagedPatternProviderLogic) be.getLogic();
        var oxygen = new GenericStack(me.ramidzkh.mekae2.ae2.MekanismKey.of(mekanism.common.registries.MekanismChemicals.OXYGEN.asStack(32)),32);
        var chlorine = new GenericStack(me.ramidzkh.mekae2.ae2.MekanismKey.of(mekanism.common.registries.MekanismChemicals.CHLORINE.asStack(32)),32);
        var acid = new GenericStack(me.ramidzkh.mekae2.ae2.MekanismKey.of(mekanism.common.registries.MekanismChemicals.SULFURIC_ACID.asStack(64)),64);
        // Intentionally reverse the two inputs: the existing recipe binding must still choose the right ports.
        logic.getPatternInv().setItemDirect(0,PatternDetailsHelper.encodeProcessingPattern(List.of(chlorine,oxygen),List.of(acid)));
        var pattern = logic.getAvailablePatterns().getFirst();
        var adapter = new com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekanismMoreMachinesAdapter();
        var binding = adapter.bind(level,main,pattern);
        helper.assertTrue(binding != null,"Mek native recipe binds before preparing its ports");
        var template = counters(List.of(chlorine,oxygen));
        var plan = adapter.planWithBinding(level,main,pattern,template,binding.handle(),null);
        for (var port : plan.targets()) {
            level.setBlockAndUpdate(port.pos(),BuiltInRegistries.BLOCK.get(id("mekanism:bounding_block")).defaultBlockState());
            ((mekanism.common.tile.TileEntityBoundingBlock)level.getBlockEntity(port.pos())).setMainLocation(main,false);
        }
        helper.runAfterDelay(50, () -> {
            try {
                var machine = level.getBlockEntity(main);
                var left = (mekanism.api.chemical.IChemicalTank)machine.getClass().getField("leftTank").get(machine);
                var right = (mekanism.api.chemical.IChemicalTank)machine.getClass().getField("rightTank").get(machine);
                left.setStack(mekanism.common.registries.MekanismChemicals.OXYGEN.asStack(left.getCapacity()-96));
                right.setStack(mekanism.common.registries.MekanismChemicals.CHLORINE.asStack(right.getCapacity()-160));
                long leftBefore=left.getStored(), rightBefore=right.getStored();
                helper.assertTrue(logic.getBatchCapacity(pattern)>1,"Mek lane advertises batch dispatch");
                long remaining=logic.pushBatch(pattern,template,8);
                helper.assertTrue(remaining==5,"tightest native tank accepts three copies and returns five: "+remaining);
                helper.assertTrue(left.getStored()-leftBefore==96 && right.getStored()-rightBefore==96,"native chemical ports receive equal three-copy quantities");
                helper.assertTrue(template[0].get(chlorine.what())==32 && template[1].get(oxygen.what())==32,"Mek borrowed template is unchanged");
                helper.assertTrue(logic.pushBatch(pattern,template,8)==8,"full input tank rejects further copies without consuming anything");
                System.out.println("PP_BATCH_MEK_PASS requested=8 accepted=3 leftover=5 oxygen=96 chlorine=96");
                helper.succeed();
            } catch (ReflectiveOperationException exception) {throw new RuntimeException(exception);}
        });
    }

    private static Identifier id(String id) { return Identifier.parse(id); }
    private static GenericStack stack(net.minecraft.world.item.Item item, long amount) { return new GenericStack(AEItemKey.of(item),amount); }
    private static KeyCounter[] counters(List<GenericStack> stacks) {
        var result = new KeyCounter[stacks.size()];
        for (int i=0;i<result.length;i++) { result[i]=new KeyCounter(); result[i].add(stacks.get(i).what(),stacks.get(i).amount()); }
        return result;
    }
    private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) { return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum(); }
}
