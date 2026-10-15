package com.moakiee.ae2lt.packaged.mixin;

import java.util.HashMap;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.jerry.mekmm.common.util.ValidatorUtils", remap = false)
public abstract class MekmmRecipeConfigMixin {
    @Inject(method = "getRecipeFromConfig(Ljava/util/List;)Ljava/util/HashMap;", at = @At("RETURN"),
            cancellable = true, remap = false)
    private static void ae2ltpp$keepEmptyReplicationConfigUsable(List<?> configuredRecipes,
                                                               CallbackInfoReturnable<HashMap<String, Integer>> callback) {
        if (callback.getReturnValue() == null) {
            callback.setReturnValue(new HashMap<>());
        }
    }
}
