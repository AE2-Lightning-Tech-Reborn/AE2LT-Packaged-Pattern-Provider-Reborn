package com.moakiee.ae2lt.packaged.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moakiee.ae2lt.packaged.logic.multiblock.ars.ArsRitualCraftState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.common.block.tile.RitualBrazierTile", remap = false)
public abstract class ArsRitualBrazierMixin {
    @WrapMethod(method = "tick")
    private void ae2ltpp$captureTick(Operation<Void> original) {
        ArsRitualCraftState.duringNativeCall((BlockEntity) (Object) this, () -> original.call());
    }

    @WrapMethod(method = "startRitual(Lnet/minecraft/world/entity/player/Player;)V")
    private void ae2ltpp$captureStart(Player player, Operation<Void> original) {
        ArsRitualCraftState.duringNativeCall((BlockEntity) (Object) this, () -> original.call(player));
    }

    @Inject(method = "setRitual", at = @At("HEAD"))
    private void ae2ltpp$releasePrevious(ResourceLocation id, CallbackInfo ci) {
        ArsRitualCraftState.release((BlockEntity) (Object) this);
    }
}
