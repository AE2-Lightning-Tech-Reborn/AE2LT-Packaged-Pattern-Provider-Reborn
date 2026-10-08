package com.moakiee.ae2lt.packaged.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class AvaritiaNeoTests {
    private static final int TOTAL_CRAFTS = 4;

    private AvaritiaNeoTests() {
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 1200)
    public static void poweredCpuOrdersNativeShapedRecipe(GameTestHelper helper) {
        startOrders(helper, "endest_pearl", "RecipeExtremeShaped", 1);
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 1200)
    public static void poweredCpuOrdersNativeShapelessRecipe(GameTestHelper helper) {
        startOrders(helper, "cosmic_meatballs", "RecipeExtremeShapeless", 2);
    }

    private static void startOrders(GameTestHelper helper, String recipeName, String recipeClass, int outputCount) {
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().byKey(new ResourceLocation("avaritia", recipeName)).orElseThrow();
        helper.assertTrue(recipe.getClass().getName().equals("net.byAqua3.avaritia.recipe." + recipeClass),
                "Avaritia CPU fixture did not load the native extreme recipe");
        var amounts = new LinkedHashMap<AEItemKey, Long>();
        for (var ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) {
                var choices = ingredient.getItems();
                helper.assertTrue(choices.length > 0, "Avaritia native recipe has an empty ingredient");
                amounts.merge(AEItemKey.of(choices[0]), 1L, Long::sum);
            }
        }
        var result = recipe.getResultItem(level.registryAccess());
        helper.assertTrue(!amounts.isEmpty() && result.getCount() == outputCount,
                "Avaritia native recipe quantities changed");
        var output = AEItemKey.of(result);
        helper.assertTrue(!amounts.containsKey(output), "Avaritia fixture output overlaps inputs");
        var tableRelative = new BlockPos(4, 2, 4);
        var tableBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("avaritia", "extreme_crafting_table"));
        helper.assertTrue(tableBlock != null, "Avaritia extreme table is missing");
        helper.setBlock(tableRelative, tableBlock.defaultBlockState());
        var table = level.getBlockEntity(helper.absolutePos(tableRelative));
        Container matrix;
        Container preview;
        try {
            matrix = (Container) table.getClass().getField("matrix").get(table);
            preview = (Container) table.getClass().getField("result").get(table);
        } catch (ReflectiveOperationException failed) {
            throw new IllegalStateException("Avaritia table inventory layout changed", failed);
        }
        helper.assertTrue(matrix.getContainerSize() == 81 && preview.getContainerSize() == 1,
                "Avaritia CPU fixture has unexpected table inventories");
        var providerRelative = tableRelative.south();
        helper.setBlock(providerRelative, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(providerRelative.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.setBlock(providerRelative.east(2), AEBlocks.CHEST.block().defaultBlockState());
        helper.setBlock(providerRelative.east().above(), AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(helper.absolutePos(providerRelative));
        var chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(providerRelative.east(2)));
        chest.setCell(AEItems.ITEM_CELL_1K.stack());
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.AVARITIA_EXTREME_TABLE_ADAPTER.get()));
        provider.setReturnMode(ReturnMode.AUTO);
        var inputs = amounts.entrySet().stream()
                .map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toArray(GenericStack[]::new);
        provider.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                inputs, new GenericStack[]{new GenericStack(output, outputCount)}));
        provider.getLogic().updatePatterns();
        var fixture = new CraftingFixture(provider, matrix, preview, amounts, output, outputCount);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(provider.getMainNode().isActive(), "Avaritia CPU provider is not active");
            var grid = provider.getMainNode().getGrid();
            helper.assertTrue(grid == chest.getMainNode().getGrid(), "Avaritia storage is on another grid");
            helper.assertTrue(grid.getCraftingService().getCpus().size() == 1, "Avaritia fixture has no active CPU");
            helper.assertTrue(grid.getCraftingService().isCraftable(output), "Avaritia pattern is not registered in ME");
            for (var entry : amounts.entrySet()) {
                long supply = entry.getValue() * TOTAL_CRAFTS;
                helper.assertTrue(grid.getStorageService().getInventory().insert(entry.getKey(), supply,
                        Actionable.MODULATE, IActionSource.empty()) == supply, "Avaritia fixture could not store inputs");
            }
            helper.runAfterDelay(5, () -> beginOrder(helper, fixture, 1, 0));
        });
    }

    private static void beginOrder(GameTestHelper helper, CraftingFixture fixture, int crafts, int completed) {
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
        var service = fixture.provider().getMainNode().getGrid().getCraftingService();
        var future = service.beginCraftingCalculation(helper.getLevel(), requester, fixture.output(),
                (long) crafts * fixture.outputCount(), CalculationStrategy.REPORT_MISSING_ITEMS);
        pollPlan(helper, fixture, crafts, completed, future, 100);
    }

    private static void pollPlan(GameTestHelper helper, CraftingFixture fixture, int crafts, int completed,
                                 Future<ICraftingPlan> future, int remainingTicks) {
        if (!future.isDone()) {
            helper.assertTrue(remainingTicks > 0, "Avaritia ME calculation timed out");
            helper.runAfterDelay(1, () -> pollPlan(helper, fixture, crafts, completed, future, remainingTicks - 1));
            return;
        }
        ICraftingPlan plan;
        try {
            plan = future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Avaritia ME calculation interrupted", interrupted);
        } catch (ExecutionException failed) {
            throw new IllegalStateException("Avaritia ME calculation failed", failed.getCause());
        }
        var service = fixture.provider().getMainNode().getGrid().getCraftingService();
        if (completed == TOTAL_CRAFTS) {
            helper.assertTrue(plan.simulation() && plan.missingItems().size() == fixture.inputs().size(),
                    "Avaritia calculation did not report all depleted inputs");
            for (var entry : fixture.inputs().entrySet()) {
                helper.assertTrue(plan.missingItems().get(entry.getKey()) == entry.getValue(),
                        "Avaritia calculation reported incorrect missing input quantities");
            }
            helper.assertTrue(!service.submitJob(plan, null, null, false, IActionSource.empty()).successful(),
                    "Avaritia CPU accepted an order with missing ingredients");
            helper.runAfterDelay(40, () -> {
                assertResult(helper, fixture, completed);
                helper.succeed();
            });
            return;
        }
        helper.assertTrue(!plan.simulation() && plan.missingItems().isEmpty(), "Avaritia ME order has missing inputs");
        helper.assertTrue(plan.finalOutput().what().equals(fixture.output())
                && plan.finalOutput().amount() == (long) crafts * fixture.outputCount(), "Avaritia output count changed");
        for (var entry : fixture.inputs().entrySet()) {
            helper.assertTrue(plan.usedItems().get(entry.getKey()) == entry.getValue() * crafts,
                    "Avaritia CPU order uses incorrect input quantities");
        }
        fixture.matrix().setItem(0, new ItemStack(Items.EMERALD));
        var cpu = (CraftingCPUCluster) service.getCpus().iterator().next();
        var submitted = service.submitJob(plan, null, cpu, false, IActionSource.empty());
        helper.assertTrue(submitted.successful(), "Avaritia CPU rejected order: " + submitted.errorCode());
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(cpu.isBusy(), "Avaritia CPU ignored the occupied manual table");
            var manual = fixture.matrix().getItem(0);
            helper.assertTrue(manual.is(Items.EMERALD) && manual.getCount() == 1 && fixture.preview().isEmpty(),
                    "Avaritia virtual craft changed manual table contents");
            var stored = fixture.provider().getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks();
            helper.assertTrue(stored.get(fixture.output()) == (long) completed * fixture.outputCount(),
                    "Avaritia CPU produced output while the manual table was occupied");
            fixture.matrix().removeItemNoUpdate(0);
            fixture.matrix().setChanged();
            pollOrder(helper, fixture, completed + crafts, cpu, 300);
        });
    }

    private static void pollOrder(GameTestHelper helper, CraftingFixture fixture, int completed,
                                  CraftingCPUCluster cpu, int remainingTicks) {
        if (cpu.isBusy()) {
            helper.assertTrue(remainingTicks > 0,
                    "Avaritia CPU order timed out: " + fixture.provider().saveWithoutMetadata());
            helper.runAfterDelay(1, () -> pollOrder(helper, fixture, completed, cpu, remainingTicks - 1));
            return;
        }
        helper.runAfterDelay(40, () -> {
            assertResult(helper, fixture, completed);
            var saved = fixture.provider().saveWithoutMetadata();
            fixture.provider().loadTag(saved);
            helper.runAfterDelay(40, () -> {
                assertResult(helper, fixture, completed);
                beginOrder(helper, fixture, completed == 1 ? 3 : 1, completed);
            });
        });
    }

    private static void assertResult(GameTestHelper helper, CraftingFixture fixture, int completed) {
        var grid = fixture.provider().getMainNode().getGrid();
        var stored = grid.getStorageService().getInventory().getAvailableStacks();
        for (var entry : fixture.inputs().entrySet()) {
            helper.assertTrue(stored.get(entry.getKey()) == entry.getValue() * (TOTAL_CRAFTS - completed),
                    "Avaritia CPU lost or duplicated inputs");
        }
        helper.assertTrue(stored.get(fixture.output()) == (long) completed * fixture.outputCount(),
                "Avaritia CPU lost or duplicated native recipe outputs");
        helper.assertTrue(stored.size() == (completed == TOTAL_CRAFTS ? 1 : fixture.inputs().size() + 1),
                "Avaritia ME storage has unexpected keys");
        helper.assertTrue(grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                "Avaritia CPU remained busy after completing order");
        helper.assertTrue(fixture.provider().getLogic().getReturnInv().isEmpty(), "Avaritia return inventory is not empty");
        helper.assertTrue(fixture.matrix().isEmpty() && fixture.preview().isEmpty(), "Avaritia virtual craft changed the table");
    }

    private record CraftingFixture(PackagedPatternProviderBlockEntity provider, Container matrix, Container preview,
                                   Map<AEItemKey, Long> inputs, AEItemKey output, int outputCount) {}
}
