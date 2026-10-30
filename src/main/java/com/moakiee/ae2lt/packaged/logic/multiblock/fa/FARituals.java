package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** Uses the same ritual source as the native forge, including FA JS's replacement source. */
final class FARituals {
    record Entry(ResourceLocation id, Object recipe) {}

    private FARituals() {}

    static List<Entry> all(Level level) {
        var out = new ArrayList<Entry>();
        if (managed()) {
            for (var holder : managedRecipes(level)) out.add(new Entry(holder.id(), holder.value()));
        } else {
            var registry = FAAccess.registry(level, "hephaestus_forge/ritual");
            for (var recipe : registry) out.add(new Entry(registry.getKey(recipe), recipe));
        }
        return List.copyOf(out);
    }

    static boolean isCurrent(Level level, Entry entry) {
        if (!managed()) return FAAccess.registry(level, "hephaestus_forge/ritual").get(entry.id()) == entry.recipe();
        return level.getRecipeManager().byKey(entry.id())
                .filter(holder -> holder.value().getType() == managedType())
                .map(holder -> holder.value() == entry.recipe()).orElse(false);
    }

    private static boolean managed() {
        return ModList.get().isLoaded("forbidden_arcanus_js");
    }

    private static RecipeType<?> managedType() {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(FAAccess.id("ritual"))
                .orElseThrow(() -> new IllegalStateException("FA JS ritual recipe type is missing"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> managedRecipes(Level level) {
        // FA JS mixes Recipe into Ritual. Keep that optional type out of our classpath.
        return (List) level.getRecipeManager().getAllRecipesFor((RecipeType) managedType());
    }
}
