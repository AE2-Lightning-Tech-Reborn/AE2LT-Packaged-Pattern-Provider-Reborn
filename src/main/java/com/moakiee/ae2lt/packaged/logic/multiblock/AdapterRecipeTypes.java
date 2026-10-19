package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;

import net.minecraftforge.registries.ForgeRegistries;

/**
 * Resolves modded recipe types from Forge first, then the vanilla registry.
 * Forge 1.20.1 mods use both registration paths; checking only one misses recipes.
 */
public final class AdapterRecipeTypes {

    private static final Logger LOG = LoggerFactory.getLogger("ae2ltpp/recipe-types");
    private static final Set<ResourceLocation> MISSING_DIAGNOSTICS = ConcurrentHashMap.newKeySet();

    private AdapterRecipeTypes() {
    }

    @Nullable
    public static RecipeType<?> find(ResourceLocation id) {
        var forgeType = ForgeRegistries.RECIPE_TYPES.getValue(id);
        if (forgeType != null) {
            return forgeType;
        }
        var vanillaType = Registry.RECIPE_TYPE.getOptional(id).orElse(null);
        if (vanillaType == null && MISSING_DIAGNOSTICS.add(id)) {
            LOG.warn("Recipe type {} is not registered in Forge or vanilla registries", id);
        }
        return vanillaType;
    }
}
