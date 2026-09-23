package com.moakiee.ae2lt.packaged.probe;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.avaritia.AvaritiaTableAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingTableAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Runs against real optional-mod jars in a separate dev server; never shipped. */
@Mod("available_adapter_probe")
public final class AvailableAdapterProbe {
    private int ticks;

    public AvailableAdapterProbe() {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ServerTickEvent.Post event) {
        if (++ticks != 40) return;
        var server = event.getServer();
        try {
            var level = server.overworld();
            checkTable(level, "extendedcrafting:ultimate_table", true);
            checkTable(level, "avaritia:extreme_crafting_table", false);
            int ec = checkRecipes(level, "extendedcrafting:table");
            int av = checkRecipes(level, "avaritia:crafting_table_recipe");
            int ma = checkAwakeningRecipes(level);
            int occultism = checkRecipes(level, "occultism:ritual");
            checkPositionedEmptyCells(level);
            checkTableBinding(level, "extendedcrafting:basic_table", "extendedcrafting:table",
                    new ExtendedCraftingTableAdapter());
            checkTableBinding(level, "avaritia:extreme_crafting_table", "avaritia:crafting_table_recipe",
                    new AvaritiaTableAdapter());
            record("PASS ec=" + ec + " av=" + av + " ma=" + ma + " occultism=" + occultism);
        } catch (Throwable failure) {
            record("FAIL " + failure);
            failure.printStackTrace();
        } finally {
            server.halt(false);
        }
    }

    private static void checkTable(ServerLevel level, String id, boolean cucumber) {
        var pos = new BlockPos(5, 80, 5);
        var block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElseThrow();
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        var be = level.getBlockEntity(pos);
        require(be != null, "missing block entity " + id);
        if (cucumber) {
            require(new ExtendedCraftingTableAdapter().recognizesMain(level, pos, be), "EC table not recognized");
            var inventory = CucumberInventoryAccess.get(be);
            require(inventory instanceof IItemHandlerModifiable && inventory.getSlots() >= 81,
                    "EC inventory bridge not modifiable");
        } else {
            require(new AvaritiaTableAdapter().recognizesMain(level, pos, be), "Avaritia table not recognized");
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    private static int checkRecipes(ServerLevel level, String typeId) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.getOptional(Identifier.parse(typeId)).orElseThrow();
        var recipes = RecipeCompat26.byType(level, type);
        require(!recipes.isEmpty(), "no recipes of type " + typeId);
        int usable = 0;
        for (RecipeHolder<?> holder : recipes) {
            var result = RecipeCompat26.resultItem(holder.value(), level);
            var ingredients = RecipeCompat26.ingredients(holder.value());
            if (!result.isEmpty() && ingredients.stream().anyMatch(ingredient -> !ingredient.isEmpty())) usable++;
        }
        require(usable > 0, "no recipe previews or ingredients for " + typeId);
        return usable;
    }

    private static int checkAwakeningRecipes(ServerLevel level) throws Exception {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE
                .getOptional(Identifier.parse("mysticalagriculture:awakening")).orElseThrow();
        var access = Class.forName("com.moakiee.ae2lt.packaged.logic.multiblock.ma.AwakeningAltarAccess");
        var altarMethod = access.getDeclaredMethod("getAltarIngredient", Object.class);
        var pedestalMethod = access.getDeclaredMethod("getIngredients", Object.class);
        var essenceMethod = access.getDeclaredMethod("getEssences", Object.class);
        altarMethod.setAccessible(true);
        pedestalMethod.setAccessible(true);
        essenceMethod.setAccessible(true);
        int usable = 0;
        for (var holder : RecipeCompat26.byType(level, type)) {
            var recipe = holder.value();
            if (RecipeCompat26.resultItem(recipe, level).isEmpty()) continue;
            if (altarMethod.invoke(null, recipe) == null) continue;
            if (!(pedestalMethod.invoke(null, recipe) instanceof List<?> pedestals)
                    || pedestals.size() != 4) continue;
            if (!(essenceMethod.invoke(null, recipe) instanceof List<?> essences)
                    || essences.size() != 4) continue;
            usable++;
        }
        require(usable > 0, "MA awakening accessors produced no usable recipes");
        return usable;
    }

    private static void checkTableBinding(ServerLevel level, String blockId, String typeId,
                                          VirtualCraftingAdapter adapter) {
        var pos = new BlockPos(5, 80, 5);
        var block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(blockId)).orElseThrow();
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        try {
            RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.getOptional(Identifier.parse(typeId)).orElseThrow();
            int eligible = 0;
            int decoded = 0;
            int bound = 0;
            for (var holder : RecipeCompat26.byType(level, type)) {
                var recipe = holder.value();
                var output = RecipeCompat26.resultItem(recipe, level);
                if (output.isEmpty()) continue;
                var counts = new LinkedHashMap<AEItemKey, Long>();
                boolean supported = true;
                int slots = 0;
                for (var ingredient : RecipeCompat26.ingredients(recipe)) {
                    if (ingredient.isEmpty()) continue;
                    if (++slots > 9) { supported = false; break; }
                    var first = ingredient.items().findFirst();
                    if (first.isEmpty()) { supported = false; break; }
                    counts.merge(AEItemKey.of(new ItemStack(first.get().value())), 1L, Long::sum);
                }
                if (!supported || counts.isEmpty()) continue;
                eligible++;
                var input = counts.entrySet().stream()
                        .map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
                var encoded = PatternDetailsHelper.encodeProcessingPattern(input,
                        List.of(new GenericStack(AEItemKey.of(output), output.getCount())));
                var pattern = PatternDetailsHelper.decodePattern(encoded, level);
                if (pattern == null) continue;
                decoded++;
                var binding = adapter.bind(level, pos, pattern);
                if (binding == null) continue;
                bound++;
                var available = new KeyCounter();
                counts.forEach(available::add);
                var plan = adapter.planVirtualWithBinding(level, pos, pattern,
                        new KeyCounter[] {available}, binding.handle(), null);
                if (plan == null) continue;
                require(plan.outputs().contains(new GenericStack(AEItemKey.of(output), output.getCount())),
                        "wrong table product " + holder.id());
                System.out.println("AVAILABLE_ADAPTER_PROBE BIND " + typeId + " " + holder.id());
                return;
            }
            throw new AssertionError("no real table recipe bound and planned for " + typeId
                    + " eligible=" + eligible + " decoded=" + decoded + " bound=" + bound);
        } finally {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void checkPositionedEmptyCells(ServerLevel level) throws Exception {
        var emptySlotField = RecipeCompat26.class.getDeclaredField("EMPTY_SLOT");
        emptySlotField.setAccessible(true);
        require(((net.minecraft.world.item.crafting.Ingredient) emptySlotField.get(null)).isEmpty(),
                "26.1 vacant-cell marker is not empty");
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE
                .getOptional(Identifier.parse("extendedcrafting:table")).orElseThrow();
        for (var holder : RecipeCompat26.byType(level, type)) {
            var recipe = holder.value();
            if (!recipe.getClass().getSimpleName().equals("ShapedTableRecipe")) continue;
            var raw = recipe.getClass().getMethod("getPositionedIngredients").invoke(recipe);
            if (!(raw instanceof List<?> slots) || slots.stream().noneMatch(slot -> slot instanceof Optional<?> o && o.isEmpty())) continue;
            var converted = RecipeCompat26.ingredients(recipe);
            require(converted.size() == slots.size(), "shaped layout changed size " + holder.id());
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i) instanceof Optional<?> optional && optional.isEmpty()) {
                    require(converted.get(i).isEmpty(), "vacant cell became an input " + holder.id());
                }
            }
            return;
        }
        // Some EC data packs only provide solid patterns. The marker above is
        // still checked even when there is no shaped gap in the current pack.
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void record(String result) {
        System.out.println("AVAILABLE_ADAPTER_PROBE " + result);
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(
                    System.getProperty("availableAdapterProbeResult")), result);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
