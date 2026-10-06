package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ArsCatalystGameTests {
    @GameTest(template = "chisel_test", timeoutTicks = 200)
    public static void nativeAndThunderboltPlannersNeedOnlyOneCatalystSet(GameTestHelper h) throws Exception {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var diamond = AEItemKey.of(Items.DIAMOND);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(iron, 1), new GenericStack(diamond, 2)),
                List.of(new GenericStack(gold, 1), new GenericStack(diamond, 2))), h.getLevel());
        var service = proxy(appeng.api.networking.crafting.ICraftingService.class, (object, method, args) -> switch (method.getName()) {
            case "getCraftingFor" -> gold.equals(args[0]) || diamond.equals(args[0]) ? List.of(pattern) : List.of();
            case "getCraftables" -> java.util.Set.of(gold, diamond);
            case "getFuzzyCraftable" -> null;
            case "canEmitFor" -> false;
            default -> throw new UnsupportedOperationException(method.toString());
        });
        for (int amount : new int[]{1, 64, 1000}) {
            var stock = new KeyCounter();
            stock.add(iron, amount);
            stock.add(diamond, 2);
            var storage = proxy(appeng.api.networking.storage.IStorageService.class, (object, method, args) -> {
                if (method.getName().equals("getCachedInventory")) return stock;
                throw new UnsupportedOperationException(method.toString());
            });
            var grid = proxy(appeng.api.networking.IGrid.class, (object, method, args) -> switch (method.getName()) {
                case "getStorageService" -> storage;
                case "getCraftingService" -> service;
                default -> throw new UnsupportedOperationException(method.toString());
            });
            var node = proxy(appeng.api.networking.IGridNode.class, (object, method, args) -> {
                if (method.getName().equals("getGrid")) return grid;
                throw new UnsupportedOperationException(method.toString());
            });
            var requester = new appeng.api.networking.crafting.ICraftingSimulationRequester() {
                @Override public appeng.api.networking.security.IActionSource getActionSource() {
                    return appeng.api.networking.security.IActionSource.empty();
                }
                @Override public appeng.api.networking.IGridNode getGridNode() { return node; }
            };
            var job = new appeng.crafting.CraftingCalculation(h.getLevel(), grid, requester,
                    new GenericStack(gold, amount), appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            // Invoke AE2's tree directly so the installed Thunderbolt attempt hook cannot replace it.
            var jobType = appeng.crafting.CraftingCalculation.class;
            var watch = jobType.getDeclaredField("watch"); watch.setAccessible(true);
            ((com.google.common.base.Stopwatch) watch.get(job)).start();
            var time = jobType.getDeclaredField("time"); time.setAccessible(true); time.setInt(job, Integer.MAX_VALUE);
            var running = jobType.getDeclaredField("running"); running.setAccessible(true); running.setBoolean(job, true);
            var treeField = jobType.getDeclaredField("tree"); treeField.setAccessible(true);
            var tree = (appeng.crafting.CraftingTreeNode) treeField.get(job);
            var originalState = new appeng.crafting.inv.ChildCraftingSimulationState(
                    new appeng.crafting.inv.NetworkCraftingSimulationState(storage, requester.getActionSource()));
            originalState.ignore(gold);
            var request = appeng.crafting.CraftingTreeNode.class.getDeclaredMethod("request",
                    appeng.crafting.inv.CraftingSimulationState.class, long.class, KeyCounter.class);
            request.setAccessible(true);
            request.invoke(tree, originalState, (long) amount, null);
            var nativePlan = appeng.crafting.inv.CraftingSimulationState.buildCraftingPlan(originalState, job, amount);
            assertPlan(h, nativePlan, iron, diamond, amount, "AE2");
            var fastState = new appeng.crafting.inv.NetworkCraftingSimulationState(storage, requester.getActionSource());
            var attempt = com.moakiee.thunderbolt.core.crafting.planner.FastCraftingPlanner.tryAttempt(
                    service, fastState, h.getLevel(), gold, amount, false);
            h.assertTrue(attempt.handled() && attempt.plan() != null, "Thunderbolt returns a feasible plan");
            assertPlan(h, attempt.plan(), iron, diamond, amount, "Thunderbolt");
        }
        System.out.println("PP_ARS_PLANNERS_PASS planners=AE2+Thunderbolt requests=1,64,1000 catalysts=2");
        h.succeed();
    }

    private static void assertPlan(GameTestHelper h, appeng.api.networking.crafting.ICraftingPlan plan,
            AEItemKey iron, AEItemKey diamond, long amount, String planner) {
        h.assertTrue(!plan.simulation() && plan.missingItems().isEmpty(), planner + " has no missing materials");
        h.assertTrue(plan.usedItems().get(iron) == amount && plan.usedItems().get(diamond) == 2,
                planner + " consumes " + amount + " iron and reserves only 2 diamonds");
        h.assertTrue(plan.patternTimes().values().stream().mapToLong(Long::longValue).sum() == amount,
                planner + " schedules exactly the requested number of crafts");
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    @GameTest(template = "chisel_test", timeoutTicks = 200)
    public static void ordinaryProcessingPatternCyclesOneNamedCatalystSet64Times(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var main = h.absolutePos(new BlockPos(4, 2, 4));
        var left = main.west();
        var right = main.east();
        level.setBlockAndUpdate(main, BuiltInRegistries.BLOCK.get(id("ars_nouveau:imbuement_chamber")).defaultBlockState());
        level.setBlockAndUpdate(left, BuiltInRegistries.BLOCK.get(id("ars_nouveau:arcane_pedestal")).defaultBlockState());
        level.setBlockAndUpdate(right, BuiltInRegistries.BLOCK.get(id("ars_nouveau:arcane_platform")).defaultBlockState());
        var catalyst = new ItemStack(Items.DIAMOND);
        catalyst.set(DataComponents.CUSTOM_NAME, Component.literal("persistent catalyst"));
        var catalystKey = AEItemKey.of(catalyst);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var inputs = List.of(new GenericStack(iron, 1), new GenericStack(catalystKey, 2));
        var outputs = List.of(new GenericStack(gold, 1), new GenericStack(catalystKey, 2));
        var encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
        // Restore the real encoded item before binding, as a persisted crafting task would do.
        var restored = ItemStack.parse(level.registryAccess(), encoded.save(level.registryAccess())).orElseThrow();
        var pattern = PatternDetailsHelper.decodePattern(restored, level);
        h.assertTrue(pattern != null && pattern.getOutputs().equals(outputs), "encoded catalyst outputs survive item persistence");
        var adapter = new ArsNouveauImbuementChamberAdapter();
        var binding = adapter.bind(level, main, pattern);
        h.assertTrue(binding != null, "real Ars recipe binds an ordinary AE2 processing pattern with catalyst returns");
        var invalid = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs,
                List.of(new GenericStack(gold, 1), new GenericStack(catalystKey, 1))), level);
        h.assertTrue(adapter.bind(level, main, invalid) == null, "an incomplete catalyst return is rejected");
        var legacy = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs,
                List.of(new GenericStack(gold, 1))), level);
        h.assertTrue(adapter.bind(level, main, legacy) != null, "legacy product-only processing patterns still bind");
        var filter = new AllowedOutputFilter();
        filter.allowStrict(gold);
        var chamber = (Container) level.getBlockEntity(main);
        var tick = chamber.getClass().getMethod("tick");
        var stock = new KeyCounter();
        stock.add(iron, 64);
        stock.add(catalystKey, 2);
        for (int order = 0; order < 64; order++) {
            h.assertTrue(adapter.canDispatch(level, main, binding.handle()), "chamber and both pedestal types are ready");
            var provided = new KeyCounter[]{new KeyCounter()};
            for (var s : inputs) {
                h.assertTrue(stock.get(s.what()) >= s.amount(), "each dispatch uses only available physical stock");
                stock.remove(s.what(), s.amount());
                provided[0].add(s.what(), s.amount());
            }
            var plan = adapter.planWithBinding(level, main, pattern, provided, binding.handle(), null);
            h.assertTrue(plan != null && DispatchExecutor.execute(plan, null, retained -> {
                throw new IllegalStateException("unexpected retained inputs " + retained);
            }).success(), "real containers accept one complete recipe");
            h.assertTrue(adapter.extractOutputs(level, main, filter, null).isEmpty(), "unfinished reagent is never harvested");
            // Advance the real Ars machine without waiting 64 separate 100-tick cycles.
            for (int step = 0; step < 160 && !chamber.getItem(0).is(Items.GOLD_INGOT); step++) tick.invoke(chamber);
            h.assertTrue(chamber.getItem(0).is(Items.GOLD_INGOT), "Ars itself completes the imbuement");
            var returned = adapter.extractOutputs(level, main, filter, null);
            returned.forEach(s -> stock.add(s.what(), s.amount()));
            h.assertTrue(stock.get(catalystKey) == 2, "both original component-bearing catalysts return exactly once");
            h.assertTrue(chamber.isEmpty() && ((Container) level.getBlockEntity(left)).isEmpty()
                    && ((Container) level.getBlockEntity(right)).isEmpty(), "one cycle empties every machine slot");
            h.assertTrue(adapter.extractOutputs(level, main, filter, null).isEmpty(), "a second harvest cannot duplicate returns");
        }
        h.assertTrue(stock.get(gold) == 64 && stock.get(iron) == 0 && stock.get(catalystKey) == 2,
                "64 products consume 64 iron while conserving one two-item catalyst set");
        System.out.println("PP_ARS_CATALYST_PASS products=64 consumed=64 catalysts=2 components=preserved ordinaryPattern=persisted");
        h.succeed();
    }

    private static ResourceLocation id(String name) { return ResourceLocation.parse(name); }
}
