package com.moakiee.ae2lt.packaged.mixin;

import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraCraftState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nature's Aura containers inherit this removal path for players, explosions and automation. */
@Mixin(BlockBehaviour.class)
public abstract class NaturesAuraBlockRemovalMixin {
    @Inject(method = "onRemove", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;removeBlockEntity(Lnet/minecraft/core/BlockPos;)V"))
    private void ae2ltpp$releasePaidContents(BlockState state, Level level, BlockPos pos,
            BlockState replacement, boolean moving, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) {
            NaturesAuraCraftState.beforeBlockEntityRemoval(serverLevel, pos);
        }
    }
}
