package com.moakiee.ae2lt.packaged.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.moakiee.ae2lt.packaged.logic.multiblock.compat.ForbiddenArcanusHephaestusForgeAdapter;

@Pseudo
@Mixin(targets = "com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualManager", remap = false)
public abstract class HephaestusRitualManagerMixin {
    @Shadow private ServerLevel level;
    @Shadow private BlockPos pos;
    @Unique private ResourceLocation ae2ltpp$finishingRitual;

    @Inject(method = "finishRitual", at = @At("HEAD"), remap = false)
    private void ae2ltpp$captureUpgradeRitual(CallbackInfo callback) {
        ae2ltpp$finishingRitual = ForbiddenArcanusHephaestusForgeAdapter.activeRitualId(level, this);
    }

    @Inject(method = "finishRitual", at = @At("TAIL"), remap = false)
    private void ae2ltpp$recordCompletedUpgrade(CallbackInfo callback) {
        ForbiddenArcanusHephaestusForgeAdapter.markUpgradeCompleted(level, pos, this, ae2ltpp$finishingRitual);
    }

    @Inject(method = "finishRitual", at = @At("RETURN"), remap = false)
    private void ae2ltpp$clearFinishingRitual(CallbackInfo callback) {
        ae2ltpp$finishingRitual = null;
    }

    @Inject(method = "failRitual", at = @At("HEAD"), remap = false)
    private void ae2ltpp$invalidateFailedUpgrade(CallbackInfo callback) {
        if (ae2ltpp$finishingRitual == null) {
            ForbiddenArcanusHephaestusForgeAdapter.invalidateFailedUpgrade(level, pos, this);
        }
    }
}
