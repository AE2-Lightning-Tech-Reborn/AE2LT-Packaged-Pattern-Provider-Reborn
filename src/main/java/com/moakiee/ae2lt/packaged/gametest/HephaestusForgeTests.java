package com.moakiee.ae2lt.packaged.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import com.moakiee.ae2lt.packaged.logic.multiblock.AcceptedInsertion;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.SlotHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.compat.ForbiddenArcanusHephaestusForgeAdapter;
import com.moakiee.ae2lt.packaged.item.HephaestusUpgradeReceiptItem;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class HephaestusForgeTests {
    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 1800)
    public static void poweredCpuWaitsForEssencesAndCompletesReloadedRituals(GameTestHelper helper) {
        var relative = new BlockPos(7, 2, 7);
        placeForge(helper, relative);
        var forge = helper.getBlockEntity(relative);
        var wax = ForgeRegistries.ITEMS.getValue(new ResourceLocation("forbidden_arcanus", "wax"));
        var result = ForgeRegistries.ITEMS.getValue(new ResourceLocation("forbidden_arcanus", "ferrognetic_mixture"));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(wax != null && result != null, "CPU ritual items missing");
        var inputs = List.of(
                new GenericStack(AEItemKey.of(Items.LODESTONE), 1),
                new GenericStack(AEItemKey.of(Items.CLAY_BALL), 2),
                new GenericStack(AEItemKey.of(wax), 2),
                new GenericStack(AEItemKey.of(Items.SLIME_BALL), 2),
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
        var output = AEItemKey.of(result);
        var providerRelative = relative.above();
        helper.setBlock(providerRelative, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(providerRelative.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.setBlock(providerRelative.east(2), AEBlocks.CHEST.block().defaultBlockState());
        helper.setBlock(providerRelative.east().above(), AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) helper.getBlockEntity(providerRelative);
        var chest = (ChestBlockEntity) helper.getBlockEntity(providerRelative.east(2));
        chest.setCell(AEItems.ITEM_CELL_1K.stack());
        provider.getAdapterInv().setItemDirect(0,
                new ItemStack(PPItems.FORBIDDEN_ARCANUS_HEPHAESTUS_FORGE_ADAPTER.get()));
        provider.setReturnMode(ReturnMode.AUTO);
        provider.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                inputs.toArray(GenericStack[]::new), new GenericStack[]{new GenericStack(output, 1)}));
        provider.getLogic().updatePatterns();
        var fixture = new RitualCpuFixture(provider, forge, inputs, output);
        helper.runAfterDelay(60, () -> {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(provider.getMainNode().isActive(), "Ritual CPU provider is not active");
            var grid = provider.getMainNode().getGrid();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(grid == chest.getMainNode().getGrid(), "Ritual CPU storage is on another grid");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(grid.getCraftingService().getCpus().size() == 1, "Ritual fixture has no active CPU");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(grid.getCraftingService().isCraftable(output), "Ritual pattern is not registered in ME");
            for (var input : inputs) {
                com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(grid.getStorageService().getInventory().insert(input.what(), input.amount() * 2,
                        Actionable.MODULATE, IActionSource.empty()) == input.amount() * 2,
                        "Ritual fixture could not store input " + input.what());
            }
            beginRitualCpuOrder(helper, fixture, 0);
        });
    }

    private static void beginRitualCpuOrder(GameTestHelper helper, RitualCpuFixture fixture, int completed) {
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
                helper.getLevel(), requester, fixture.output(), 1, CalculationStrategy.REPORT_MISSING_ITEMS);
        pollRitualCpuPlan(helper, fixture, completed, future, 100);
    }

    private static void pollRitualCpuPlan(GameTestHelper helper, RitualCpuFixture fixture, int completed,
                                        Future<ICraftingPlan> future, int remainingTicks) {
        if (!future.isDone()) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(remainingTicks > 0, "Ritual ME calculation timed out");
            helper.runAfterDelay(1, () -> pollRitualCpuPlan(helper, fixture, completed, future, remainingTicks - 1));
            return;
        }
        ICraftingPlan plan;
        try {
            plan = future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ritual ME calculation interrupted", interrupted);
        } catch (ExecutionException failed) {
            throw new IllegalStateException("Ritual ME calculation failed", failed.getCause());
        }
        var service = fixture.provider().getMainNode().getGrid().getCraftingService();
        if (completed == 2) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan.simulation(), "Depleted ritual ingredients did not require simulation");
            for (var input : fixture.inputs()) {
                com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan.missingItems().get(input.what()) == input.amount(),
                        "Ritual calculation reported incorrect missing input " + input.what());
            }
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!service.submitJob(plan, null, null, false, IActionSource.empty()).successful(),
                    "Ritual CPU accepted an order with missing ingredients");
            helper.runAfterDelay(40, () -> {
                assertRitualCpuResult(helper, fixture, 2);
                helper.succeed();
            });
            return;
        }
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!plan.simulation() && plan.missingItems().isEmpty(), "Ritual ME order has missing ingredients");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan.finalOutput().what().equals(fixture.output()) && plan.finalOutput().amount() == 1,
                "Ritual plan has incorrect output");
        for (var input : fixture.inputs()) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan.usedItems().get(input.what()) == input.amount(),
                    "Ritual plan has incorrect input amount " + input.what());
        }
        var cpu = (CraftingCPUCluster) service.getCpus().iterator().next();
        var submitted = service.submitJob(plan, null, cpu, false, IActionSource.empty());
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(submitted.successful() && cpu.isBusy(), "Ritual CPU rejected order: " + submitted.errorCode());
        helper.runAfterDelay(40, () -> {
            var manager = invoke(fixture.forge(), "getRitualManager", new Class<?>[0]);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(cpu.isBusy() && Boolean.FALSE.equals(invoke(manager, "isRitualActive", new Class<?>[0])),
                    "Ritual did not wait for missing essences");
            assertRitualSlotsEmpty(helper, fixture.forge());
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(SlotHarvestOwnership.load(helper.getLevel(), fixture.forge().getBlockPos(),
                    fixture.provider(), "forbidden_arcanus_hephaestus_forge:harvest") == null,
                    "Essence-blocked ritual created output ownership");
            var stored = fixture.provider().getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(stored.get(fixture.output()) == completed, "Essence-blocked ritual fabricated output");
            var essences = invoke(fixture.forge(), "getEssenceManager", new Class<?>[0]);
            invoke(essences, "setAureal", new Class<?>[]{int.class}, 100);
            invoke(essences, "setBlood", new Class<?>[]{int.class}, 1250);
            invoke(essences, "setSouls", new Class<?>[]{int.class}, 2);
            pollRitualCpuOrder(helper, fixture, cpu, completed + 1, false, 650);
        });
    }

    private static void pollRitualCpuOrder(GameTestHelper helper, RitualCpuFixture fixture, CraftingCPUCluster cpu,
                                         int completed, boolean reloaded, int remainingTicks) {
        var manager = invoke(fixture.forge(), "getRitualManager", new Class<?>[0]);
        if (!reloaded && Boolean.TRUE.equals(invoke(manager, "isRitualActive", new Class<?>[0]))) {
            var claim = SlotHarvestOwnership.load(helper.getLevel(), fixture.forge().getBlockPos(),
                    fixture.provider(), "forbidden_arcanus_hephaestus_forge:harvest");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(claim != null, "Active CPU ritual has no output ownership");
            fixture.provider().loadTag(fixture.provider().saveWithoutMetadata());
            var restored = SlotHarvestOwnership.load(helper.getLevel(), fixture.forge().getBlockPos(),
                    fixture.provider(), "forbidden_arcanus_hephaestus_forge:harvest");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(restored != null && claim.createdTick() == restored.createdTick()
                    && claim.recipeId().equals(restored.recipeId())
                    && ItemStack.matches(claim.expectedOutput(), restored.expectedOutput()),
                    "In-flight provider reload lost ritual ownership");
            helper.runAfterDelay(1, () -> pollRitualCpuOrder(helper, fixture, cpu, completed, true, remainingTicks - 1));
            return;
        }
        if (cpu.isBusy()) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(remainingTicks > 0, "Ritual CPU order timed out: " + fixture.forge().saveWithoutMetadata()
                    + " provider=" + fixture.provider().saveWithoutMetadata());
            helper.runAfterDelay(1, () -> pollRitualCpuOrder(helper, fixture, cpu, completed, reloaded, remainingTicks - 1));
            return;
        }
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(reloaded, "Ritual completed without exercising in-flight reload");
        helper.runAfterDelay(40, () -> {
            assertRitualCpuResult(helper, fixture, completed);
            fixture.provider().loadTag(fixture.provider().saveWithoutMetadata());
            helper.runAfterDelay(40, () -> {
                assertRitualCpuResult(helper, fixture, completed);
                beginRitualCpuOrder(helper, fixture, completed);
            });
        });
    }

    private static void assertRitualCpuResult(GameTestHelper helper, RitualCpuFixture fixture, int completed) {
        var grid = fixture.provider().getMainNode().getGrid();
        var stored = grid.getStorageService().getInventory().getAvailableStacks();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(stored.get(fixture.output()) == completed, "Ritual CPU lost or duplicated output");
        for (var input : fixture.inputs()) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(stored.get(input.what()) == input.amount() * (2 - completed),
                    "Ritual CPU lost or duplicated input " + input.what());
        }
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(stored.size() == (completed == 2 ? 1 : fixture.inputs().size() + 1),
                "Ritual ME storage contains unexpected keys");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                "Ritual CPU remained busy after completing order");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.provider().getLogic().getReturnInv().isEmpty(), "Ritual output remains in return inventory");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(SlotHarvestOwnership.load(helper.getLevel(), fixture.forge().getBlockPos(), fixture.provider(),
                "forbidden_arcanus_hephaestus_forge:harvest") == null, "Completed ritual retained output ownership");
        assertRitualSlotsEmpty(helper, fixture.forge());
        var essences = invoke(fixture.forge(), "getEssenceManager", new Class<?>[0]);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(Integer.valueOf(0).equals(invoke(essences, "getAureal", new Class<?>[0]))
                && Integer.valueOf(0).equals(invoke(essences, "getBlood", new Class<?>[0]))
                && Integer.valueOf(0).equals(invoke(essences, "getSouls", new Class<?>[0])),
                "Ritual did not consume the exact supplied essences");
    }

    private static void assertRitualSlotsEmpty(GameTestHelper helper, BlockEntity forge) {
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(((ItemStack) invoke(forge, "getStack", new Class<?>[]{int.class}, 4)).isEmpty(),
                "Ritual main slot is not empty");
        int[][] offsets = {{-3, 0}, {-2, -2}, {0, -3}, {2, -2}, {3, 0}, {2, 2}, {0, 3}, {-2, 2}};
        for (var offset : offsets) {
            var pedestal = helper.getLevel().getBlockEntity(forge.getBlockPos().offset(offset[0], 0, offset[1]));
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(pedestal != null && ((ItemStack) invoke(pedestal, "getStack", new Class<?>[0])).isEmpty(),
                    "Ritual pedestal retained an input");
        }
    }

    private record RitualCpuFixture(PackagedPatternProviderBlockEntity provider, BlockEntity forge,
                                    List<GenericStack> inputs, AEItemKey output) {}

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 100)
    public static void everyItemRitualBinds(GameTestHelper helper) {
        var level = helper.getLevel();
        var relative = new BlockPos(2, 1, 2);
        place(helper, relative, "hephaestus_forge");
        var pos = helper.absolutePos(relative);
        var adapter = new ForbiddenArcanusHephaestusForgeAdapter();
        var registry = level.registryAccess().registryOrThrow(ResourceKey.<Registry<Object>>createRegistryKey(
                new ResourceLocation("forbidden_arcanus", "hephaestus_forge/ritual")));
        int itemRituals = 0;
        int upgradeRituals = 0;
        for (var entry : registry.entrySet()) {
            var result = invoke(entry.getValue(), "result", new Class<?>[0]);
            if (result != null && result.getClass().getName().endsWith(".UpgradeTierResult")) {
                var main = (Ingredient) invoke(entry.getValue(), "mainIngredient", new Class<?>[0]);
                var inputs = (List<?>) invoke(entry.getValue(), "inputs", new Class<?>[0]);
                var amounts = new LinkedHashMap<AEItemKey, Long>();
                amounts.merge(AEItemKey.of(main.getItems()[0]), 1L, Long::sum);
                for (var ritualInput : inputs) {
                    var ingredient = (Ingredient) invoke(ritualInput, "ingredient", new Class<?>[0]);
                    var amount = (Integer) invoke(ritualInput, "amount", new Class<?>[0]);
                    amounts.merge(AEItemKey.of(ingredient.getItems()[0]), amount.longValue(), Long::sum);
                }
                var encodedInputs = amounts.entrySet().stream()
                        .map(e -> new GenericStack(e.getKey(), e.getValue())).toArray(GenericStack[]::new);
                int tier = (Integer) invoke(result, "getUpgradedTier", new Class<?>[0]);
                var receipt = HephaestusUpgradeReceiptItem.forTier(PPItems.HEPHAESTUS_UPGRADE_RECEIPT.get(), tier);
                var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(encodedInputs,
                        new GenericStack[]{new GenericStack(AEItemKey.of(receipt), 1)}), level);
                com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(pattern != null && adapter.bind(level, pos, pattern) != null,
                        "Upgrade ritual did not bind: " + entry.getKey().location());
                upgradeRituals++;
                continue;
            }
            if (result == null || !result.getClass().getName().endsWith(".CreateItemResult")) continue;
            var output = invoke(result, "getResult", new Class<?>[0]);
            if (!(output instanceof ItemStack item) || item.isEmpty()) continue;
            var main = invoke(entry.getValue(), "mainIngredient", new Class<?>[0]);
            var inputs = invoke(entry.getValue(), "inputs", new Class<?>[0]);
            if (!(main instanceof Ingredient mainIngredient) || !(inputs instanceof List<?> ritualInputs)) continue;
            var amounts = new LinkedHashMap<AEItemKey, Long>();
            var mainChoices = mainIngredient.getItems();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(mainChoices.length > 0, "Item ritual has no main ingredient choice");
            amounts.merge(AEItemKey.of(mainChoices[0]), 1L, Long::sum);
            for (var ritualInput : ritualInputs) {
                var ingredient = invoke(ritualInput, "ingredient", new Class<?>[0]);
                var amount = invoke(ritualInput, "amount", new Class<?>[0]);
                com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(ingredient instanceof Ingredient && amount instanceof Integer,
                        "Malformed ritual input");
                var choices = ((Ingredient) ingredient).getItems();
                com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(choices.length > 0, "Item ritual has empty pedestal ingredient");
                amounts.merge(AEItemKey.of(choices[0]), ((Integer) amount).longValue(), Long::sum);
            }
            var encodedInputs = amounts.entrySet().stream()
                    .map(e -> new GenericStack(e.getKey(), e.getValue())).toArray(GenericStack[]::new);
            var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(encodedInputs,
                    new GenericStack[]{new GenericStack(AEItemKey.of(item), item.getCount())}), level);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(pattern != null, "Could not decode ritual pattern " + entry.getKey().location());
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.bind(level, pos, pattern) != null,
                    "Item ritual did not bind: " + entry.getKey().location());
            itemRituals++;
        }
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(itemRituals > 0, "No item rituals were loaded");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(upgradeRituals == 4, "Not all upgrade rituals loaded and bound");
        helper.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 700)
    public static void itemRitualLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var relative = new BlockPos(7, 2, 7);
        var pos = helper.absolutePos(relative);
        placeForge(helper, relative);
        var entity = level.getBlockEntity(pos);
        var adapter = new ForbiddenArcanusHephaestusForgeAdapter();
        var wax = ForgeRegistries.ITEMS.getValue(new ResourceLocation("forbidden_arcanus", "wax"));
        var result = ForgeRegistries.ITEMS.getValue(new ResourceLocation("forbidden_arcanus", "ferrognetic_mixture"));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(wax != null && result != null, "Ritual items missing");
        GenericStack[] supplied = {
                new GenericStack(AEItemKey.of(Items.LODESTONE), 1),
                new GenericStack(AEItemKey.of(Items.CLAY_BALL), 2),
                new GenericStack(AEItemKey.of(wax), 2),
                new GenericStack(AEItemKey.of(Items.SLIME_BALL), 2),
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)
        };
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(supplied,
                new GenericStack[]{new GenericStack(AEItemKey.of(result), 1)}), level);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(pattern != null, "Cannot decode processing pattern");
        var binding = adapter.bind(level, pos, pattern);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(binding != null, "Item ritual did not bind");
        var scope = new MemoryScope();
        SlotHarvestOwnership.store(level, pos, scope, "forbidden_arcanus_hephaestus_forge:harvest",
                new ItemStack(result), new ResourceLocation("forbidden_arcanus", "ferrognetic_mixture"));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.canDispatch(level, pos, binding.handle(), scope),
                "Abandoned item ritual claim blocked idle empty forge");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(SlotHarvestOwnership.load(level, pos, scope, "forbidden_arcanus_hephaestus_forge:harvest") == null,
                "Abandoned item ritual claim was not cleared");
        var counter = new KeyCounter();
        for (var input : supplied) counter.add(input.what(), input.amount());
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.planWithBinding(level, pos, pattern, new KeyCounter[]{counter}, binding.handle(),
                IActionSource.empty(), scope) == null, "Empty essences must block planning");
        var essences = invoke(entity, "getEssenceManager", new Class<?>[0]);
        invoke(essences, "setAureal", new Class<?>[]{int.class}, 100);
        invoke(essences, "setBlood", new Class<?>[]{int.class}, 1250);
        invoke(essences, "setSouls", new Class<?>[]{int.class}, 2);
        var plan = adapter.planWithBinding(level, pos, pattern, new KeyCounter[]{counter}, binding.handle(), IActionSource.empty(), scope);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan != null, "Complete empty forge did not plan");
        var before = entity.saveWithoutMetadata();
        for (var target : plan.targets()) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.customInserter().apply(target.stacks().get(0), Actionable.SIMULATE) == 1,
                    "Simulation refused input");
        }
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(before.equals(entity.saveWithoutMetadata()), "Simulation mutated forge");
        var firstTarget = plan.targets().get(0);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(firstTarget.customInserter().apply(firstTarget.stacks().get(0), Actionable.MODULATE) == 1,
                "Cannot set up partial rollback");
        var firstPedestal = level.getBlockEntity(firstTarget.pos());
        invoke(firstPedestal, "setStackAndSync", new Class<?>[]{ItemStack.class, boolean.class},
                new ItemStack(Items.DIAMOND), false);
        SlotHarvestOwnership.store(level, pos, scope, "forbidden_arcanus_hephaestus_forge:harvest",
                new ItemStack(result), new ResourceLocation("forbidden_arcanus", "ferrognetic_mixture"));
        var unrecovered = new java.util.ArrayList<GenericStack>();
        plan.onPartialCommit().recover(List.of(new AcceptedInsertion(firstTarget, firstTarget.stacks().get(0))),
                unrecovered::add);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(unrecovered.isEmpty(), "Changed pedestal must not be refunded");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(SlotHarvestOwnership.load(level, pos, scope, "forbidden_arcanus_hephaestus_forge:harvest") != null,
                "Incomplete rollback erased pending ownership");
        invoke(firstPedestal, "setStackAndSync", new Class<?>[]{ItemStack.class, boolean.class}, ItemStack.EMPTY, false);
        SlotHarvestOwnership.clear(level, pos, scope, "forbidden_arcanus_hephaestus_forge:harvest");
        invoke(essences, "setAureal", new Class<?>[]{int.class}, 0);
        var accepted = new java.util.ArrayList<AcceptedInsertion>();
        for (var target : plan.targets()) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.customInserter().apply(target.stacks().get(0), Actionable.MODULATE) == 1, "Input rejected");
            accepted.add(new AcceptedInsertion(target, target.stacks().get(0)));
        }
        boolean rejected = false;
        try { plan.onCommit().run(); }
        catch (IllegalStateException expected) { rejected = true; }
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(rejected, "Empty essences must reject ritual");
        var recovered = new java.util.ArrayList<GenericStack>();
        plan.onPartialCommit().recover(accepted, recovered::add);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(recovered.size() == accepted.size(), "Not all inputs rolled back");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.canDispatch(level, pos, binding.handle(), scope), "Rollback did not restore idle forge");
        invoke(essences, "setAureal", new Class<?>[]{int.class}, 100);
        invoke(essences, "setBlood", new Class<?>[]{int.class}, 1250);
        invoke(essences, "setSouls", new Class<?>[]{int.class}, 2);
        for (var target : plan.targets()) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.customInserter().apply(target.stacks().get(0), Actionable.MODULATE) == 1, "Retry input rejected");
        }
        plan.onCommit().run();
        var restoredScope = new MemoryScope(scope.states);
        var restoredAdapter = new ForbiddenArcanusHephaestusForgeAdapter();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(((Integer) invoke(essences, "getAureal", new Class<?>[0])) == 0, "Essences not consumed");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!restoredAdapter.canDispatch(level, pos, binding.handle(), restoredScope), "Active ritual accepts second job");
        var filter = new AllowedOutputFilter();
        filter.allowStrict(AEItemKey.of(result));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(restoredAdapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope).isEmpty(),
                "Restored ownership harvested active ritual");
        helper.runAfterDelay(510, () -> {
            var outputs = restoredAdapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(outputs.size() == 1 && outputs.get(0).what().equals(AEItemKey.of(result))
                    && outputs.get(0).amount() == 1, "Completed ritual output missing");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(restoredAdapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope).isEmpty(), "Output harvested twice");
            invoke(entity, "setStack", new Class<?>[]{int.class, ItemStack.class}, 4, new ItemStack(result));
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(restoredAdapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope).isEmpty(), "Manual output stolen");
            helper.succeed();
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 700)
    public static void upgradeRitualLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var relative = new BlockPos(7, 2, 7);
        var pos = helper.absolutePos(relative);
        placeForge(helper, relative);
        var adapter = new ForbiddenArcanusHephaestusForgeAdapter();
        var receipt = HephaestusUpgradeReceiptItem.forTier(PPItems.HEPHAESTUS_UPGRADE_RECEIPT.get(), 2);
        var planks = ForgeRegistries.ITEMS.getValue(new ResourceLocation("forbidden_arcanus", "edelwood_planks"));
        var crystal = ForgeRegistries.ITEMS.getValue(new ResourceLocation("forbidden_arcanus", "arcane_crystal"));
        var scrap = ForgeRegistries.ITEMS.getValue(new ResourceLocation("forbidden_arcanus", "spawner_scrap"));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(planks != null && crystal != null && scrap != null, "Upgrade ritual items missing");
        GenericStack[] supplied = {
                new GenericStack(AEItemKey.of(planks), 1),
                new GenericStack(AEItemKey.of(crystal), 4),
                new GenericStack(AEItemKey.of(scrap), 4)
        };
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(supplied,
                new GenericStack[]{new GenericStack(AEItemKey.of(receipt), 1)}), level);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(pattern != null, "Upgrade pattern not decoded");
        var binding = adapter.bind(level, pos, pattern);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(binding != null, "Upgrade pattern did not bind");
        var scope = new MemoryScope();
        var filter = new AllowedOutputFilter();
        filter.allowStrict(AEItemKey.of(receipt));
        SlotHarvestOwnership.store(level, pos, scope, "forbidden_arcanus_hephaestus_forge:harvest", receipt,
                new ResourceLocation("forbidden_arcanus", "upgrade_tier_2"));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.extractOutputs(level, pos, filter, IActionSource.empty(), scope).isEmpty(),
                "Unfinished upgrade generated a receipt");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.canDispatch(level, pos, binding.handle(), scope), "Failed upgrade claim blocked dispatch");
        var entity = level.getBlockEntity(pos);
        var essences = invoke(entity, "getEssenceManager", new Class<?>[0]);
        invoke(essences, "setAureal", new Class<?>[]{int.class}, 500);
        invoke(essences, "setBlood", new Class<?>[]{int.class}, 6000);
        invoke(essences, "setSouls", new Class<?>[]{int.class}, 10);
        var counter = new KeyCounter();
        for (var input : supplied) counter.add(input.what(), input.amount());
        var plan = adapter.planWithBinding(level, pos, pattern, new KeyCounter[]{counter}, binding.handle(),
                IActionSource.empty(), scope);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan != null, "Upgrade ritual not planned");
        for (var target : plan.targets()) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.customInserter().apply(target.stacks().get(0), Actionable.MODULATE) == 1,
                    "Upgrade input rejected");
        }
        plan.onCommit().run();
        var tierProperty = (IntegerProperty) level.getBlockState(pos).getBlock().getStateDefinition().getProperty("tier");
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(tierProperty, 2));
        var manager = invoke(entity, "getRitualManager", new Class<?>[0]);
        var originalRitual = invoke(manager, "getActiveRitual", new Class<?>[0]);
        invoke(manager, "failRitual", new Class<?>[0]);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.extractOutputs(level, pos, filter, IActionSource.empty(), scope).isEmpty(),
                "External tier change issued a receipt after ritual failure");
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(tierProperty, 1));
        var storage = invoke(essences, "getStorage", new Class<?>[0]);
        invoke(manager, "startRitual", new Class<?>[]{storage.getClass(), originalRitual.getClass()}, storage, originalRitual);
        invoke(manager, "finishRitual", new Class<?>[0]);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.extractOutputs(level, pos, filter, IActionSource.empty(), scope).isEmpty(),
                "Unrelated manually completed ritual received a stale upgrade receipt");
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(tierProperty, 1));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.canDispatch(level, pos, binding.handle(), scope),
                "Failed ritual claim blocked retry");
        invoke(essences, "setAureal", new Class<?>[]{int.class}, 500);
        invoke(essences, "setBlood", new Class<?>[]{int.class}, 6000);
        invoke(essences, "setSouls", new Class<?>[]{int.class}, 10);
        var retry = adapter.planWithBinding(level, pos, pattern, new KeyCounter[]{counter}, binding.handle(),
                IActionSource.empty(), scope);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(retry != null, "Failed upgrade did not plan retry");
        for (var target : retry.targets()) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.customInserter().apply(target.stacks().get(0), Actionable.MODULATE) == 1,
                    "Upgrade retry input rejected");
        }
        retry.onCommit().run();
        var restoredScope = new MemoryScope(scope.states);
        var restoredAdapter = new ForbiddenArcanusHephaestusForgeAdapter();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(restoredAdapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope).isEmpty(),
                "Active upgrade issued a receipt");
        helper.runAfterDelay(510, () -> {
            var tier = (IntegerProperty) level.getBlockState(pos).getBlock().getStateDefinition().getProperty("tier");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(level.getBlockState(pos).getValue(tier) == 2, "Forge tier did not upgrade");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(entity.getPersistentData().contains("ae2ltpp_hephaestus_completed_nonce"),
                    "Completion hook did not record the successful ritual");
            var saved = entity.saveWithoutMetadata();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(saved.getCompound("ForgeData").contains("ae2ltpp_hephaestus_completed_nonce"),
                    "Forge data did not save completion proof: " + saved.getAllKeys());
            entity.getPersistentData().remove("ae2ltpp_hephaestus_completed_nonce");
            entity.load(saved);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(entity.getPersistentData().contains("ae2ltpp_hephaestus_completed_nonce"),
                    "Forge data did not restore completion proof");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!restoredAdapter.canDispatch(level, pos, binding.handle(), restoredScope),
                    "Persisted upgrade proof was discarded");
            var secondProvider = new MemoryScope();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!restoredAdapter.canDispatch(level, pos, binding.handle(), secondProvider),
                    "Another provider overwrote an unclaimed upgrade proof");
            var denied = new AllowedOutputFilter();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(restoredAdapter.extractOutputs(level, pos, denied, IActionSource.empty(), restoredScope).isEmpty(),
                    "Receipt escaped output filter");
            var outputs = restoredAdapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(outputs.size() == 1 && outputs.get(0).what().equals(AEItemKey.of(receipt))
                    && outputs.get(0).amount() == 1, "Completed upgrade receipt missing");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(restoredAdapter.extractOutputs(level, pos, filter, IActionSource.empty(), restoredScope).isEmpty(),
                    "Upgrade receipt issued twice");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(restoredAdapter.canDispatch(level, pos, binding.handle(), restoredScope),
                    "Claimed upgrade blocked other jobs");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(restoredAdapter.canDispatch(level, pos, binding.handle(), secondProvider),
                    "Claimed upgrade blocked another provider");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(restoredAdapter.planWithBinding(level, pos, pattern, new KeyCounter[]{counter},
                    binding.handle(), IActionSource.empty(), restoredScope) == null, "Old tier ritual planned again");
            helper.succeed();
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 100)
    public static void removedProviderReleasesUpgradeProof(GameTestHelper helper) {
        var level = helper.getLevel();
        var relative = new BlockPos(7, 2, 7);
        var pos = helper.absolutePos(relative);
        placeForge(helper, relative);
        var tier = (IntegerProperty) level.getBlockState(pos).getBlock().getStateDefinition().getProperty("tier");
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(tier, 2));
        var ownerPos = helper.absolutePos(relative.above(2));
        helper.setBlock(relative.above(2), PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        var owner = (PackagedPatternProviderBlockEntity) level.getBlockEntity(ownerPos);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(owner != null, "Missing proof owner");
        var receipt = HephaestusUpgradeReceiptItem.forTier(PPItems.HEPHAESTUS_UPGRADE_RECEIPT.get(), 2);
        var nonce = "removed-provider-test";
        var proofKey = "forbidden_arcanus_hephaestus_forge:harvest:nonce@" + level.dimension().location();
        owner.setState(pos, proofKey, nonce);
        SlotHarvestOwnership.store(level, pos, owner, "forbidden_arcanus_hephaestus_forge:harvest", receipt,
                new ResourceLocation("forbidden_arcanus", "upgrade_tier_2"));
        var data = level.getBlockEntity(pos).getPersistentData();
        data.putString("ae2ltpp_hephaestus_completed_nonce", nonce);
        data.putString("ae2ltpp_hephaestus_owner_dimension", level.dimension().location().toString());
        data.putLong("ae2ltpp_hephaestus_owner_position", ownerPos.asLong());
        var adapter = new ForbiddenArcanusHephaestusForgeAdapter();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!adapter.canDispatch(level, pos, List.of(), new MemoryScope()),
                "Another provider overwrote a live upgrade claim");
        var forge = level.getBlockEntity(pos);
        var saved = forge.saveWithoutMetadata();
        forge.getPersistentData().remove("ae2ltpp_hephaestus_owner_dimension");
        forge.load(saved);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!adapter.canDispatch(level, pos, List.of(), new MemoryScope()),
                "Reloading the forge lost the live proof owner");
        owner.clearFlagsForTarget(pos);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.canDispatch(level, pos, List.of(), new MemoryScope()),
                "Removed provider left the forge permanently blocked");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!forge.getPersistentData().contains("ae2ltpp_hephaestus_completed_nonce"),
                "Orphan proof was not cleared");
        helper.succeed();
    }

    private static void placeForge(GameTestHelper helper, BlockPos relative) {
        String[] floor = {"***PPP***", "*PPPAPPP*", "*PAPPPAP*", "PPPPCPPPP", "PAPCACPAP",
                "PPPPCPPPP", "*PAPPPAP*", "*PPPAPPP*", "***PPP***"};
        for (int row = 0; row < floor.length; row++) for (int column = 0; column < floor[row].length(); column++) {
            String block = switch (floor[row].charAt(column)) {
                case 'P' -> "polished_darkstone";
                case 'A' -> "arcane_chiseled_polished_darkstone";
                case 'C' -> "chiseled_arcane_polished_darkstone";
                default -> null;
            };
            if (block != null) place(helper, relative.offset(column - 4, -1, row - 4), block);
        }
        place(helper, relative, "hephaestus_forge");
        int[][] offsets = {{-3, 0}, {-2, -2}, {0, -3}, {2, -2}, {3, 0}, {2, 2}, {0, 3}, {-2, 2}};
        for (var offset : offsets) place(helper, relative.offset(offset[0], 0, offset[1]), "darkstone_pedestal");
    }

    private static void place(GameTestHelper helper, BlockPos pos, String path) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("forbidden_arcanus", path));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(block != null, "Missing block " + path);
        helper.setBlock(pos, block.defaultBlockState());
    }

    private static Object invoke(Object target, String name, Class<?>[] signature, Object... args) {
        return ReflectionSupport.invokeMutation(ReflectionSupport.findMethodCached(target.getClass(), name, signature)
                .orElseThrow(), target, args).orElse(null);
    }

    private static final class MemoryScope implements AdapterPersistentScope {
        private final Map<String, String> states = new HashMap<>();
        private MemoryScope() { }
        private MemoryScope(Map<String, String> saved) { states.putAll(saved); }
        @Override public void setFlag(BlockPos pos, String key) { states.put(key, "true"); }
        @Override public boolean hasFlag(BlockPos pos, String key) { return states.containsKey(key); }
        @Override public void clearFlag(BlockPos pos, String key) { states.remove(key); }
        @Override public void setState(BlockPos pos, String key, String value) { states.put(key, value); }
        @Override public String getState(BlockPos pos, String key) { return states.get(key); }
        @Override public void clearState(BlockPos pos, String key) { states.remove(key); }
    }
}
