package com.moakiee.ae2lt.packaged.mixin;

import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraCraftState;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraOfferingMachine;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.ellpeck.naturesaura.blocks.tiles.BlockEntityOfferingTable", remap = false)
public abstract class NaturesAuraOfferingMixin implements NaturesAuraOfferingMachine {
    @Shadow public abstract void tick();
    @Unique private boolean ae2ltpp$activateNow;
    @Unique private ItemEntity ae2ltpp$offeredStart;

    @Override
    public void ae2ltpp$activateOffering() {
        ae2ltpp$activateNow = true;
        try {
            tick();
        } finally {
            ae2ltpp$activateNow = false;
            ae2ltpp$settleStart();
        }
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getGameTime()J"))
    private long ae2ltpp$triggerImmediately(long time) {
        return ae2ltpp$activateNow ? 0 : time;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends Entity> List<T> ae2ltpp$providePaidStart(Level level, Class<T> entityClass, AABB bounds) {
        var be = (BlockEntity) (Object) this;
        if (!NaturesAuraCraftState.active(be)) return level.getEntitiesOfClass(entityClass, bounds);
        var start = NaturesAuraCraftState.offeringStart(be);
        if (start.isEmpty()) return List.of();
        var pos = be.getBlockPos();
        // The native recipe consumes this entity, but it is never added to the level.
        // Magnets, collectors and entity-join handlers therefore cannot see the paid item.
        ae2ltpp$offeredStart = new ItemEntity(level, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, start);
        ae2ltpp$offeredStart.setNoPickUpDelay();
        return List.of(entityClass.cast(ae2ltpp$offeredStart));
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void ae2ltpp$acknowledgeConsumption(CallbackInfo ci) {
        ae2ltpp$settleStart();
    }

    @Unique
    private void ae2ltpp$settleStart() {
        if (ae2ltpp$offeredStart != null) {
            if (ae2ltpp$offeredStart.isRemoved()) NaturesAuraCraftState.consumeOfferingStart((BlockEntity) (Object) this);
            ae2ltpp$offeredStart = null;
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean ae2ltpp$captureResult(Level level, Entity entity) {
        return NaturesAuraCraftState.capture((BlockEntity) (Object) this, entity) || level.addFreshEntity(entity);
    }
}
