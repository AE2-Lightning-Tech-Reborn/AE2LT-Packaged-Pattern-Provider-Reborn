package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

class VirtualBatchAccumulatorStressTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void flushClockTracksOnlyInitializationFlushAndClockRecovery() {
        var accumulator = new VirtualBatchAccumulator();
        accumulator.enqueue(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 64)));
        assertEquals(Long.MIN_VALUE, accumulator.getLastFlushTick());
        assertFalse(accumulator.tickFlush(100, stacks -> stacks));
        assertEquals(100, accumulator.getLastFlushTick());
        for (long gameTick = 100; gameTick < 110; gameTick++) {
            assertFalse(accumulator.tickFlush(gameTick, stacks -> stacks));
            assertEquals(100, accumulator.getLastFlushTick());
        }
        assertTrue(accumulator.tickFlush(110, stacks -> stacks));
        assertEquals(110, accumulator.getLastFlushTick());
        assertTrue(accumulator.tickFlush(90, stacks -> stacks));
        assertEquals(90, accumulator.getLastFlushTick());
        var saved = new CompoundTag();
        accumulator.writeToNBT(saved);
        var restored = new VirtualBatchAccumulator();
        restored.readFromNBT(saved);
        assertEquals(90, restored.getLastFlushTick());
        assertFalse(restored.tickFlush(99, stacks -> stacks));
        assertTrue(restored.tickFlush(100, stacks -> List.of()));
        assertFalse(restored.hasPending());
        assertEquals(100, restored.getLastFlushTick());
        restored.clear();
        assertEquals(Long.MIN_VALUE, restored.getLastFlushTick());
    }

    @Test
    void sustainedBackpressureAndRecoveryConserveGrowingBatches() {
        var keys = new ArrayList<AEKey>();
        for (int index = 0; index < 127; index++) {
            var item = new ItemStack(Items.IRON_INGOT);
            item.getOrCreateTag().putInt("variant", index);
            keys.add(AEItemKey.of(item));
        }
        keys.add(AEFluidKey.of(Fluids.WATER));
        var accumulator = new VirtualBatchAccumulator();
        var produced = new HashMap<AEKey, Long>();
        var delivered = new HashMap<AEKey, Long>();
        var saved = new CompoundTag();
        var flushes = new int[1];
        long gameTick = 100;
        int reloads = 0;
        for (int tick = 0; tick < 60000; tick++) {
            var key = keys.get(tick % keys.size());
            accumulator.enqueue(List.of(new GenericStack(key, 64)));
            produced.merge(key, 64L, Long::sum);
            int currentTick = tick;
            accumulator.tickFlush(gameTick, stacks -> {
                flushes[0]++;
                if (flushes[0] % 1000 == 0) {
                    throw new IllegalStateException("injected rejection before delivery");
                }
                if (currentTick < 30000) {
                    return stacks;
                }
                var residuals = new ArrayList<GenericStack>();
                for (var stack : stacks) {
                    long accepted = Math.min(1024, stack.amount());
                    delivered.merge(stack.what(), accepted, Long::sum);
                    if (accepted < stack.amount()) {
                        residuals.add(new GenericStack(stack.what(), stack.amount() - accepted));
                    }
                }
                return residuals;
            });
            if (tick % 200 == 0) {
                accumulator.writeToNBT(saved);
                var restored = new VirtualBatchAccumulator();
                restored.readFromNBT(saved);
                accumulator = restored;
                reloads++;
            }
            var pending = accumulator.snapshot();
            assertTrue(pending.size() <= keys.size(), "queue cardinality must stay bounded by distinct keys");
            for (var expected : produced.entrySet()) {
                assertEquals(expected.getValue().longValue(),
                        delivered.getOrDefault(expected.getKey(), 0L) + pending.getOrDefault(expected.getKey(), 0L),
                        () -> "sustained conservation at tick " + currentTick + " key=" + expected.getKey());
            }
            gameTick += tick > 0 && tick % 1000 == 0 ? -25 : 1;
        }
        assertTrue(flushes[0] >= 5900, "test must exercise repeated real flush boundaries");
        assertTrue(delivered.values().stream().mapToLong(Long::longValue).sum() > 3000000,
                "test must recover and deliver accumulated products before final drain");
        for (var stack : accumulator.drainAll()) {
            delivered.merge(stack.what(), stack.amount(), Long::sum);
        }
        assertEquals(produced, delivered);
        assertFalse(accumulator.hasPending());
        System.out.println("STRESS PASS sustained ticks=60000 keys=128 products=3840000 reloads=" + reloads
                + " flushes=" + flushes[0]);
    }

    @Test
    void rewritingSaveAfterDrainDoesNotRestoreDeliveredProducts() {
        var accumulator = new VirtualBatchAccumulator();
        var saved = new CompoundTag();
        accumulator.enqueue(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 64)));
        accumulator.tickFlush(100, stacks -> List.of());
        accumulator.writeToNBT(saved);
        assertTrue(accumulator.tickFlush(110, stacks -> List.of()));
        accumulator.writeToNBT(saved);
        accumulator.readFromNBT(saved);
        assertFalse(accumulator.hasPending());

        accumulator.clear();
        accumulator.writeToNBT(saved);
        accumulator.readFromNBT(saved);
        accumulator.enqueue(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)));
        assertFalse(accumulator.tickFlush(120, stacks -> List.of()));
    }

    @Test
    void conservesProductsAcrossPartialDeliveryDrainAndRepeatedSaveReload() {
        int seeds = Integer.getInteger("ae2ltpp.stress.seeds", 1);
        int operations = Integer.getInteger("ae2ltpp.stress.trials", 10000);
        int keyCount = Integer.getInteger("ae2ltpp.stress.keys", 3);
        assertTrue(seeds > 0 && operations > 0 && keyCount > 0);
        long firstSeed = Long.getLong("ae2ltpp.stress.seed", 20261003L);
        for (int index = 0; index < seeds; index++) {
            runConservationScenario(firstSeed + index, operations, keyCount);
        }
    }

    private static void runConservationScenario(long seed, int operations, int keyCount) {
        var random = new Random(seed);
        var accumulator = new VirtualBatchAccumulator();
        var keys = new ArrayList<AEKey>();
        for (int index = 0; index < keyCount; index++) {
            if (index == keyCount - 1 && keyCount > 3) {
                keys.add(AEFluidKey.of(Fluids.WATER));
            } else {
                var item = new ItemStack(Items.IRON_INGOT);
                item.getOrCreateTag().putInt("variant", index);
                keys.add(AEItemKey.of(item));
            }
        }
        var produced = new HashMap<AEKey, Long>();
        var delivered = new HashMap<AEKey, Long>();
        var saved = new CompoundTag();
        long gameTick = 100;
        var counts = new int[7];
        for (int operation = 0; operation < operations; operation++) {
            int action = random.nextInt(counts.length);
            counts[action]++;
            switch (action) {
                case 0, 1 -> {
                    var outputs = new ArrayList<GenericStack>();
                    for (int stackIndex = 0; stackIndex < 1 + random.nextInt(8); stackIndex++) {
                        var key = keys.get(random.nextInt(keys.size()));
                        long amount = 1 + random.nextInt(64);
                        outputs.add(new GenericStack(key, amount));
                        produced.merge(key, amount, Long::sum);
                    }
                    accumulator.enqueue(outputs);
                }
                case 2 -> {
                    gameTick += random.nextInt(25) - 3;
                    accumulator.tickFlush(gameTick, stacks -> {
                        var residuals = new ArrayList<GenericStack>();
                        for (var stack : stacks) {
                            long amount = random.nextLong(stack.amount() + 1);
                            delivered.merge(stack.what(), amount, Long::sum);
                            if (amount < stack.amount()) {
                                residuals.add(new GenericStack(stack.what(), stack.amount() - amount));
                            }
                        }
                        return residuals;
                    });
                }
                case 3 -> {
                    accumulator.writeToNBT(saved);
                    var restored = new VirtualBatchAccumulator();
                    restored.readFromNBT(saved);
                    accumulator = restored;
                }
                case 4 -> {
                    for (var stack : accumulator.drainAll()) {
                        delivered.merge(stack.what(), stack.amount(), Long::sum);
                    }
                }
                case 5 -> {
                    gameTick += VirtualBatchAccumulator.FLUSH_INTERVAL_TICKS;
                    accumulator.tickFlush(gameTick, stacks -> stacks);
                }
                case 6 -> {
                    gameTick += VirtualBatchAccumulator.FLUSH_INTERVAL_TICKS;
                    var current = accumulator;
                    current.tickFlush(gameTick, stacks -> {
                        for (var stack : stacks) {
                            delivered.merge(stack.what(), stack.amount(), Long::sum);
                        }
                        var key = keys.get(random.nextInt(keys.size()));
                        long amount = 1 + random.nextInt(64);
                        current.enqueue(List.of(new GenericStack(key, amount)));
                        produced.merge(key, amount, Long::sum);
                        return List.of();
                    });
                }
                default -> throw new AssertionError();
            }
            Map<AEKey, Long> pending = accumulator.snapshot();
            for (var key : keys) {
                int currentOperation = operation;
                assertEquals(produced.getOrDefault(key, 0L).longValue(),
                        delivered.getOrDefault(key, 0L) + pending.getOrDefault(key, 0L),
                        () -> "product conservation seed=" + seed + " operation=" + currentOperation + " key=" + key);
            }
            assertTrue(pending.size() <= keys.size());
        }
        for (var stack : accumulator.drainAll()) {
            delivered.merge(stack.what(), stack.amount(), Long::sum);
        }
        assertEquals(produced, delivered, "final drain must deliver every produced item and fluid exactly once");
        assertFalse(accumulator.hasPending());
        System.out.println("STRESS PASS seed=" + seed + " operations=" + operations + " keys=" + keys.size()
                + " actionCounts=" + java.util.Arrays.toString(counts));
    }

    @Test
    void retainsWholeBatchWhenSinkRejectsBeforeDelivery() {
        var accumulator = new VirtualBatchAccumulator();
        var outputs = List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 64),
                new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 32));
        accumulator.enqueue(outputs);
        var expected = accumulator.snapshot();
        accumulator.tickFlush(0, stacks -> List.of());
        assertTrue(accumulator.tickFlush(10, stacks -> {
            throw new IllegalStateException("injected failure before delivery");
        }));
        assertEquals(expected, accumulator.snapshot());
        assertTrue(accumulator.tickFlush(20, stacks -> stacks));
        assertEquals(expected, accumulator.snapshot());
        assertTrue(accumulator.tickFlush(30, stacks -> List.of()));
        assertFalse(accumulator.hasPending());
    }

    @Test
    void serializesManyDistinctItemKeysAndFluidWithoutChangingSaveSchema() {
        var accumulator = new VirtualBatchAccumulator();
        var outputs = new ArrayList<GenericStack>();
        var expectedEntries = new ListTag();
        for (int index = 0; index < 1024; index++) {
            var item = new ItemStack(Items.IRON_INGOT);
            item.getOrCreateTag().putInt("variant", index);
            var key = AEItemKey.of(item);
            long amount = index + 1;
            outputs.add(new GenericStack(key, amount));
            var entry = new CompoundTag();
            entry.putLong("a", amount);
            entry.putString("k", "i");
            entry.put("v", key.toTag());
            expectedEntries.add(entry);
        }
        var fluid = AEFluidKey.of(Fluids.WATER);
        outputs.add(new GenericStack(fluid, AEFluidKey.AMOUNT_BUCKET * 3));
        var fluidEntry = new CompoundTag();
        fluidEntry.putLong("a", AEFluidKey.AMOUNT_BUCKET * 3);
        fluidEntry.putString("k", "f");
        fluidEntry.put("v", fluid.toTag());
        expectedEntries.add(fluidEntry);
        accumulator.enqueue(outputs);
        accumulator.tickFlush(100, stacks -> List.of());
        var expected = new CompoundTag();
        expected.put("pending", expectedEntries);
        expected.putLong("last_flush_tick", 100);
        var saved = new CompoundTag();
        for (int save = 0; save < 10; save++) {
            accumulator.writeToNBT(saved);
            assertEquals(expected, saved, "serialized schema, key ordering and amounts must remain unchanged");
            var restored = new VirtualBatchAccumulator();
            restored.readFromNBT(saved);
            assertEquals(accumulator.snapshot(), restored.snapshot());
            assertEquals(outputs, restored.drainAll());
        }
    }
}
