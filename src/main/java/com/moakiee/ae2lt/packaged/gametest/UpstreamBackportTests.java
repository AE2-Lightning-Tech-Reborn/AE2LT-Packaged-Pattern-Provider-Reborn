package com.moakiee.ae2lt.packaged.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.ars.ArsNouveauImbuementChamberAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.de.DraconicFusionCraftingAdapter;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Native Forge fixtures exercise the APIs that differ from NeoForge main. */
@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class UpstreamBackportTests {
    private UpstreamBackportTests() {}

    private static void place(GameTestHelper h, BlockPos pos, String id) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(block != null, "Missing native test block: " + id);
        h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
    }
    private static GenericStack stack(String id, long count) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null) throw new IllegalStateException("Missing test item: " + id);
        return new GenericStack(AEItemKey.of(item.getDefaultInstance()), count);
    }
    private static IPatternDetails pattern(GameTestHelper h, List<GenericStack> inputs, List<GenericStack> outputs) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                inputs.toArray(GenericStack[]::new), outputs.toArray(GenericStack[]::new)), h.getLevel());
    }
    private static KeyCounter[] counters(List<GenericStack> stacks) {
        var result = new KeyCounter[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            result[i] = new KeyCounter();
            result[i].add(stacks.get(i).what(), stacks.get(i).amount());
        }
        return result;
    }

    private static List<BlockPos> injectorPositions(BlockPos core) {
        var result = new ArrayList<BlockPos>();
        // DE scans three intersecting slabs, each only one block wide off-axis.
        for (var side : List.of(Direction.WEST, Direction.EAST)) {
            for (int y : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
                result.add(core.relative(side, 3).offset(0, y, z));
            }
        }
        return result;
    }

    private static void placeInjectors(GameTestHelper h, BlockPos core) {
        for (var pos : injectorPositions(core)) {
            place(h, pos, "draconicevolution:basic_crafting_injector");
            var facing = pos.getX() < core.getX() ? Direction.EAST : Direction.WEST;
            h.getLevel().setBlockAndUpdate(pos, h.getLevel().getBlockState(pos)
                    .setValue(BlockStateProperties.FACING, facing));
        }
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 200)
    public static void fusionDiscoversFreshAndRebuiltInjectors(GameTestHelper h) throws ReflectiveOperationException {
        var level = h.getLevel();
        // Pick a real fusion recipe whose basic injectors and unit-sized inputs fit the fixture.
        var recipe = level.getRecipeManager().getRecipes().stream().filter(candidate -> {
            if (!candidate.getType().toString().contains("fusion")) return false;
            try {
                var tier = candidate.getClass().getMethod("getRecipeTier").invoke(candidate);
                var catalyst = (Ingredient) candidate.getClass().getMethod("getCatalyst").invoke(candidate);
                return tier.toString().equals("DRACONIUM") && candidate.getIngredients().size() <= 8
                        && catalyst.getItems().length > 0 && catalyst.getItems()[0].getCount() == 1;
            } catch (ReflectiveOperationException ignored) { return false; }
        }).findFirst().orElseThrow();
        var inputs = new LinkedHashMap<AEItemKey, Long>();
        var catalyst = (Ingredient) recipe.getClass().getMethod("getCatalyst").invoke(recipe);
        inputs.merge(AEItemKey.of(catalyst.getItems()[0]), 1L, Long::sum);
        for (var ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) inputs.merge(AEItemKey.of(ingredient.getItems()[0]), 1L, Long::sum);
        }
        var declared = inputs.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
        var output = recipe.getResultItem();
        var pattern = pattern(h, declared, List.of(new GenericStack(AEItemKey.of(output), output.getCount())));
        var core = h.absolutePos(new BlockPos(7, 2, 7));
        place(h, core, "draconicevolution:crafting_core");
        var adapter = new DraconicFusionCraftingAdapter();
        var binding = adapter.bind(level, core, pattern);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(binding != null, "Native fusion recipe did not bind");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!adapter.canDispatch(level, core, binding.handle()), "Missing injectors must reject");
        placeInjectors(h, core);
        h.runAfterDelay(5, () -> {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.canDispatch(level, core, binding.handle()), "Fresh injector positions were not refreshed");
            injectorPositions(core).forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!adapter.canDispatch(level, core, binding.handle()), "Removed injectors stayed cached");
            placeInjectors(h, core);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.canDispatch(level, core, binding.handle()), "Rebuilt injectors were not refreshed");
            h.succeed();
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 800)
    public static void imbuementReturnsReusableCatalystsFromNativeTicker(GameTestHelper h) throws ReflectiveOperationException {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(7, 2, 7));
        place(h, pos, "ars_nouveau:imbuement_chamber");
        for (var offset : List.of(new BlockPos(-1, 0, -1), new BlockPos(1, 0, -1), new BlockPos(0, 0, 1))) {
            place(h, pos.offset(offset), "ars_nouveau:arcane_pedestal");
        }
        place(h, pos.above(2), "ars_nouveau:source_jar");
        var jar = level.getBlockEntity(pos.above(2));
        jar.getClass().getMethod("setSource", int.class).invoke(jar, 10000);
        var catalysts = List.of(stack("minecraft:flint_and_steel", 1), stack("minecraft:torch", 1), stack("minecraft:gunpowder", 1));
        var inputs = new ArrayList<>(catalysts);
        inputs.add(stack("ars_nouveau:source_gem", 1));
        var outputs = new ArrayList<GenericStack>();
        outputs.add(stack("ars_nouveau:fire_essence", 1));
        outputs.addAll(catalysts);
        var pattern = pattern(h, inputs, outputs);
        var adapter = new ArsNouveauImbuementChamberAdapter();
        var binding = adapter.bind(level, pos, pattern);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(binding != null, "Product plus catalyst pattern failed to bind");
        var plan = adapter.planWithBinding(level, pos, pattern, counters(inputs), binding.handle(), IActionSource.empty());
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan != null, "Native imbuement did not produce a dispatch plan");
        var returnInv = new appeng.helpers.iface.PatternProviderReturnInventory(() -> {});
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(DispatchExecutor.execute(plan, IActionSource.empty(), returnInv).success(), "Native catalyst dispatch failed");
        var filter = new AllowedOutputFilter();
        outputs.forEach(output -> filter.allowStrict(output.what()));
        var collected = new ArrayList<GenericStack>();
        h.succeedWhen(() -> {
            var harvested = adapter.extractOutputs(level, pos, filter, IActionSource.empty());
            collected.addAll(harvested);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(collected.size() == outputs.size() && collected.containsAll(outputs),
                    "Native recipe/catalyst results are incomplete: " + collected + " expected " + outputs);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.extractOutputs(level, pos, filter, IActionSource.empty()).isEmpty(), "Catalysts were returned twice");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2ltpp_core", template = "adapterconnectiontests.adapter_conn", timeoutTicks = 150)
    public static void workbenchBatchInterfaceIsExposedToMe(GameTestHelper h) {
        var relative = new BlockPos(4, 2, 4);
        h.setBlock(relative, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        h.setBlock(relative.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(relative));
        h.runAfterDelay(40, () -> {
            var node = provider.getMainNode().getNode();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(node != null && node.isActive(), "Batch test grid is inactive");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(node.getService(appeng.api.networking.crafting.ICraftingProvider.class) instanceof IBatchCraftingProvider,
                    "ME CPU cannot discover packaged workbench batch support");
            h.succeed();
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 200)
    public static void avaritiaAccepts130CopiesWithoutChangingBorrowedInputs(GameTestHelper h) throws ReflectiveOperationException {
        runAvaritiaBatch(h, 130, 1);
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 200)
    public static void avaritiaMillionCopyBatchesPreserveAccumulatedOutputs(GameTestHelper h) throws ReflectiveOperationException {
        runAvaritiaBatch(h, 1_000_000, 128);
    }

    private static void runAvaritiaBatch(GameTestHelper h, long copies, int rounds) throws ReflectiveOperationException {
        var level = h.getLevel();
        var recipe = level.getRecipeManager().byKey(new ResourceLocation("avaritia", "endest_pearl")).orElseThrow();
        var amounts = new LinkedHashMap<AEItemKey, Long>();
        for (var ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) amounts.merge(AEItemKey.of(ingredient.getItems()[0]), 1L, Long::sum);
        }
        var inputs = amounts.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
        var result = recipe.getResultItem();
        var table = h.absolutePos(new BlockPos(5, 2, 5));
        place(h, table, "avaritia:extreme_crafting_table");
        var matrix = (Container) level.getBlockEntity(table).getClass().getField("matrix").get(level.getBlockEntity(table));
        var providerPos = table.south();
        level.setBlockAndUpdate(providerPos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(providerPos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(providerPos);
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.AVARITIA_EXTREME_TABLE_ADAPTER.get()));
        provider.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                inputs.toArray(GenericStack[]::new), new GenericStack[]{new GenericStack(AEItemKey.of(result), result.getCount())}));
        provider.getLogic().updatePatterns();
        h.runAfterDelay(40, () -> {
            var logic = (com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic) provider.getLogic();
            var pattern = logic.getAvailablePatterns().get(0);
            var template = counters(inputs);
            matrix.setItem(0, new ItemStack(Items.DIRT));
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(logic.pushBatch(pattern, template, copies) == copies, "Occupied native table accepted batch inputs");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(matrix.getItem(0).is(Items.DIRT), "Rejected batch changed manual items");
            matrix.clearContent();
            for (int round = 0; round < rounds; round++) {
                com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(logic.pushBatch(logic.getAvailablePatterns().get(0), template, copies) == 0,
                        "Native workbench rejected batch at round " + round);
                for (int i = 0; i < template.length; i++) {
                    com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(template[i].size() == 1
                                    && template[i].get(inputs.get(i).what()) == inputs.get(i).amount(),
                            "Borrowed template was mutated at round " + round);
                }
                provider.load(provider.saveWithFullMetadata());
                logic.updatePatterns();
            }
            var outputs = logic.drainVirtualBatch();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(outputs.equals(List.of(new GenericStack(AEItemKey.of(result), copies * rounds * result.getCount()))),
                    "Batch quantities changed across reload");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(logic.drainVirtualBatch().isEmpty(), "Reload duplicated batch outputs");
            org.slf4j.LoggerFactory.getLogger("ae2ltpp-stress").info(
                    "STRESS PASS native Avaritia copiesPerBatch={} rounds={} reloads={} totalCrafts={}",
                    copies, rounds, rounds, copies * rounds);
            h.succeed();
        });
    }
    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 30)
    public static void balancedCoreRecipesLoadThroughNativeForgeSerializers(GameTestHelper h) throws ReflectiveOperationException {
        for (String name : List.of(
                "aa_empowerer_packaged_core",
                "aa_reconstructor_packaged_core",
                "ars_apparatus_packaged_core",
                "avaritia_end_table_packaged_core",
                "avaritia_extreme_smithing_packaged_core",
                "avaritia_extreme_table_packaged_core",
                "avaritia_nether_table_packaged_core",
                "avaritia_sculk_table_packaged_core",
                "botania_mana_pool_packaged_core",
                "botania_terra_plate_packaged_core",
                "de_fusion_packaged_core",
                "ec_advanced_table_packaged_core",
                "ec_basic_table_packaged_core",
                "ec_combination_packaged_core",
                "ec_elite_table_packaged_core",
                "ec_ender_crafter_packaged_core",
                "ec_flux_crafter_packaged_core",
                "ec_ultimate_table_packaged_core",
                "ma_infusion_altar_packaged_core",
                "occultism_ritual_packaged_core")) {
            var id = new ResourceLocation("ae2ltpp", "packaged_core/" + name);
            // Re-Avaritia-only recipes intentionally skip when AvaritiaNeo is installed.
            if (name.startsWith("avaritia_")) continue;
            var recipe = h.getLevel().getRecipeManager().byKey(id).orElseThrow(() ->
                    new AssertionError("Core recipe failed native deserialization: " + id));
            var result = name.equals("ars_apparatus_packaged_core")
                    ? (ItemStack) recipe.getClass().getField("result").get(recipe)
                    : recipe.getResultItem();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!result.isEmpty() && ForgeRegistries.ITEMS.getKey(result.getItem())
                    .equals(new ResourceLocation("ae2ltpp", name)), "Core recipe lost its result: " + id);
        }
        var recipe = h.getLevel().getRecipeManager().byKey(
                new ResourceLocation("ae2ltpp", "packaged_core/de_fusion_packaged_core")).orElseThrow();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(recipe.getIngredients().stream().allMatch(ingredient -> !ingredient.isEmpty()),
                "Fusion recipe has invalid native ingredients");
        h.succeed();
    }

}
