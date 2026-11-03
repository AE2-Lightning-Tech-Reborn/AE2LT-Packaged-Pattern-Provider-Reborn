package com.moakiee.ae2lt.packaged.logic.multiblock.table;

import static com.moakiee.ae2lt.packaged.logic.multiblock.table.VirtualTableTestSupport.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.avaritia.AvaritiaExtremeSmithingAdapter;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class VirtualTableJsGameTests {
    @GameTest(template = "workbench_test")
    public static void realKubeJsRecipesUseTheNativeManagerAndNativeClasses(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded("kubejs"), "KubeJS must actually be installed");
        for (var family : Family.values()) {
            var f = table(h, family, 1);
            for (String shape : List.of("shape", "shapeless")) {
                var holder = h.getLevel().getRecipeManager().byKey(id("ae2ltpp:js_" + shape + "_" + family.mod)).orElseThrow();
                h.assertTrue(BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).equals(id(family.mod + ":" + family.type)),
                        "script recipe remains in native RecipeType");
                var in = shape.equals("shape") ? List.of(item(Items.IRON_INGOT, 2), item(Items.GOLD_INGOT, 1))
                        : List.of(item(Items.GOLD_INGOT, 1), item(Items.COPPER_INGOT, 1));
                var out = shape.equals("shape") ? item(Items.AMETHYST_SHARD, 2) : item(Items.PRISMARINE_SHARD, 1);
                var result = f.plan(pattern(h, in, List.of(out)), counters(in));
                h.assertTrue(result != null && result.outputs().equals(List.of(out)), "script-only " + shape + " recipe crafts");
                probe(h, "js-recipe-source", Map.of("id", holder.id().toString(), "class", holder.value().getClass().getName(),
                        "type", holder.value().getType().toString(), "serializer", BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer()).toString()));
            }
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void scriptRemovalAndReplacementHaveNoNativeFallback(GameTestHelper h) {
        for (var family : Family.values()) {
            h.assertTrue(h.getLevel().getRecipeManager().byKey(id("ae2ltpp:js_removed_" + family.mod)).isEmpty(), "script removal persisted in manager");
            var replacement = h.getLevel().getRecipeManager().byKey(id("ae2ltpp:js_replaced_" + family.mod)).orElseThrow();
            h.assertTrue(replacement.value().getIngredients().getFirst().test(new net.minecraft.world.item.ItemStack(Items.COBBLESTONE)), "same ID now has new recipe");
            var f = table(h, family, 1);
            var good = List.of(item(Items.COBBLESTONE, 1));
            h.assertTrue(f.plan(pattern(h, good, List.of(item(Items.FLINT, 1))), counters(good)) != null, "replacement input works");
            var removed = List.of(item(Items.DIRT, 1));
            h.assertTrue(f.plan(pattern(h, removed, List.of(item(Items.FLINT, 1))), counters(removed)) == null, "deleted JSON recipe never resurfaces");
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void legacySmithingSchemaRejectsCustomButRawDatapackRemainsUsable(GameTestHelper h) {
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("ae2ltpp:js_smithing")).isEmpty(),
                "pinned Re-Avaritia 1.3.9.9's broken item_stack schema rejects event.custom; this is an explicit unsupported-path probe");
        var holder = h.getLevel().getRecipeManager().byKey(id("ae2ltpp:js_smithing_raw")).orElseThrow();
        h.assertTrue(BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).equals(id("avaritia:extreme_smithing_recipe")), "script smithing recipe uses native type");
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        h.getLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(id("avaritia:extreme_smithing_table")).defaultBlockState());
        var in = List.of(item(Items.PAPER, 1), item(Items.WOODEN_SWORD, 1), item(Items.IRON_INGOT, 3));
        var p = pattern(h, in, List.of(item(Items.STONE_SWORD, 1)));
        var adapter = new AvaritiaExtremeSmithingAdapter();
        var binding = adapter.bind(h.getLevel(), pos, p);
        h.assertTrue(binding != null, "native JSON survives KubeJS parsing");
        var result = adapter.planVirtualWithBinding(h.getLevel(), pos, p, counters(in), binding.handle(), SOURCE);
        h.assertTrue(result != null && result.outputs().equals(p.getOutputs()), "actual script smithing recipe crafts");
        probe(h, "js-smithing", Map.of("class", holder.value().getClass().getName(), "custom", "rejected-by-upstream-schema", "rawDatapack", "accepted"));
        h.succeed();
    }

    private record Provider(Family family, boolean wireless, PackagedPatternProviderLogic logic) {}

    @GameTest(template = "workbench_test", batch = "zz_virtual_js_reload", timeoutTicks = 60000)
    public static void actualResourceReloadInvalidatesPositiveAndNegativeProviderBindings(GameTestHelper h) {
        var providers = new ArrayList<Provider>();
        int index = 0;
        for (var family : Family.values()) for (boolean wireless : new boolean[]{false, true}) {
            var pos = h.absolutePos(new BlockPos(2 + (index % 2) * 10, 2, 2 + (index / 2) * 10));
            index++;
            Block block = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get() : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
            h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
            h.getLevel().setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            var target = pos.east(wireless ? 3 : 1);
            h.getLevel().setBlockAndUpdate(target, BuiltInRegistries.BLOCK.get(family.block(1)).defaultBlockState());
            var provider = (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(pos);
            provider.getAdapterInv().setItemDirect(0, family == Family.AVARITIA ? PPItems.AVARITIA_EXTREME_TABLE_ADAPTER.toStack() : PPItems.EC_ULTIMATE_ADAPTER.toStack());
            if (wireless) h.assertTrue(provider.addOrUpdateConnection(h.getLevel().dimension(), target, Direction.WEST), "wireless connection installed");
            var logic = (PackagedPatternProviderLogic) provider.getLogic();
            for (int slot = 0; slot < 2; slot++) {
                logic.getPatternInv().setItemDirect(slot, PatternDetailsHelper.encodeProcessingPattern(List.of(item(Items.CLAY_BALL, 1)),
                        List.of(item(slot == 0 ? Items.BRICK : Items.NETHER_BRICK, 1))));
            }
            providers.add(new Provider(family, wireless, logic));
        }
        h.runAfterDelay(60, () -> {
            for (var provider : providers) {
                provider.logic.updatePatterns();
                h.assertTrue(provider.logic.pushPattern(output(provider.logic, Items.BRICK), counters(List.of(item(Items.CLAY_BALL, 1)))), "warm positive cache");
                h.assertTrue(!provider.logic.pushPattern(output(provider.logic, Items.NETHER_BRICK), counters(List.of(item(Items.CLAY_BALL, 1)))), "warm negative cache");
            }
            var script = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/virtual_tables.js");
            final String original;
            try {
                original = Files.readString(script);
                h.assertTrue(original.contains("const revision = 1;"), "reload fixture starts at revision 1");
                Files.writeString(script, original.replace("const revision = 1;", "const revision = 2;"));
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
            var server = h.getLevel().getServer();
            var finished = new java.util.concurrent.atomic.AtomicBoolean();
            var result = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            // Same reload path as /reload, without blocking the server thread or manually invalidating bindings.
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, failure) -> server.execute(() -> {
                try {
                    if (failure != null) throw new AssertionError("real resource reload failed", failure);
                    for (var provider : providers) {
                        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("ae2ltpp:js_reload_old_" + provider.family.mod)).isEmpty(), "script removed old recipe");
                        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("ae2ltpp:js_reload_new_" + provider.family.mod)).isPresent(), "script installed new recipe");
                        h.assertTrue(!provider.logic.pushPattern(output(provider.logic, Items.BRICK), counters(List.of(item(Items.CLAY_BALL, 1)))),
                                "removed recipe cannot craft through positive cache: " + provider.family + " wireless=" + provider.wireless);
                        h.assertTrue(provider.logic.pushPattern(output(provider.logic, Items.NETHER_BRICK), counters(List.of(item(Items.CLAY_BALL, 1)))),
                                "new recipe must bypass stale negative cache immediately: " + provider.family + " wireless=" + provider.wireless);
                        probe(h, "js-reload-complete", Map.of("family", provider.family.mod, "wireless", provider.wireless, "oldRejected", true, "newAccepted", true));
                    }
                } catch (Throwable verification) {
                    result.set(verification);
                } finally {
                    try { Files.writeString(script, original); }
                    catch (java.io.IOException restore) { result.set(restore); }
                    finished.set(true);
                }
            }));
            h.succeedWhen(() -> {
                h.assertTrue(finished.get(), "asynchronous reload completed");
                h.assertTrue(result.get() == null, "reload validation: " + result.get());
            });
        });
    }

    private static IPatternDetails output(PackagedPatternProviderLogic logic, net.minecraft.world.item.Item output) {
        return logic.getAvailablePatterns().stream().filter(p -> p.getOutputs().getFirst().what().equals(AEItemKey.of(output))).findFirst().orElseThrow();
    }
}
