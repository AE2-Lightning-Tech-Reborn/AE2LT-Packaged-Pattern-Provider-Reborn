package com.moakiee.ae2lt.packaged.mixin;

import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraCraftState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native player breaks spawn inventory contents without clearing their slots. */
@Pseudo
@Mixin(targets = "de.ellpeck.naturesaura.blocks.tiles.BlockEntityImpl", remap = false)
public abstract class NaturesAuraInventoryDropMixin {
    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void ae2ltpp$releasePaidContentsBeforeNativeDrop(CallbackInfo ci) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() instanceof ServerLevel level) {
            NaturesAuraCraftState.beforeBlockEntityRemoval(level, be.getBlockPos());
        }
    }
}
