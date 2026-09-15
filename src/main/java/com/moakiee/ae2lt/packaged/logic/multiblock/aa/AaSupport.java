package com.moakiee.ae2lt.packaged.logic.multiblock.aa;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fml.ModList;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;

import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterRecipeTypes;

/** Helpers shared by the Actually Additions adapters. */
final class AaSupport {

    private static final String MOD_ID = "actuallyadditions";

    private AaSupport() {
    }

    static boolean isActuallyAdditionsLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    static ResourceLocation aaId(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.length == 1 && outputs[0].what() instanceof AEItemKey;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<Recipe<?>> recipes(ServerLevel level, ResourceLocation typeId) {
        var type = AdapterRecipeTypes.find(typeId);
        if (type == null) {
            return List.of();
        }
        return (List<Recipe<?>>) (List<?>) level.getRecipeManager()
                .getAllRecipesFor((RecipeType) type);
    }
}
