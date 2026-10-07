package com.moakiee.ae2lt.packaged.gametest;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.VirtualBatchAccumulator;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class VirtualOutputBatchTests {
    private static final Logger LOG = LoggerFactory.getLogger("ae2ltpp-stress");
    private static final int SOAK_ROUNDS = Math.max(1, Math.min(300,
            Integer.getInteger("ae2ltpp.stress.soak_rounds", 10)));

    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void blockedBatchPersistsOnlyOnFlushClockChanges(GameTestHelper helper) {
        for (int index = 0; index < 2; index++) {
            var relative = new BlockPos(2 + 4 * index, 1, 2);
            Block block = index == 0 ? PPBlocks.PACKAGED_PATTERN_PROVIDER.get()
                    : PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get();
            helper.setBlock(relative, block.defaultBlockState());
            var pos = helper.absolutePos(relative);
            var provider = (PackagedPatternProviderBlockEntity) helper.getLevel().getBlockEntity(pos);
            var logic = (PackagedPatternProviderLogic) provider.getLogic();
            var outputs = List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 64));
            var accumulator = new VirtualBatchAccumulator();
            accumulator.enqueue(outputs);
            var saved = new CompoundTag();
            logic.writeToNBT(saved);
            accumulator.writeToNBT(saved);
            logic.readFromNBT(saved);
            var chunk = helper.getLevel().getChunkAt(pos);
            chunk.setUnsaved(false);
            logic.tickAutoReturn();
            helper.assertTrue(chunk.isUnsaved(), "Initial flush clock was not marked for persistence");
            logic.writeToNBT(saved);
            helper.assertTrue(saved.getLong("last_flush_tick") == helper.getLevel().getGameTime(),
                    "Initial flush clock was not saved");
            chunk.setUnsaved(false);
            for (int call = 0; call < 10000; call++) {
                logic.tickAutoReturn();
            }
            helper.assertTrue(!chunk.isUnsaved(), "Unchanged blocked batch repeatedly dirtied its chunk");
            logic.writeToNBT(saved);
            saved.putLong("last_flush_tick", helper.getLevel().getGameTime()
                    - VirtualBatchAccumulator.FLUSH_INTERVAL_TICKS);
            logic.readFromNBT(saved);
            chunk.setUnsaved(false);
            logic.tickAutoReturn();
            helper.assertTrue(chunk.isUnsaved(), "Blocked flush clock update was not marked for persistence");
            logic.writeToNBT(saved);
            var retained = new VirtualBatchAccumulator();
            retained.readFromNBT(saved);
            helper.assertTrue(retained.drainAll().equals(outputs), "Blocked flush changed the backlog");
            chunk.setUnsaved(false);
            for (int call = 0; call < 10000; call++) {
                logic.tickAutoReturn();
            }
            helper.assertTrue(!chunk.isUnsaved(), "Repeated calls after flush dirtied the chunk");
            helper.assertTrue(logic.drainVirtualBatch().equals(outputs) && chunk.isUnsaved(),
                    "Final drain lost products or failed to persist the removal");
        }
        LOG.info("STRESS PASS blocked persistence providers=2 unchangedCalls=40000");
        helper.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void reusedSaveTagDoesNotRestoreClearedOwnership(GameTestHelper helper) {
        for (int index = 0; index < 2; index++) {
            var relative = new BlockPos(2 + 4 * index, 1, 2);
            Block block = index == 0 ? PPBlocks.PACKAGED_PATTERN_PROVIDER.get()
                    : PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get();
            helper.setBlock(relative, block.defaultBlockState());
            var pos = helper.absolutePos(relative);
            var provider = (PackagedPatternProviderBlockEntity) helper.getLevel().getBlockEntity(pos);
            var target = pos.above(3);
            provider.setFlag(target, "save-reuse-test");
            provider.setState(target, "save-reuse-test", "owned");
            var saved = new CompoundTag();
            provider.saveAdditional(saved);
            provider.loadTag(saved);
            helper.assertTrue(provider.hasFlag(target, "save-reuse-test")
                            && "owned".equals(provider.getState(target, "save-reuse-test")),
                    "Live ownership was not persisted before cleanup");
            provider.clearFlagsForTarget(target);
            provider.saveAdditional(saved);
            provider.loadTag(saved);
            helper.assertTrue(!provider.hasFlag(target, "save-reuse-test")
                            && provider.getState(target, "save-reuse-test") == null,
                    "Reused save tag restored cleared ownership");
            helper.assertTrue(!saved.contains("ae2ltpp_adapter_flags")
                            && !saved.contains("ae2ltpp_adapter_states"),
                    "Cleared ownership left stale save fields");
        }
        helper.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void reusedSaveTagDoesNotRestoreClearedUnlockRule(GameTestHelper helper) {
        for (int index = 0; index < 2; index++) {
            var relative = new BlockPos(2 + 4 * index, 1, 2);
            Block block = index == 0 ? PPBlocks.PACKAGED_PATTERN_PROVIDER.get()
                    : PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get();
            helper.setBlock(relative, block.defaultBlockState());
            var provider = (PackagedPatternProviderBlockEntity) helper.getLevel()
                    .getBlockEntity(helper.absolutePos(relative));
            var logic = provider.getLogic();
            var saved = new CompoundTag();
            logic.writeToNBT(saved);
            saved.putString("Ae2ltUnlockMatchMode", "ID_ONLY");
            saved.put("Ae2ltUnlockTemplate", new ItemStack(Items.IRON_INGOT).save(new CompoundTag()));
            logic.readFromNBT(saved);
            var active = new CompoundTag();
            logic.writeToNBT(active);
            helper.assertTrue("ID_ONLY".equals(active.getString("Ae2ltUnlockMatchMode"))
                            && active.contains("Ae2ltUnlockTemplate"),
                    "Active unlock rule was not persisted before cleanup");
            logic.clearContent();
            logic.writeToNBT(saved);
            logic.readFromNBT(saved);
            logic.writeToNBT(active);
            helper.assertTrue(!saved.contains("Ae2ltUnlockMatchMode")
                            && !saved.contains("Ae2ltUnlockTemplate")
                            && !active.contains("Ae2ltUnlockMatchMode")
                            && !active.contains("Ae2ltUnlockTemplate"),
                    "Reused save tag restored a cleared unlock rule");
        }
        helper.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 400)
    public static void completeProviderRecreationPreservesBacklogAndOwnership(GameTestHelper helper) {
        var fixtures = new ArrayList<RecreationFixture>();
        for (int index = 0; index < 4; index++) {
            var relative = new BlockPos(2 + 4 * index, 1, 2);
            Block block = index % 2 == 0 ? PPBlocks.PACKAGED_PATTERN_PROVIDER.get()
                    : PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get();
            helper.setBlock(relative, block.defaultBlockState());
            var pos = helper.absolutePos(relative);
            var provider = (PackagedPatternProviderBlockEntity) helper.getLevel().getBlockEntity(pos);
            var outputs = new ArrayList<GenericStack>();
            for (int variant = 0; variant < 32; variant++) {
                var item = new ItemStack(Items.IRON_INGOT);
                item.getOrCreateTag().putInt("variant", index * 32 + variant);
                outputs.add(new GenericStack(AEItemKey.of(item), variant + 1));
            }
            var accumulator = new VirtualBatchAccumulator();
            accumulator.enqueue(outputs);
            var saved = new CompoundTag();
            provider.getLogic().writeToNBT(saved);
            accumulator.writeToNBT(saved);
            provider.getLogic().readFromNBT(saved);
            provider.setFlag(pos.above(3), "recreation-test");
            provider.setState(pos.above(3), "recreation-test", "owned");
            fixtures.add(new RecreationFixture(pos, List.copyOf(outputs)));
        }
        helper.runAfterDelay(20, () -> runRecreationRound(helper, fixtures, 0));
    }

    private static void runRecreationRound(GameTestHelper helper, List<RecreationFixture> fixtures, int round) {
        var level = helper.getLevel();
        for (var fixture : fixtures) {
            var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(fixture.pos());
            var saved = provider.saveWithFullMetadata();
            var state = level.getBlockState(fixture.pos());
            level.removeBlockEntity(fixture.pos());
            helper.assertTrue(provider.isRemoved(), "Old provider was not detached during recreation");
            var restored = BlockEntity.loadStatic(fixture.pos(), state, saved);
            helper.assertTrue(restored instanceof PackagedPatternProviderBlockEntity && restored != provider,
                    "Full saved provider did not create a distinct block entity");
            level.setBlockEntity(restored);
            var replacement = (PackagedPatternProviderBlockEntity) restored;
            helper.assertTrue(replacement.hasFlag(fixture.pos().above(3), "recreation-test")
                            && "owned".equals(replacement.getState(fixture.pos().above(3), "recreation-test")),
                    "Ownership was lost during block entity recreation at round " + round);
            var logic = (PackagedPatternProviderLogic) replacement.getLogic();
            logic.tickAutoReturn();
            var batchTag = new CompoundTag();
            logic.writeToNBT(batchTag);
            var retained = new VirtualBatchAccumulator();
            retained.readFromNBT(batchTag);
            helper.assertTrue(retained.drainAll().equals(fixture.outputs()),
                    "Full block entity recreation or blocked flush changed the backlog at round " + round);
        }
        if (round < 5) {
            helper.runAfterDelay(20, () -> runRecreationRound(helper, fixtures, round + 1));
            return;
        }
        for (var fixture : fixtures) {
            var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(fixture.pos());
            var logic = (PackagedPatternProviderLogic) provider.getLogic();
            helper.assertTrue(logic.drainVirtualBatch().equals(fixture.outputs()) && logic.drainVirtualBatch().isEmpty(),
                    "Recreated provider could not drain exactly once");
        }
        LOG.info("STRESS PASS provider recreation providers={} rounds={} rebuilds={}",
                fixtures.size(), round + 1, fixtures.size() * (round + 1));
        helper.succeed();
    }

    private record RecreationFixture(BlockPos pos, List<GenericStack> outputs) {}

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 6500)
    public static void multipleProvidersPreserveOutputsAcrossRepeatedBlockedFlushes(GameTestHelper helper) {
        var batches = new ArrayList<ProviderBatch>();
        for (int providerIndex = 0; providerIndex < 8; providerIndex++) {
            var relative = new BlockPos(2 + 4 * (providerIndex % 4), 1, 2 + 4 * (providerIndex / 4));
            Block block = providerIndex % 2 == 0 ? PPBlocks.PACKAGED_PATTERN_PROVIDER.get()
                    : PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get();
            helper.setBlock(relative, block.defaultBlockState());
            var provider = (PackagedPatternProviderBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
            var outputs = new ArrayList<GenericStack>();
            for (int keyIndex = 0; keyIndex < 128; keyIndex++) {
                var item = new ItemStack(Items.IRON_INGOT);
                item.getOrCreateTag().putInt("variant", providerIndex * 128 + keyIndex);
                outputs.add(new GenericStack(AEItemKey.of(item), keyIndex + 1));
            }
            var accumulator = new VirtualBatchAccumulator();
            accumulator.enqueue(outputs);
            var logic = (PackagedPatternProviderLogic) provider.getLogic();
            var saved = new CompoundTag();
            logic.writeToNBT(saved);
            accumulator.writeToNBT(saved);
            logic.readFromNBT(saved);
            batches.add(new ProviderBatch(logic, List.copyOf(outputs)));
        }
        long startedTick = helper.getLevel().getGameTime();
        helper.runAfterDelay(20, () -> runBlockedFlushRound(helper, batches, 0, startedTick));
    }

    private static void runBlockedFlushRound(GameTestHelper helper, List<ProviderBatch> batches, int round,
            long startedTick) {
        for (var batch : batches) {
            var saved = new CompoundTag();
            batch.logic().writeToNBT(saved);
            saved.putLong("last_flush_tick", helper.getLevel().getGameTime() - VirtualBatchAccumulator.FLUSH_INTERVAL_TICKS);
            batch.logic().readFromNBT(saved);
            batch.logic().tickAutoReturn();
            batch.logic().writeToNBT(saved);
            var retained = new VirtualBatchAccumulator();
            retained.readFromNBT(saved);
            helper.assertTrue(retained.drainAll().equals(batch.outputs()),
                    "Blocked flush or reload changed provider outputs at round " + round);
            helper.assertTrue(saved.getLong("last_flush_tick") == helper.getLevel().getGameTime(),
                    "Blocked flush was not exercised at round " + round);
            batch.logic().readFromNBT(saved);
        }
        if (round + 1 < SOAK_ROUNDS) {
            helper.runAfterDelay(20, () -> runBlockedFlushRound(helper, batches, round + 1, startedTick));
            return;
        }
        for (var batch : batches) {
            helper.assertTrue(batch.logic().drainVirtualBatch().equals(batch.outputs()),
                    "Final drain changed the amount or ordering of provider outputs");
            helper.assertTrue(batch.logic().drainVirtualBatch().isEmpty(), "Provider replayed drained outputs");
        }
        long elapsedTicks = helper.getLevel().getGameTime() - startedTick;
        helper.assertTrue(elapsedTicks >= SOAK_ROUNDS * 20L, "Soak did not span the configured server ticks");
        LOG.info("STRESS PASS blocked providers={} keysPerProvider=128 rounds={} checks={} elapsedServerTicks={}",
                batches.size(), round + 1, batches.size() * (round + 1), elapsedTicks);
        helper.succeed();
    }

    private record ProviderBatch(PackagedPatternProviderLogic logic, List<GenericStack> outputs) {}

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 300)
    public static void poweredProvidersDeliverOnceAfterStorageBecomesAvailable(GameTestHelper helper) {
        var fixtures = new ArrayList<PoweredFixture>();
        for (int providerIndex = 0; providerIndex < 2; providerIndex++) {
            var relative = new BlockPos(2, 1, 2 + providerIndex * 6);
            Block block = providerIndex == 0 ? PPBlocks.PACKAGED_PATTERN_PROVIDER.get()
                    : PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get();
            helper.setBlock(relative, block.defaultBlockState());
            helper.setBlock(relative.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            helper.setBlock(relative.east(2), AEBlocks.CHEST.block().defaultBlockState());
            var provider = (PackagedPatternProviderBlockEntity) helper.getLevel()
                    .getBlockEntity(helper.absolutePos(relative));
            var chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative.east(2)));
            var outputs = new ArrayList<GenericStack>();
            for (int keyIndex = 0; keyIndex < 16; keyIndex++) {
                var item = new ItemStack(Items.IRON_INGOT);
                item.getOrCreateTag().putInt("variant", providerIndex * 16 + keyIndex);
                outputs.add(new GenericStack(AEItemKey.of(item), keyIndex + 1));
            }
            fixtures.add(new PoweredFixture(provider, chest, List.copyOf(outputs)));
        }
        helper.runAfterDelay(60, () -> {
            for (var fixture : fixtures) {
                helper.assertTrue(fixture.provider().getMainNode().isActive(), "Powered provider was not active");
                helper.assertTrue(fixture.provider().getMainNode().getGrid() == fixture.chest().getMainNode().getGrid(),
                        "Provider and chest did not join the same grid");
                var logic = (PackagedPatternProviderLogic) fixture.provider().getLogic();
                var accumulator = new VirtualBatchAccumulator();
                accumulator.enqueue(fixture.outputs());
                var saved = new CompoundTag();
                logic.writeToNBT(saved);
                accumulator.writeToNBT(saved);
                logic.readFromNBT(saved);
                logic.tickAutoReturn();
            }
            helper.runAfterDelay(40, () -> {
                for (var fixture : fixtures) {
                    var logic = (PackagedPatternProviderLogic) fixture.provider().getLogic();
                    var saved = new CompoundTag();
                    logic.writeToNBT(saved);
                    logic.readFromNBT(saved);
                    var retained = new VirtualBatchAccumulator();
                    retained.readFromNBT(saved);
                    var amounts = new KeyCounter();
                    retained.snapshot().forEach(amounts::add);
                    var returned = logic.getInternalReturnInv();
                    for (int slot = 0; slot < returned.size(); slot++) {
                        if (returned.getKey(slot) != null) {
                            amounts.add(returned.getKey(slot), returned.getAmount(slot));
                        }
                    }
                    helper.assertTrue(amounts.size() == fixture.outputs().size(), "Blocked grid changed output keys");
                    for (var stack : fixture.outputs()) {
                        helper.assertTrue(amounts.get(stack.what()) == stack.amount(),
                                "Blocked grid or reload lost or duplicated an output");
                    }
                    fixture.chest().setCell(AEItems.ITEM_CELL_1K.stack());
                }
                helper.runAfterDelay(60, () -> {
                    for (var fixture : fixtures) {
                        assertDelivered(helper, fixture);
                        var logic = (PackagedPatternProviderLogic) fixture.provider().getLogic();
                        var saved = new CompoundTag();
                        logic.writeToNBT(saved);
                        logic.readFromNBT(saved);
                    }
                    helper.runAfterDelay(40, () -> {
                        for (var fixture : fixtures) {
                            assertDelivered(helper, fixture);
                        }
                        LOG.info("STRESS PASS powered storage recovery providers=2 keysPerProvider=16 reloads=4");
                        helper.succeed();
                    });
                });
            });
        });
    }

    private static void assertDelivered(GameTestHelper helper, PoweredFixture fixture) {
        var logic = (PackagedPatternProviderLogic) fixture.provider().getLogic();
        var amounts = fixture.chest().getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks();
        helper.assertTrue(amounts.size() == fixture.outputs().size(), "Grid storage has unexpected output keys");
        for (var stack : fixture.outputs()) {
            helper.assertTrue(amounts.get(stack.what()) == stack.amount(),
                    "Grid storage did not receive exactly the promised output amount");
        }
        var saved = new CompoundTag();
        logic.writeToNBT(saved);
        var retained = new VirtualBatchAccumulator();
        retained.readFromNBT(saved);
        helper.assertTrue(!retained.hasPending() && logic.getInternalReturnInv().isEmpty(),
                "Delivered products remain queued for replay");
    }

    private record PoweredFixture(PackagedPatternProviderBlockEntity provider, ChestBlockEntity chest,
            List<GenericStack> outputs) {}

    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void unpoweredProviderRetainsManyDistinctOutputsAcrossFlushAndReload(GameTestHelper helper) {
        var hostRelative = new BlockPos(2, 1, 2);
        helper.setBlock(hostRelative, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(hostRelative));
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        var outputs = new ArrayList<GenericStack>();
        for (int index = 0; index < 1024; index++) {
            var item = new ItemStack(Items.IRON_INGOT);
            item.getOrCreateTag().putInt("variant", index);
            outputs.add(new GenericStack(AEItemKey.of(item), index + 1));
        }
        var accumulator = new VirtualBatchAccumulator();
        accumulator.enqueue(outputs);
        accumulator.tickFlush(helper.getLevel().getGameTime() - VirtualBatchAccumulator.FLUSH_INTERVAL_TICKS,
                stacks -> List.of());
        var saved = new CompoundTag();
        logic.writeToNBT(saved);
        accumulator.writeToNBT(saved);
        logic.readFromNBT(saved);
        logic.tickAutoReturn();
        logic.writeToNBT(saved);
        var retained = new VirtualBatchAccumulator();
        retained.readFromNBT(saved);
        helper.assertTrue(retained.snapshot().equals(accumulator.snapshot()),
                "Unpowered flush changed output variants or amounts");
        helper.assertTrue(saved.getLong("last_flush_tick") == helper.getLevel().getGameTime(),
                "Fixture did not reach the real flush boundary");
        logic.readFromNBT(saved);
        helper.assertTrue(logic.drainVirtualBatch().equals(outputs),
                "Reload changed output ordering, variants or amounts");
        logic.writeToNBT(saved);
        retained.readFromNBT(saved);
        helper.assertTrue(!retained.hasPending() && logic.drainVirtualBatch().isEmpty(),
                "Drained products could be restored or delivered twice");
        helper.succeed();
    }
}
