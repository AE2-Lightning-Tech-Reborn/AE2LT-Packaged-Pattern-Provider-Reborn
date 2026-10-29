package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Optional mixin bridge for an atomic, fully paid native glyph recipe. */
public interface ArsScribesMachine {
    boolean ae2ltpp$isScribeIdle();
    void ae2ltpp$beginScribing(RecipeHolder<?> recipe, List<ItemStack> inputs);
}
