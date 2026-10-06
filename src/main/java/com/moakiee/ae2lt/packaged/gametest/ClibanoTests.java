package com.moakiee.ae2lt.packaged.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterRecipeTypes;
import com.moakiee.ae2lt.packaged.logic.multiblock.BlockCapabilities;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.SlotHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.compat.ForbiddenArcanusClibanoAdapter;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ClibanoTests {
    private ClibanoTests() {
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 1000)
    public static void poweredCpuOrdersCraftAndReturnExactAmounts(GameTestHelper helper) {
        var relative = new BlockPos(4, 2, 4);
        var front = relative.north();
        place(helper, front, "clibano_center");
        for (var direction : Direction.values()) {
            if (direction.getAxis() == Direction.Axis.Z) continue;
            place(helper, front.relative(direction), direction.getAxis() == Direction.Axis.Y
                    ? "clibano_side_horizontal" : "clibano_side_vertical");
        }
        place(helper, relative, "clibano_main_part");
        var machine = helper.getBlockEntity(relative);
        setStack(machine, 1, item("soul"));
        setStack(machine, 2, new ItemStack(Items.COAL, 8));
        var providerPos = relative.south();
        helper.setBlock(providerPos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(providerPos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.setBlock(providerPos.east(2), AEBlocks.CHEST.block().defaultBlockState());
        helper.setBlock(providerPos.east().above(), AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) helper.getBlockEntity(providerPos);
        var chest = (ChestBlockEntity) helper.getBlockEntity(providerPos.east(2));
        chest.setCell(AEItems.ITEM_CELL_1K.stack());
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.FORBIDDEN_ARCANUS_CLIBANO_ADAPTER.get()));
        provider.setReturnMode(ReturnMode.AUTO);
        var input = AEItemKey.of(item("gilded_darkstone"));
        var output = AEItemKey.of(item("deorum_nugget"));
        provider.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(input, 1)}, new GenericStack[]{new GenericStack(output, 1)}));
        provider.getLogic().updatePatterns();
        var fixture = new CraftingFixture(provider, chest, machine, input, output);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(provider.getMainNode().isActive(), "CPU fixture provider is not active");
            var grid = provider.getMainNode().getGrid();
            helper.assertTrue(grid == chest.getMainNode().getGrid(), "CPU fixture storage is on another grid");
            helper.assertTrue(grid.getCraftingService().getCpus().size() == 1, "CPU fixture has no active crafting CPU");
            helper.assertTrue(grid.getCraftingService().isCraftable(output), "Clibano pattern is not registered in ME");
            helper.assertTrue(grid.getStorageService().getInventory().insert(input, 4, Actionable.MODULATE,
                    IActionSource.empty()) == 4, "CPU fixture could not store ingredients");
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(grid.getStorageService().getCachedInventory().get(input) == 4,
                        "ME crafting snapshot did not receive the stored ingredients");
                beginCpuOrder(helper, fixture, 1, 0);
            });
        });
    }

    private static void beginCpuOrder(GameTestHelper helper, CraftingFixture fixture, long amount, long completed) {
        var requester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public IGridNode getGridNode() {
                return fixture.provider().getMainNode().getNode();
            }
        };
        var future = fixture.provider().getMainNode().getGrid().getCraftingService().beginCraftingCalculation(
                helper.getLevel(), requester, fixture.output(), amount, CalculationStrategy.REPORT_MISSING_ITEMS);
        pollCpuPlan(helper, fixture, amount, completed, future, 100);
    }

    private static void pollCpuPlan(GameTestHelper helper, CraftingFixture fixture, long amount, long completed,
                                   Future<ICraftingPlan> future, int remainingTicks) {
        if (!future.isDone()) {
            helper.assertTrue(remainingTicks > 0, "ME crafting calculation timed out");
            helper.runAfterDelay(1, () -> pollCpuPlan(helper, fixture, amount, completed, future, remainingTicks - 1));
            return;
        }
        ICraftingPlan plan;
        try {
            plan = future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ME crafting calculation interrupted", interrupted);
        } catch (ExecutionException failed) {
            throw new IllegalStateException("ME crafting calculation failed", failed.getCause());
        }
        var service = fixture.provider().getMainNode().getGrid().getCraftingService();
        if (completed == 4) {
            helper.assertTrue(plan.simulation() && plan.missingItems().get(fixture.input()) == 1,
                    "ME calculation did not report depleted Clibano ingredients");
            helper.assertTrue(!service.submitJob(plan, null, null, false, IActionSource.empty()).successful(),
                    "ME CPU accepted a plan with missing ingredients");
            helper.runAfterDelay(40, () -> {
                assertCpuResult(helper, fixture, 4);
                helper.succeed();
            });
            return;
        }
        helper.assertTrue(!plan.simulation() && plan.missingItems().isEmpty(), "ME order has missing ingredients: "
                + "simulation=" + plan.simulation() + " input=" + plan.missingItems().get(fixture.input())
                + " output=" + plan.missingItems().get(fixture.output()));
        helper.assertTrue(plan.finalOutput().what().equals(fixture.output()) && plan.finalOutput().amount() == amount
                && plan.usedItems().get(fixture.input()) == amount, "ME order quantities do not match the recipe");
        var cpu = (CraftingCPUCluster) service.getCpus().iterator().next();
        var submitted = service.submitJob(plan, null, cpu, false, IActionSource.empty());
        helper.assertTrue(submitted.successful(),
                "ME CPU rejected Clibano order: " + submitted.errorCode());
        var link = cpu.craftingLogic.getLastLink();
        helper.assertTrue(cpu.isBusy() && link != null && link.isStandalone(), "ME CPU did not start standalone order");
        pollCpuOrder(helper, fixture, completed + amount, link, 550);
    }

    private static void pollCpuOrder(GameTestHelper helper, CraftingFixture fixture, long completed,
                                    ICraftingLink link, int remainingTicks) {
        helper.assertTrue(!link.isCanceled(), "ME CPU canceled Clibano order");
        if (completed > 1 && SlotHarvestOwnership.load(helper.getLevel(), fixture.machine().getBlockPos(),
                fixture.provider(), "forbidden_arcanus_clibano:harvest") == null && remainingOutputs(fixture) > 0) {
            drainBonusOutputs(helper, fixture);
        }
        if (fixture.provider().getMainNode().getGrid().getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy())) {
            helper.assertTrue(remainingTicks > 0, "ME CPU Clibano order timed out: completed=" + completed
                    + " mode=" + fixture.provider().getReturnMode() + " active=" + fixture.provider().getMainNode().isActive()
                    + " waiting=" + fixture.provider().getMainNode().getGrid().getCraftingService()
                    .getRequestedAmount(fixture.output()) + " provider=" + fixture.provider().saveWithoutMetadata()
                    + " machine=" + fixture.machine().saveWithoutMetadata());
            helper.runAfterDelay(1, () -> pollCpuOrder(helper, fixture, completed, link, remainingTicks - 1));
            return;
        }
        helper.runAfterDelay(40, () -> {
            assertCpuResult(helper, fixture, completed);
            var leftovers = remainingOutputs(fixture);
            var saved = fixture.provider().saveWithoutMetadata();
            fixture.provider().loadTag(saved);
            helper.runAfterDelay(40, () -> {
                assertCpuResult(helper, fixture, completed);
                helper.assertTrue(remainingOutputs(fixture) == leftovers, "Reload harvested unowned Clibano bonus outputs");
                drainBonusOutputs(helper, fixture);
                beginCpuOrder(helper, fixture, completed == 1 ? 3 : 1, completed);
            });
        });
    }

    private static void assertCpuResult(GameTestHelper helper, CraftingFixture fixture, long completed) {
        var grid = fixture.provider().getMainNode().getGrid();
        var stored = grid.getStorageService().getInventory().getAvailableStacks();
        helper.assertTrue(stored.get(fixture.input()) == 4 - completed && stored.get(fixture.output()) == completed,
                "ME CPU lost or duplicated Clibano ingredients or products");
        helper.assertTrue(stored.size() == (completed == 4 ? 1 : 2), "ME storage has unexpected products");
        helper.assertTrue(grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                "ME CPU remained busy after finishing Clibano order");
        helper.assertTrue(fixture.provider().getLogic().getReturnInv().isEmpty(), "Clibano products remain in return inventory");
        for (int slot = 3; slot <= 4; slot++) {
            helper.assertTrue(((ItemStack) call(fixture.machine(), "getStack", new Class<?>[]{int.class}, slot)).isEmpty(),
                    "Completed Clibano order left an input behind");
        }
        remainingOutputs(fixture);
    }

    private static int remainingOutputs(CraftingFixture fixture) {
        int count = 0;
        for (int slot = 5; slot <= 6; slot++) {
            var stack = (ItemStack) call(fixture.machine(), "getStack", new Class<?>[]{int.class}, slot);
            if (!stack.isEmpty() && !fixture.output().equals(AEItemKey.of(stack))) {
                throw new IllegalStateException("Clibano produced an unexpected output");
            }
            count += stack.getCount();
        }
        return count;
    }

    private static void drainBonusOutputs(GameTestHelper helper, CraftingFixture fixture) {
        int leftovers = remainingOutputs(fixture);
        var handler = BlockCapabilities.find(helper.getLevel(), fixture.machine().getBlockPos(),
                ForgeCapabilities.ITEM_HANDLER, Direction.DOWN);
        helper.assertTrue(handler != null, "Clibano output handler is missing");
        int drained = 0;
        for (int slot = 5; slot <= 6; slot++) {
            drained += handler.extractItem(slot, Integer.MAX_VALUE, false).getCount();
        }
        helper.assertTrue(drained == leftovers && remainingOutputs(fixture) == 0,
                "External extraction did not preserve Clibano bonus output amounts");
    }

    private record CraftingFixture(PackagedPatternProviderBlockEntity provider, ChestBlockEntity chest,
                                   BlockEntity machine, AEItemKey input, AEItemKey output) {}

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 100)
    public static void everyRecipeChecksCurrentAndQueuedFire(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var relative = new BlockPos(3, 2, 3);
        place(helper, relative, "clibano_main_part");
        var pos = helper.absolutePos(relative);
        var entity = level.getBlockEntity(pos);
        helper.assertTrue(entity != null, "Clibano block entity missing");
        var fireField = ReflectionSupport.findDeclaredFieldCached(entity.getClass(), "fireType").orElseThrow();
        var adapter = new ForbiddenArcanusClibanoAdapter();
        var type = AdapterRecipeTypes.find(new ResourceLocation("forbidden_arcanus", "clibano_combustion"));
        helper.assertTrue(type != null, "Clibano recipe type missing");
        int recipesChecked = 0;
        int fireRestricted = 0;
        ItemStack[] queued = {ItemStack.EMPTY, item("soul"), item("enchanted_soul")};
        for (var recipe : level.getRecipeManager().getRecipes()) {
            if (recipe.getType() != type) continue;
            var output = recipe.getResultItem(level.registryAccess());
            var choices = recipe.getIngredients().get(0).getItems();
            helper.assertTrue(choices.length > 0 && !output.isEmpty(), "Empty Clibano recipe " + recipe.getId());
            var required = (Enum<?>) call(recipe, "getRequiredFireType", new Class<?>[0]);
            var fireTypes = required.getDeclaringClass().getEnumConstants();
            var input = new GenericStack(AEItemKey.of(choices[0]), 1);
            var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{input}, new GenericStack[]{new GenericStack(AEItemKey.of(output), output.getCount())}),
                    level);
            helper.assertTrue(pattern != null, "Clibano pattern did not decode");
            var binding = adapter.bind(level, pos, pattern);
            helper.assertTrue(binding != null, "Clibano recipe did not bind " + recipe.getId());
            var counter = new KeyCounter();
            counter.add(input.what(), 1);
            for (var fire : fireTypes) {
                fireField.set(entity, fire);
                for (int queuedType = 0; queuedType < queued.length; queuedType++) {
                    setStack(entity, 1, queued[queuedType].copy());
                    var plan = adapter.planWithBinding(level, pos, pattern, new KeyCounter[]{counter},
                            binding.handle(), IActionSource.empty(), new MemoryScope());
                    boolean expected = Math.max(((Enum<?>) fire).ordinal(), queuedType) >= required.ordinal();
                    helper.assertTrue((plan != null) == expected,
                            "Clibano fire admission mismatch: " + recipe.getId() + " current=" + fire + " queued=" + queuedType);
                    helper.assertTrue(((ItemStack) call(entity, "getStack", new Class<?>[]{int.class}, 3)).isEmpty(),
                            "Planning mutated the input slot");
                }
            }
            fireField.set(entity, fireTypes[0]);
            setStack(entity, 1, ItemStack.EMPTY);
            recipesChecked++;
            if (required.ordinal() > 0) fireRestricted++;
        }
        helper.assertTrue(recipesChecked >= 15 && fireRestricted > 0, "Clibano recipe coverage incomplete");
        helper.setBlock(relative, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 250)
    public static void queuedSoulCraftAndOwnedHarvest(GameTestHelper helper) {
        var level = helper.getLevel();
        var relative = new BlockPos(3, 2, 3);
        var front = relative.north();
        place(helper, front, "clibano_center");
        for (var direction : Direction.values()) {
            if (direction.getAxis() == Direction.Axis.Z) continue;
            place(helper, front.relative(direction), direction.getAxis() == Direction.Axis.Y
                    ? "clibano_side_horizontal" : "clibano_side_vertical");
        }
        place(helper, relative, "clibano_main_part");
        var pos = helper.absolutePos(relative);
        var entity = level.getBlockEntity(pos);
        helper.assertTrue(entity != null, "Clibano block entity missing");
        var adapter = new ForbiddenArcanusClibanoAdapter();
        var input = new GenericStack(AEItemKey.of(item("gilded_darkstone")), 1);
        var result = item("deorum_nugget");
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{input}, new GenericStack[]{new GenericStack(AEItemKey.of(result), 1)}), level);
        helper.assertTrue(pattern != null, "Soul-fire pattern did not decode");
        var binding = adapter.bind(level, pos, pattern);
        helper.assertTrue(binding != null, "Soul-fire recipe did not bind");
        var scope = new MemoryScope();
        var counter = new KeyCounter();
        counter.add(input.what(), 1);
        helper.assertTrue(adapter.planWithBinding(level, pos, pattern, new KeyCounter[]{counter},
                binding.handle(), IActionSource.empty(), scope) == null, "Cold Clibano accepted soul-fire recipe");
        setStack(entity, 1, item("soul"));
        setStack(entity, 2, new ItemStack(Items.COAL));
        var plan = adapter.planWithBinding(level, pos, pattern, new KeyCounter[]{counter},
                binding.handle(), IActionSource.empty(), scope);
        helper.assertTrue(plan != null, "Queued soul did not permit dispatch");
        var target = plan.targets().get(0);
        helper.assertTrue(target.customInserter().apply(input, Actionable.SIMULATE) == 1, "Input simulation rejected");
        helper.assertTrue(((ItemStack) call(entity, "getStack", new Class<?>[]{int.class}, 3)).isEmpty(),
                "Simulation mutated input slot");
        helper.assertTrue(target.customInserter().apply(input, Actionable.MODULATE) == 1, "Input insertion rejected");
        plan.onCommit().run();
        var restoredScope = new MemoryScope();
        restoredScope.states.putAll(scope.states);
        var filter = new AllowedOutputFilter();
        filter.allowStrict(AEItemKey.of(result));
        setStack(entity, 5, result.copy());
        helper.assertTrue(adapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope).isEmpty(),
                "Clibano harvested manual output before consuming its input");
        helper.assertTrue(!restoredScope.states.isEmpty(), "Premature harvest lost ownership");
        setStack(entity, 5, ItemStack.EMPTY);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!adapter.canDispatch(level, pos, binding.handle(), restoredScope),
                    "Active Clibano accepted second job");
            helper.assertTrue(adapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope).isEmpty(),
                    "Clibano harvested unfinished craft");
        });
        helper.runAfterDelay(130, () -> {
            try {
                var produced = (ItemStack) call(entity, "getStack", new Class<?>[]{int.class}, 5);
                helper.assertTrue(ItemStack.isSameItemSameTags(produced, result) && produced.getCount() >= 1,
                        "Soul-fire craft did not produce its result: " + entity.saveWithoutMetadata());
                var oversized = produced.copy();
                oversized.grow(2);
                int expectedRemainder = oversized.getCount() - 1;
                setStack(entity, 5, oversized);
                var outputs = adapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope);
                helper.assertTrue(outputs.size() == 1 && outputs.get(0).what().equals(AEItemKey.of(result))
                        && outputs.get(0).amount() == 1, "Soul-fire craft was not harvested exactly once: outputs="
                        + outputs + " state=" + entity.saveWithoutMetadata() + " ownership=" + restoredScope.states);
                helper.assertTrue(((ItemStack) call(entity, "getStack", new Class<?>[]{int.class}, 5)).getCount()
                        == expectedRemainder, "Clibano harvested output beyond the promised amount");
                helper.assertTrue(adapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope).isEmpty(),
                        "Clibano harvested output twice");
                setStack(entity, 6, result.copy());
                helper.assertTrue(adapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope).isEmpty(),
                        "Clibano stole manual output");
            } finally {
                helper.setBlock(relative, Blocks.AIR);
            }
            helper.assertTrue(helper.getBlockState(front).isAir(), "Clibano center was not dismantled");
            for (var direction : Direction.values()) {
                if (direction.getAxis() == Direction.Axis.Z) continue;
                helper.assertTrue(helper.getBlockState(front.relative(direction)).isAir(),
                        "Clibano side was not dismantled: " + direction);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 100)
    public static void reloadBeforeWorldAttachmentPreservesDismantleStates(GameTestHelper helper) {
        var level = helper.getLevel();
        var relative = new BlockPos(3, 2, 3);
        var front = relative.north();
        var originals = new HashMap<BlockPos, BlockState>();
        originals.put(front, Blocks.OAK_LOG.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        originals.put(front.above(), Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        originals.put(front.below(), Blocks.COBBLESTONE.defaultBlockState());
        originals.put(front.west(), Blocks.OAK_LOG.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        originals.put(front.east(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        for (var entry : originals.entrySet()) {
            var partPos = entry.getKey();
            place(helper, partPos, partPos.equals(front) ? "clibano_center"
                    : partPos.getY() == front.getY() ? "clibano_side_vertical" : "clibano_side_horizontal");
            call(helper.getBlockEntity(partPos), "setReplaceState", new Class<?>[]{BlockState.class}, entry.getValue());
        }
        place(helper, relative, "clibano_main_part");
        boolean reloadsCompleted = false;
        try {
            for (int round = 0; round < 100; round++) {
                for (var entry : originals.entrySet()) {
                    var partPos = helper.absolutePos(entry.getKey());
                    var previous = level.getBlockEntity(partPos);
                    var saved = previous.saveWithFullMetadata();
                    var restored = BlockEntity.loadStatic(partPos, previous.getBlockState(), saved);
                    helper.assertTrue(restored != null && restored != previous && restored.getLevel() == null,
                            "Clibano part did not load before world attachment");
                    helper.assertTrue(entry.getValue().equals(call(restored, "getReplaceState", new Class<?>[0])),
                            "Clibano part lost dismantle state before world attachment: " + entry.getKey()
                                    + " round=" + round + " saved=" + saved);
                    helper.assertTrue("south".equals(restored.saveWithoutMetadata().getString("MainDirection")),
                            "Clibano part lost its main direction during reload");
                    level.removeBlockEntity(partPos);
                    level.setBlockEntity(restored);
                    restored.load(saved);
                    helper.assertTrue(entry.getValue().equals(call(restored, "getReplaceState", new Class<?>[0])),
                            "World-attached Clibano part lost dismantle state");
                }
            }
            reloadsCompleted = true;
        } finally {
            if (!reloadsCompleted) {
                for (var entry : originals.entrySet()) {
                    var part = helper.getBlockEntity(entry.getKey());
                    call(part, "setReplaceState", new Class<?>[]{BlockState.class}, entry.getValue());
                }
            }
            helper.setBlock(relative, Blocks.AIR);
        }
        for (var entry : originals.entrySet()) {
            helper.assertTrue(helper.getBlockState(entry.getKey()).equals(entry.getValue()),
                    "Dismantled Clibano did not restore original block properties at " + entry.getKey());
        }
        helper.succeed();
    }

    private static void place(GameTestHelper helper, BlockPos relative, String name) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("forbidden_arcanus", name));
        helper.assertTrue(block != null, "Clibano block missing " + name);
        helper.setBlock(relative, block.defaultBlockState());
        if (!name.equals("clibano_main_part")) {
            var entity = helper.getBlockEntity(relative);
            call(entity, "setReplaceState", new Class<?>[]{BlockState.class}, Blocks.AIR.defaultBlockState());
            call(entity, "setMainDirection", new Class<?>[]{Direction.class}, Direction.SOUTH);
            entity.setChanged();
        }
    }

    private static ItemStack item(String name) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("forbidden_arcanus", name));
        if (item == null) throw new IllegalStateException("Clibano item missing " + name);
        return new ItemStack(item);
    }

    private static void setStack(Object entity, int slot, ItemStack stack) {
        call(entity, "setStack", new Class<?>[]{int.class, ItemStack.class}, slot, stack);
    }

    private static Object call(Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
        var method = ReflectionSupport.findMethodCached(target.getClass(), name, parameterTypes).orElseThrow();
        return ReflectionSupport.invokeMutation(method, target, arguments).orElse(null);
    }

    private static final class MemoryScope implements AdapterPersistentScope {
        private final Map<String, String> states = new HashMap<>();

        @Override public void setFlag(BlockPos pos, String key) { states.put(key, "true"); }
        @Override public boolean hasFlag(BlockPos pos, String key) { return states.containsKey(key); }
        @Override public void clearFlag(BlockPos pos, String key) { states.remove(key); }
        @Override public void setState(BlockPos pos, String key, String value) { states.put(key, value); }
        @Override public String getState(BlockPos pos, String key) { return states.get(key); }
        @Override public void clearState(BlockPos pos, String key) { states.remove(key); }
    }
}
