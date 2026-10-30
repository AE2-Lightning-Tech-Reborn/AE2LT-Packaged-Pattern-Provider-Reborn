package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.registry.*;
import com.moakiee.ae2lt.packaged.resource.ForbiddenEssenceResources;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;

/** Loads the real optional mods and executes their actual scripted recipe and native ticker. */
@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class FAJsGameTests {
    private static final ResourceLocation BEE = id("ae2ltpp:js_butcher_bee");
    private static final IActionSource SOURCE = IActionSource.empty();

    private static ResourceLocation id(String name) { return ResourceLocation.parse(name); }
    private static ItemStack item(String name) { return new ItemStack(BuiltInRegistries.ITEM.get(id(name))); }
    private static GenericStack stack(String name, long amount) { return new GenericStack(AEItemKey.of(item(name)), amount); }
    private static BlockEntity block(GameTestHelper h, BlockPos pos, String name) {
        h.getLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(id(name)).defaultBlockState());
        return h.getLevel().getBlockEntity(pos);
    }
    private static ItemStack egg(String type) {
        var egg = item("productivebees:spawn_egg_configurable_bee");
        var data = new CompoundTag();
        data.putString("id", "productivebees:configurable_bee");
        data.putString("type", "productivebees:" + type);
        egg.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        return egg;
    }
    private static List<GenericStack> physicalInputs() {
        return List.of(stack("minecraft:bee_spawn_egg", 1), stack("minecraft:water_bucket", 1),
                stack("forbidden_arcanus:dark_matter", 1), stack("minecraft:rotten_flesh", 2));
    }
    private static List<GenericStack> inputsWithEssences() {
        var inputs = new ArrayList<>(physicalInputs());
        inputs.add(new GenericStack(ForbiddenEssenceResources.AUREAL, 100));
        inputs.add(new GenericStack(ForbiddenEssenceResources.SOULS, 10));
        inputs.add(new GenericStack(ForbiddenEssenceResources.BLOOD, 1000));
        return inputs;
    }
    private static KeyCounter[] inputs(List<GenericStack> stacks) {
        var counter = new KeyCounter(); stacks.forEach(s -> counter.add(s.what(), s.amount()));
        return new KeyCounter[] {counter};
    }
    private static IPatternDetails pattern(GameTestHelper h, List<GenericStack> inputs, ItemStack output) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs,
                List.of(new GenericStack(AEItemKey.of(output), output.getCount()))), h.getLevel());
    }
    private static BlockEntity forge(GameTestHelper h) {
        for (var mod : List.of("forbidden_arcanus", "forbidden_arcanus_js", "kubejs", "productivebees"))
            h.assertTrue(ModList.get().isLoaded(mod), "required native test mod loaded: " + mod);
        var pos = h.absolutePos(new BlockPos(5, 3, 5));
        String[] rows = {"***PPP***", "*PPPAPPP*", "*PAPPPAP*", "PPPPCPPPP", "PAPCACPAP", "PPPPCPPPP", "*PAPPPAP*", "*PPPAPPP*", "***PPP***"};
        for (int z = 0; z < 9; z++) for (int x = 0; x < 9; x++) {
            String path = switch (rows[z].charAt(x)) {
                case 'P' -> "polished_darkstone"; case 'A' -> "gilded_chiseled_polished_darkstone";
                case 'C' -> "chiseled_arcane_polished_darkstone"; default -> null;
            };
            if (path != null) block(h, pos.offset(x - 4, -1, z - 4), "forbidden_arcanus:" + path);
        }
        var forge = block(h, pos, "forbidden_arcanus:hephaestus_forge_tier_1");
        for (var offset : List.of(new BlockPos(-3,0,0), new BlockPos(3,0,0), new BlockPos(0,0,3), new BlockPos(0,0,-3)))
            block(h, pos.offset(offset), "forbidden_arcanus:darkstone_pedestal");
        FAAccess.setStack(forge, 0, item("forbidden_arcanus:maledictus_pact"));
        FAAccess.setStack(forge, 1, item("forbidden_arcanus:crimson_stone"));
        return forge;
    }
    private static void assertEmpty(GameTestHelper h, BlockEntity forge) {
        h.assertTrue(FAAccess.stack(forge, 4).isEmpty() && !FAAccess.active(forge)
                && !forge.getPersistentData().contains(FAForgeAdapter.JOB), "rejection leaves forge idle");
        for (int i = 0; i < 4; i++) h.assertTrue(FAAccess.amount(forge, FAForgeConversion.name(i)) == 0, "no essence consumed " + i);
    }

    @GameTest(template="core_test", timeoutTicks=700)
    public static void scriptedBeeRitualDispatchesCompletesAndSurvivesReload(GameTestHelper h) {
        var forge = forge(h); var adapter = new FAForgeAdapter(); var list = inputsWithEssences();
        var pattern = pattern(h, list, egg("butcher"));
        h.runAfterDelay(100, () -> {
            h.assertTrue(FARituals.all(h.getLevel()).stream().anyMatch(r -> r.id().equals(BEE)), "scripted recipe discovered");
            h.assertTrue(FAAccess.registry(h.getLevel(), "hephaestus_forge/ritual").get(BEE) == null, "fixture exists only in RecipeManager");
            var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
            h.assertTrue(binding != null, "scripted bee pattern binds");
            var views = FARecipeViews.recipes(h.getLevel(), List.of());
            h.assertTrue(views.stream().anyMatch(v -> v.kind().equals("ritual") && v.outputs().equals(pattern.getOutputs())), "JEI exposes scripted bee with full components");
            var plan = adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE);
            h.assertTrue(plan != null, "scripted bee dispatch plans");
            h.assertTrue(DispatchExecutor.execute(plan, SOURCE, s -> h.fail("unexpected retention " + s)).success(), "dispatch commits");
            h.assertTrue(FAAccess.active(forge), "native ritual actually starts");
            var saved = forge.saveWithFullMetadata(h.getLevel().registryAccess());
            forge.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(FAAccess.active(forge), "managed ritual holder survives serialization");
            h.succeedWhen(() -> {
                var outputs = adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE);
                h.assertTrue(outputs.equals(pattern.getOutputs()), "native ticker produces exact bee type and entity data");
                h.assertTrue(adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE).isEmpty(), "output returned once");
                for (int i = 0; i < 4; i++) h.assertTrue(FAAccess.amount(forge, FAForgeConversion.name(i)) == 0, "exact essence charge " + i);
            });
        });
    }

    @GameTest(template="core_test", timeoutTicks=180)
    public static void wrongBeeComponentsAndMissingEnhancersRejectWithoutConsumption(GameTestHelper h) {
        var forge = forge(h); var adapter = new FAForgeAdapter(); var list = inputsWithEssences();
        h.runAfterDelay(100, () -> {
            for (var wrong : List.of(egg("diamond"), item("productivebees:spawn_egg_configurable_bee"), egg("butcher").copyWithCount(2)))
                h.assertTrue(adapter.bind(h.getLevel(), forge.getBlockPos(), pattern(h, list, wrong)) == null, "wrong bee, missing data or wrong count rejects");
            var pattern = pattern(h, list, egg("butcher"));
            var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
            h.assertTrue(binding != null, "valid output binds before machine gating");
            FAAccess.setStack(forge, 0, ItemStack.EMPTY);
            h.assertTrue(adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE) == null, "native enhancer requirement enforced");
            assertEmpty(h, forge); h.succeed();
        });
    }

    @GameTest(template="core_test", timeoutTicks=180)
    public static void removedManagedRecipeNeverFallsBackToNativeRegistry(GameTestHelper h) {
        var forge = forge(h); var adapter = new FAForgeAdapter();
        h.runAfterDelay(100, () -> {
            var registry = FAAccess.registry(h.getLevel(), "hephaestus_forge/ritual");
            var nativeRecipe = registry.get(id("forbidden_arcanus:eternal_stella"));
            h.assertTrue(nativeRecipe != null, "native recipe remains in registry");
            var list = new ArrayList<GenericStack>();
            list.add(new GenericStack(AEItemKey.of(((Ingredient) FAAccess.call(nativeRecipe, "mainIngredient")).getItems()[0]), 1));
            for (var input : (List<?>) FAAccess.call(nativeRecipe, "inputs"))
                list.add(new GenericStack(AEItemKey.of(((Ingredient) FAAccess.call(input, "ingredient")).getItems()[0]), (int) FAAccess.call(input, "amount")));
            var pattern = pattern(h, list, FAAccess.ritualResult(nativeRecipe, ((AEItemKey) list.getFirst().what()).toStack()));
            var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
            h.assertTrue(binding != null, "translated native recipe binds before removal");
            var entry = (FARituals.Entry) binding.handle();
            var manager = h.getLevel().getRecipeManager(); var original = List.copyOf(manager.getRecipes());
            try {
                manager.replaceRecipes(original.stream().filter(r -> !r.id().equals(entry.id())).toList());
                h.assertTrue(!FARituals.isCurrent(h.getLevel(), entry), "stale handle rejected after recipe removal");
                h.assertTrue(adapter.bind(h.getLevel(), forge.getBlockPos(), pattern) == null, "removed recipe is not resurrected from native registry");
                h.assertTrue(adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE) == null, "cached binding cannot dispatch removed recipe");
                h.assertTrue(FARecipeViews.recipes(h.getLevel(), List.of()).stream().noneMatch(v -> v.outputs().equals(pattern.getOutputs())), "JEI excludes removed recipe");
                assertEmpty(h, forge);
            } finally { manager.replaceRecipes(original); }
            h.succeed();
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecipeHolder<?> holder(ResourceLocation id, Object recipe) { return new RecipeHolder(id, (Recipe) recipe); }

    @GameTest(template="core_test", timeoutTicks=180)
    public static void replacedRecipeRequiresFreshBinding(GameTestHelper h) {
        var forge = forge(h); var adapter = new FAForgeAdapter(); var list = inputsWithEssences();
        var pattern = pattern(h, list, egg("butcher"));
        h.runAfterDelay(100, () -> {
            var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
            h.assertTrue(binding != null, "original scripted recipe binds");
            var entry = (FARituals.Entry) binding.handle(); var recipe = entry.recipe();
            var args = Arrays.stream(recipe.getClass().getRecordComponents()).map(c -> FAAccess.call(recipe, c.getName())).toArray();
            var replacement = FAAccess.construct(recipe.getClass().getName(), args);
            var manager = h.getLevel().getRecipeManager(); var original = List.copyOf(manager.getRecipes());
            try {
                manager.replaceRecipes(original.stream().<RecipeHolder<?>>map(r -> r.id().equals(BEE) ? holder(BEE, replacement) : r).toList());
                h.assertTrue(adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE) == null, "same id and equal data do not validate stale recipe instance");
                var fresh = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
                h.assertTrue(fresh != null && adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), fresh.handle(), SOURCE) != null, "fresh binding uses reloaded instance");
                assertEmpty(h, forge);
            } finally { manager.replaceRecipes(original); }
            h.succeed();
        });
    }

    @GameTest(template="core_test", timeoutTicks=1400)
    public static void realCpuConvertsMissingEssencesThenDeliversScriptedBee(GameTestHelper h) {
        var forge = forge(h); var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(12,3,5));
        level.setBlockAndUpdate(pos, PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(pos.north(), AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        provider.setReturnMode(ReturnMode.AUTO);
        provider.getAdapterInv().setItemDirect(0, PPItems.FA_FORGE_ADAPTER.toStack());
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        var output = new GenericStack(AEItemKey.of(egg("butcher")), 1);
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(inputsWithEssences(), List.of(output)));
        logic.getPatternInv().setItemDirect(1, PatternDetailsHelper.encodeProcessingPattern(
                List.of(stack("forbidden_arcanus:aureal_bottle", 1)), List.of(new GenericStack(ForbiddenEssenceResources.AUREAL, 35))));
        logic.getPatternInv().setItemDirect(2, PatternDetailsHelper.encodeProcessingPattern(
                List.of(stack("forbidden_arcanus:soul", 1)), List.of(new GenericStack(ForbiddenEssenceResources.SOULS, 1))));
        var stock = new KeyCounter();
        physicalInputs().forEach(s -> stock.add(s.what(), s.amount()));
        stock.add(stack("forbidden_arcanus:aureal_bottle", 3).what(), 3);
        stock.add(stack("forbidden_arcanus:soul", 10).what(), 10);
        stock.add(ForbiddenEssenceResources.BLOOD, 1000);
        var storage = new MEStorage() {
            public long extract(AEKey key, long n, Actionable mode, IActionSource source) {
                long taken = Math.min(n, stock.get(key)); if (mode == Actionable.MODULATE) stock.add(key, -taken); return taken;
            }
            public long insert(AEKey key, long n, Actionable mode, IActionSource source) {
                if (mode == Actionable.MODULATE) stock.add(key, n); return n;
            }
            public void getAvailableStacks(KeyCounter out) { out.addAll(stock); }
            public Component getDescription() { return Component.literal("FA JS CPU reserve"); }
        };
        h.runAfterDelay(100, () -> {
            var grid = provider.getMainNode().getGrid();
            grid.getStorageService().addGlobalStorageProvider(m -> m.mount(storage, 0));
            logic.updatePatterns();
            h.assertTrue(provider.addOrUpdateConnection(level.dimension(), forge.getBlockPos(), Direction.WEST), "wireless forge connected");
            var source = IActionSource.ofMachine(provider);
            var future = grid.getCraftingService().beginCraftingCalculation(level,
                    new appeng.api.networking.crafting.ICraftingSimulationRequester() {
                        public IActionSource getActionSource() { return source; }
                        public appeng.api.networking.IGridNode getGridNode() { return provider.getActionableNode(); }
                    }, output.what(), 1, appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            boolean[] submitted = {false};
            h.succeedWhen(() -> {
                if (!submitted[0]) {
                    h.assertTrue(future.isDone(), "craft calculation finished");
                    try {
                        var result = grid.getCraftingService().submitJob(future.get(), null, null, false, source);
                        h.assertTrue(result.successful(), "CPU accepts scripted bee order");
                    } catch (Exception e) { throw new AssertionError(e); }
                    submitted[0] = true;
                }
                h.assertTrue(stock.get(output.what()) == 1, "CPU receives exact scripted bee after prerequisite conversions");
                h.assertTrue(stock.get(ForbiddenEssenceResources.AUREAL) == 5, "100 of 105 converted aureal consumed");
                for (var key : List.of(ForbiddenEssenceResources.SOULS, ForbiddenEssenceResources.BLOOD))
                    h.assertTrue(stock.get(key) == 0, "exact essence cost " + key);
                for (var input : physicalInputs()) h.assertTrue(stock.get(input.what()) == 0, "physical input consumed once " + input);
                h.assertTrue(stock.get(stack("forbidden_arcanus:aureal_bottle", 1).what()) == 0
                        && stock.get(stack("forbidden_arcanus:soul", 1).what()) == 0, "all conversion inputs consumed");
                h.assertTrue(grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()), "order completes");
            });
        });
    }
}
