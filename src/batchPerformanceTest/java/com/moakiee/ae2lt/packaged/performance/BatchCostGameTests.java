package com.moakiee.ae2lt.packaged.performance;

import java.lang.management.ManagementFactory;
import java.util.List;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneRateLimiter;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.TickFailureSet;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekanismMoreMachinesAdapter;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.registries.MekanismChemicals;
import mekanism.common.tile.TileEntityBoundingBlock;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class BatchCostGameTests {
    @GameTest(template = "mek_routing_test", timeoutTicks = 200)
    public static void nativeMekBatchCapacityCost(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var main = h.absolutePos(new BlockPos(5, 2, 5));
        var pos = main.north();
        level.setBlockAndUpdate(main, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekmm:large_chemical_infuser")).defaultBlockState());
        level.setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(pos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        provider.getAdapterInv().setItemDirect(0, PPItems.MEKMM_ADAPTER.toStack());
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        var oxygen = new GenericStack(MekanismKey.of(MekanismChemicals.OXYGEN.asStack(32)), 32);
        var chlorine = new GenericStack(MekanismKey.of(MekanismChemicals.CHLORINE.asStack(32)), 32);
        var acid = new GenericStack(MekanismKey.of(MekanismChemicals.SULFURIC_ACID.asStack(64)), 64);
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(List.of(chlorine, oxygen), List.of(acid)));
        var pattern = logic.getAvailablePatterns().getFirst();
        var template = new KeyCounter[]{new KeyCounter(), new KeyCounter()};
        template[0].add(chlorine.what(), 32); template[1].add(oxygen.what(), 32);
        var adapter = new MekanismMoreMachinesAdapter();
        var binding = adapter.bind(level, main, pattern);
        h.assertTrue(binding != null, "native recipe binds");
        var plan = adapter.planWithBinding(level, main, pattern, template, binding.handle(), null);
        for (var port : plan.targets()) {
            level.setBlockAndUpdate(port.pos(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:bounding_block")).defaultBlockState());
            ((TileEntityBoundingBlock) level.getBlockEntity(port.pos())).setMainLocation(main, false);
        }
        var limiterField = PackagedPatternProviderLogic.class.getDeclaredField("inputBatchLimiter");
        var failuresField = PackagedPatternProviderLogic.class.getDeclaredField("mekFailures");
        limiterField.setAccessible(true); failuresField.setAccessible(true);
        var limiter = (LaneRateLimiter) limiterField.get(logic);
        var failures = (TickFailureSet<?>) failuresField.get(logic);
        h.runAfterDelay(50, () -> {
            try {
                h.assertTrue(provider.getMainNode().isActive(), "native provider has power");
                var machine = level.getBlockEntity(main);
                var left = (IChemicalTank) machine.getClass().getField("leftTank").get(machine);
                var right = (IChemicalTank) machine.getClass().getField("rightTank").get(machine);
                var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
                bean.setThreadAllocatedMemoryEnabled(true);
                long thread = Thread.currentThread().threadId();
                for (int capacity : new int[]{0, 1, 16, 64, -1}) {
                    for (int round = -2; round < 3; round++) {
                        long nanos = 0, bytes = 0;
                        int calls = 2000;
                        for (int i = 0; i < calls; i++) {
                            int room = capacity < 0 ? (i * 17) % 65 : capacity;
                            left.setStack(MekanismChemicals.OXYGEN.asStack(left.getCapacity() - 32L * room));
                            right.setStack(MekanismChemicals.CHLORINE.asStack(right.getCapacity() - 32L * (room + 3)));
                            ((TileEntityMekanism) machine).setActive(true);
                            limiter.clear(); failures.clear(); // independent admissions, not same-tick retry suppression
                            long allocated = bean.getThreadAllocatedBytes(thread);
                            long start = System.nanoTime();
                            long remainder = logic.pushBatch(pattern, template, 64);
                            nanos += System.nanoTime() - start;
                            bytes += bean.getThreadAllocatedBytes(thread) - allocated;
                            h.assertTrue(remainder == 64 - room, "tightest native tank bounds accepted copies");
                            h.assertTrue(left.getStored() == left.getCapacity() && right.getStored() == right.getCapacity() - 96,
                                    "real insertion conserves both chemical inputs");
                            h.assertTrue(!logic.isBusy() && template[0].get(chlorine.what()) == 32 && template[1].get(oxygen.what()) == 32,
                                    "no queued remainder or borrowed-template mutation");
                        }
                        if (round >= 0) System.out.printf("PP_MEK_COST baseline=%s capacity=%d round=%d calls=%d ns/call=%.3f bytes/call=%.3f%n",
                                Boolean.getBoolean("ae2ltpp.batchCostBaseline"), capacity, round, calls,
                                nanos / (double) calls, bytes / (double) calls);
                    }
                }
                h.succeed();
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }
}
