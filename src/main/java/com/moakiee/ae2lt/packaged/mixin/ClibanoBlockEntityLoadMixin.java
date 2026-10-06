package com.moakiee.ae2lt.packaged.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoBlockEntity", remap = false)
public abstract class ClibanoBlockEntityLoadMixin {
    @Shadow private BlockState replaceState;

    @Inject(method = {"load(Lnet/minecraft/nbt/CompoundTag;)V", "m_142466_(Lnet/minecraft/nbt/CompoundTag;)V"},
            at = @At("TAIL"), remap = false)
    private void ae2ltpp$restoreDismantleStateBeforeWorldAttachment(CompoundTag tag, CallbackInfo callback) {
        if (((BlockEntity) (Object) this).getLevel() == null && tag.contains("State", Tag.TAG_COMPOUND)) {
            replaceState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("State"));
        }
    }
}
