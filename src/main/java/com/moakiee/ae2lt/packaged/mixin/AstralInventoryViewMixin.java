package com.moakiee.ae2lt.packaged.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Astral 2.0.0's inventory view treats a slot limit as the offered quantity.
 * Bound it by the real stack, including native crafting-remainder insertion.
 * Idempotent with an upstream fix; no Astral class is required when absent.
 */
@Pseudo
@Mixin(targets = "hellfirepvp.astralsorcery.common.util.inventory.InventoryView", remap = false)
public abstract class AstralInventoryViewMixin {
    @ModifyExpressionValue(method = "insertItem", at = @At(value = "INVOKE",
            target = "Ljava/util/function/BiFunction;apply(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), require = 0)
    private Object ae2ltpp$boundOfferedCount(Object limit, @Local(argsOnly = true) ItemStack offered) {
        return Math.min((Integer) limit, offered.getCount());
    }
}
