package com.moakiee.ae2lt.packaged.mixin;

import com.moakiee.ae2lt.packaged.resource.ExperienceOrbAccess;
import com.moakiee.ae2lt.packaged.resource.ExperienceOrbPickupStrategy;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin implements ExperienceOrbAccess {
    @Shadow private int count;
    @Override public int ae2ltpp$getCount() { return count; }
    @Override public void ae2ltpp$setCount(int count) { this.count = count; }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void ae2ltpp$keepUnpaidRemainder(Player player, CallbackInfo ci) {
        if (ExperienceOrbPickupStrategy.pending((ExperienceOrb) (Object) this)) ci.cancel();
    }
    @Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z", at = @At("HEAD"), cancellable = true)
    private void ae2ltpp$keepOwnRemainder(ExperienceOrb other, CallbackInfoReturnable<Boolean> cir) {
        if (ExperienceOrbPickupStrategy.pending((ExperienceOrb) (Object) this)) cir.setReturnValue(false);
    }
    @Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z", at = @At("HEAD"), cancellable = true)
    private static void ae2ltpp$keepOtherRemainder(ExperienceOrb other, int id, int value, CallbackInfoReturnable<Boolean> cir) {
        if (ExperienceOrbPickupStrategy.pending(other)) cir.setReturnValue(false);
    }
}
