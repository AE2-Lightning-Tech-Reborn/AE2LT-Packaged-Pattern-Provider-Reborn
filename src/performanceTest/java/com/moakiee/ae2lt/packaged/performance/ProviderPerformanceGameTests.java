package com.moakiee.ae2lt.packaged.performance;

import java.util.ArrayList;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEBlocks;
import appeng.me.service.TickManagerService;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ProviderPerformanceGameTests {
    private static final boolean BASELINE = Boolean.getBoolean("ae2ltpp.performanceBaseline");
    private static final int PROVIDERS = 64;
    private static final int SAMPLE_TICKS = 200;

    @GameTest(template = "core_test", timeoutTicks = 100)
    @SuppressWarnings("unchecked")
    public static void pendingWorkAndMekDeadlinesKeepUrgentTicks(GameTestHelper h) {
        if (BASELINE) { h.succeed(); return; }
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        h.getLevel().setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(pos);
        provider.setReturnMode(ReturnMode.AUTO);
        h.runAfterDelay(40, () -> {
            try {
                var logic = (PackagedPatternProviderLogic) provider.getLogic();
                logic.onHostStateChanged();
                logic.tickAutoReturn();
                var canDelay = PackagedPatternProviderLogic.class.getDeclaredMethod("canDelayTick");
                canDelay.setAccessible(true);
                h.assertTrue((boolean) canDelay.invoke(logic), "empty AUTO provider can wait twenty ticks");
                var outputsField = PackagedPatternProviderLogic.class.getDeclaredField("pendingOutputs");
                outputsField.setAccessible(true);
                var outputs = (com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingOutputBuffer) outputsField.get(logic);
                outputs.enqueue(List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)));
                h.assertTrue(!(boolean) canDelay.invoke(logic), "ten-tick output flush remains urgent");
                outputs.clear();
                var inputsField = PackagedPatternProviderLogic.class.getDeclaredField("pendingBatchInputs");
                inputsField.setAccessible(true);
                var inputs = (com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingBatchInputs) inputsField.get(logic);
                inputs.adopt(List.of(new com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingBatchInputs.Delivery(
                        net.minecraft.resources.ResourceLocation.parse("ae2ltpp:mekmm"), h.getLevel().dimension().location(),
                        pos, net.minecraft.core.Direction.NORTH, net.minecraft.resources.ResourceLocation.parse("minecraft:stone"),
                        new appeng.api.stacks.GenericStack(AEItemKey.of(Items.IRON_INGOT), 1))));
                h.assertTrue(!(boolean) canDelay.invoke(logic), "accepted input remainder retries every tick");
                inputs.clear();
                var scheduleField = PackagedPatternProviderLogic.class.getDeclaredField("mekReturns");
                scheduleField.setAccessible(true);
                var schedule = (com.moakiee.ae2lt.packaged.logic.multiblock.binding.AdaptiveReturnSchedule<Object>) scheduleField.get(logic);
                var key = new Object();
                long tick = h.getLevel().getGameTime();
                schedule.record(key, tick, true);
                h.assertTrue(!(boolean) canDelay.invoke(logic), "productive Mek polls again in one tick");
                schedule.clear();
                for (int i = 0; i < 5; i++) schedule.record(key, tick, false);
                h.assertTrue((boolean) canDelay.invoke(logic), "fully backed-off Mek poll permits the idle interval");
                schedule.wake(key, tick);
                h.assertTrue(!(boolean) canDelay.invoke(logic), "new Mek input overrides backed-off polling");
                schedule.clear();
                h.succeed();
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }

    @GameTest(template = "core_test", timeoutTicks = 360)
    public static void idleGridCadenceAndReturnInventoryWake(GameTestHelper h) {
        TickManagerService.MONITORING_ENABLED = true;
        var providers = new ArrayList<PackagedPatternProviderBlockEntity>();
        for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++) {
            var pos = h.absolutePos(new BlockPos(3 + x * 3, 2 + y * 3, 2 + z * 3));
            h.getLevel().setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
            h.getLevel().setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            var provider = (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(pos);
            provider.setReturnMode(ReturnMode.AUTO);
            providers.add(provider);
        }
        h.runAfterDelay(60, () -> {
            long[] lastTicks = new long[PROVIDERS];
            long before = 0;
            for (int i = 0; i < providers.size(); i++) {
                var provider = providers.get(i);
                h.assertTrue(provider.getMainNode().isActive(), "all fixture providers have active native grids");
                var manager = manager(provider);
                var status = manager.getStatus(provider.getMainNode().getNode());
                h.assertTrue(status.currentRate() == (BASELINE ? 1 : 20), "idle callback cadence");
                // AE2's NodeStatus.lastTick is an age, despite the accessor's name.
                lastTicks[i] = h.getLevel().getGameTime() - status.lastTick();
                before += manager.getOverallTime(provider.getMainNode().getNode());
            }
            sample(h, providers, lastTicks, 0, 0, before);
        });
    }

    private static void sample(GameTestHelper h, List<PackagedPatternProviderBlockEntity> providers,
                               long[] lastTicks, int elapsed, long callbacks, long before) {
        if (elapsed == SAMPLE_TICKS) {
            long expected = (long) PROVIDERS * (BASELINE ? SAMPLE_TICKS : SAMPLE_TICKS / 20);
            h.assertTrue(callbacks == expected, "native callback count expected=" + expected + " actual=" + callbacks);
            long after = 0;
            for (var provider : providers) after += manager(provider).getOverallTime(provider.getMainNode().getNode());
            System.out.printf("PP_IDLE_COST baseline=%s providers=%d ticks=%d callbacks=%d callbackNs=%d ns/provider/tick=%.3f%n",
                    BASELINE, PROVIDERS, SAMPLE_TICKS, callbacks, after - before,
                    (after - before) / (double) (PROVIDERS * SAMPLE_TICKS));
            var provider = providers.getFirst();
            var logic = (PackagedPatternProviderLogic) provider.getLogic();
            long last = h.getLevel().getGameTime() - manager(provider).getStatus(provider.getMainNode().getNode()).lastTick();
            h.assertTrue(logic.getInternalReturnInv().insert(0, AEItemKey.of(Items.IRON_INGOT), 7, Actionable.MODULATE) == 7,
                    "return inventory accepts an exact pending receipt");
            h.runAfterDelay(2, () -> {
                var status = manager(provider).getStatus(provider.getMainNode().getNode());
                h.assertTrue(h.getLevel().getGameTime() - status.lastTick() > last && status.currentRate() == 1,
                        "new return inventory wakes idle provider immediately");
                h.assertTrue(logic.getInternalReturnInv().getAmount(0) == 7, "full network keeps every pending item");
                provider.setReturnMode(ReturnMode.OFF);
                h.runAfterDelay(2, () -> {
                    h.assertTrue(manager(provider).getStatus(provider.getMainNode().getNode()).currentRate() == 1,
                            "turning AUTO off cannot put pending return items to sleep");
                    h.succeed();
                });
            });
            return;
        }
        h.runAfterDelay(1, () -> {
            long next = callbacks;
            for (int i = 0; i < providers.size(); i++) {
                var provider = providers.get(i);
                long tick = h.getLevel().getGameTime() - manager(provider).getStatus(provider.getMainNode().getNode()).lastTick();
                if (tick != lastTicks[i]) { next++; lastTicks[i] = tick; }
            }
            sample(h, providers, lastTicks, elapsed + 1, next, before);
        });
    }

    private static TickManagerService manager(PackagedPatternProviderBlockEntity provider) {
        return (TickManagerService) provider.getMainNode().getGrid().getTickManager();
    }
}
