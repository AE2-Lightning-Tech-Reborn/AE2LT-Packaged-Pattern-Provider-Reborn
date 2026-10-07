package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;

class PatternBindingTableTest {
    @Test
    void concurrentLookupsAndRecipeReloadsDoNotCorruptBindings() throws Exception {
        var table = new PatternBindingTable();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(9);
        var futures = new ArrayList<Future<?>>();
        try {
            for (int worker = 0; worker < 8; worker++) {
                var pattern = (IPatternDetails) java.lang.reflect.Proxy.newProxyInstance(
                        IPatternDetails.class.getClassLoader(), new Class<?>[] {IPatternDetails.class},
                        (proxy, method, args) -> null);
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int operation = 0; operation < 10000; operation++) {
                        var binding = PatternBinding.unmatched(operation);
                        table.put(pattern, binding);
                        var observed = table.getFresh(pattern, operation);
                        assertTrue(observed == null || observed == binding,
                                "reload may evict a binding but cannot replace it with another worker's value");
                        table.invalidate(pattern);
                    }
                    return null;
                }));
            }
            futures.add(executor.submit(() -> {
                start.await();
                for (int reload = 0; reload < 10000; reload++) {
                    PatternBindingTable.invalidateAllForRecipeReload();
                    table.size();
                    if (reload % 100 == 0) {
                        table.snapshot();
                    }
                }
                return null;
            }));
            start.countDown();
            for (var future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
            table.invalidateAll();
            assertEquals(0, table.size());
            var fresh = PatternBinding.unmatched(10001);
            table.put(null, fresh);
            assertSame(fresh, table.getFresh(null, 10001));
            System.out.println("STRESS PASS binding cache: 8 workers x 10000 cycles, 10000 recipe reloads");
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void writingAfterReloadDoesNotKeepOlderBindings() {
        var table = new PatternBindingTable();
        var freshPattern = (IPatternDetails) java.lang.reflect.Proxy.newProxyInstance(
                IPatternDetails.class.getClassLoader(), new Class<?>[] { IPatternDetails.class },
                (proxy, method, args) -> null);
        table.put(null, PatternBinding.unmatched(0));
        PatternBindingTable.invalidateAllForRecipeReload();
        var freshBinding = PatternBinding.unmatched(1);
        table.put(freshPattern, freshBinding);
        assertNull(table.getFresh(null, 1));
        assertSame(freshBinding, table.getFresh(freshPattern, 1));
    }

    @Test
    void expiresUnmatchedBindingsAfterNegativeTtl() {
        var table = new PatternBindingTable();
        IPatternDetails pattern = null;
        var binding = PatternBinding.unmatched(0);
        table.put(pattern, binding);
        assertSame(binding, table.getFresh(pattern, 39));
        assertNull(table.getFresh(pattern, 40));
    }

    @Test
    void discardsBindingsWhenGameClockMovesBackwards() {
        var table = new PatternBindingTable();
        table.put(null, PatternBinding.unmatched(1000));
        assertNull(table.getFresh(null, 900));
    }

    @Test
    void discardsBindingsWhenElapsedTimeOverflows() {
        var table = new PatternBindingTable();
        table.put(null, PatternBinding.unmatched(-1));
        assertNull(table.getFresh(null, Long.MAX_VALUE));
    }

    @Test
    void expiresMatchedBindingsAfterPositiveTtl() {
        var table = new PatternBindingTable();
        IPatternDetails pattern = null;
        var binding = new PatternBinding(
                List.of(new LaneCandidate(
                        new LaneKey.FaceLane(net.minecraft.core.Direction.NORTH),
                        new DummyAdapter(), new Object(), BindingMode.REAL)),
                0);
        table.put(pattern, binding);
        assertSame(binding, table.getFresh(pattern, 199));
        assertNull(table.getFresh(pattern, 200));
    }

    private static final class DummyAdapter implements MultiblockAdapter {
        @Override public int priority() { return 0; }
        @Override public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) { return false; }
        @Override public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) { return null; }
        @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) { return false; }
        @Override public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
                KeyCounter[] inputs, Object handle, IActionSource source) { return null; }
        @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos,
                AllowedOutputFilter filter, IActionSource source) { return List.of(); }
    }
}
