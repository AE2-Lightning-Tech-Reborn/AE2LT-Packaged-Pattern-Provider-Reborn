package com.moakiee.ae2lt.packaged.mixin;

import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraCraftState;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraAccess;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraManagedMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.ellpeck.naturesaura.blocks.tiles.BlockEntityWoodStand", remap = false)
public abstract class NaturesAuraTreeRitualMixin implements NaturesAuraManagedMachine {
    @Unique private ResourceLocation ae2ltpp$resumeRecipe;
    @Unique private BlockPos ae2ltpp$resumeRitualPos;
    @Unique private int ae2ltpp$resumeTimer;
    @Unique private boolean ae2ltpp$wasRunning;

    @Redirect(method = "tick", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean ae2ltpp$captureResult(Level level, Entity entity) {
        return NaturesAuraCraftState.capture((BlockEntity) (Object) this, entity) || level.addFreshEntity(entity);
    }

    @Inject(method = "writeNBT", at = @At("RETURN"), remap = false)
    private void ae2ltpp$saveRecipe(CompoundTag tag, @Coerce Object saveType,
            CallbackInfo ci) {
        BlockEntity be = (BlockEntity) (Object) this;
        BlockPos ritualPos = (BlockPos) NaturesAuraAccess.field(be, "ritualPos");
        Object currentRecipe = NaturesAuraAccess.recipe(be);
        int timer = NaturesAuraAccess.field(be, "timer") instanceof Number value ? value.intValue() : 0;
        if (NaturesAuraCraftState.active(be) && "TILE".equals(saveType.toString()) && ritualPos != null) {
            ResourceLocation id = currentRecipe instanceof Recipe<?> recipe ? recipe.getId() : ae2ltpp$resumeRecipe;
            if (id != null) {
                tag.putString("recipe", id.toString());
                tag.putLong("ritual_pos", ritualPos.asLong());
                tag.putInt("timer", timer);
            }
        }
    }

    @Inject(method = "readNBT", at = @At("RETURN"), remap = false)
    private void ae2ltpp$loadRecipe(CompoundTag tag, @Coerce Object saveType,
            CallbackInfo ci) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (NaturesAuraCraftState.active(be) && tag.contains("recipe") && NaturesAuraAccess.recipe(be) == null) {
            ae2ltpp$resumeRecipe = ResourceLocation.tryParse(tag.getString("recipe"));
            ae2ltpp$resumeRitualPos = tag.contains("ritual_pos")
                    ? BlockPos.of(tag.getLong("ritual_pos")) : null;
            ae2ltpp$resumeTimer = tag.getInt("timer");
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ae2ltpp$resume(CallbackInfo ci) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (ae2ltpp$resumeRecipe != null && be.getLevel() != null && !be.getLevel().isClientSide) {
            Recipe<?> recipe = be.getLevel().getRecipeManager().byKey(ae2ltpp$resumeRecipe).orElse(null);
            if (recipe != null && ae2ltpp$resumeRitualPos != null) {
                NaturesAuraAccess.setRitual(be, ae2ltpp$resumeRitualPos, recipe);
                NaturesAuraAccess.setTimer(be, ae2ltpp$resumeTimer);
            }
            ae2ltpp$resumeRecipe = null;
            ae2ltpp$resumeRitualPos = null;
        }
        ae2ltpp$wasRunning = NaturesAuraAccess.recipe(be) != null;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void ae2ltpp$releaseCancelledCraft(CallbackInfo ci) {
        if (ae2ltpp$wasRunning && NaturesAuraAccess.recipe((BlockEntity) (Object) this) == null) {
            NaturesAuraCraftState.cancelWithoutResult((BlockEntity) (Object) this);
        }
    }
}
