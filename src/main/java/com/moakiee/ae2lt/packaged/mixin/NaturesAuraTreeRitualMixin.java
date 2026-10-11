package com.moakiee.ae2lt.packaged.mixin;

import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraCraftState;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraManagedMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.ellpeck.naturesaura.blocks.tiles.BlockEntityWoodStand", remap = false)
public abstract class NaturesAuraTreeRitualMixin implements NaturesAuraManagedMachine {
    @Shadow private RecipeHolder<?> recipe;
    @Shadow private BlockPos ritualPos;
    @Shadow private int timer;
    @Unique private ResourceLocation ae2ltpp$resumeRecipe;
    @Unique private boolean ae2ltpp$wasRunning;

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean ae2ltpp$captureResult(Level level, Entity entity) {
        return NaturesAuraCraftState.capture((BlockEntity) (Object) this, entity) || level.addFreshEntity(entity);
    }

    // 41.10 writes RecipeHolder.toString(), which is not a resource location.
    // Repair only crafts owned by this integration, preserving native progress.
    @Inject(method = "writeNBT", at = @At("RETURN"))
    private void ae2ltpp$saveRecipe(CompoundTag tag, @Coerce Object saveType,
            HolderLookup.Provider registries, CallbackInfo ci) {
        if (NaturesAuraCraftState.active((BlockEntity) (Object) this) && "TILE".equals(saveType.toString()) && ritualPos != null) {
            var id = recipe != null ? recipe.id() : ae2ltpp$resumeRecipe;
            if (id != null) {
                tag.putString("recipe", id.toString());
                tag.putLong("ritual_pos", ritualPos.asLong());
                tag.putInt("timer", timer);
            }
        }
    }

    @Inject(method = "readNBT", at = @At("RETURN"))
    private void ae2ltpp$loadRecipe(CompoundTag tag, @Coerce Object saveType,
            HolderLookup.Provider registries, CallbackInfo ci) {
        if (NaturesAuraCraftState.active((BlockEntity) (Object) this) && tag.contains("recipe") && recipe == null) {
            ae2ltpp$resumeRecipe = ResourceLocation.tryParse(tag.getString("recipe"));
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ae2ltpp$resume(CallbackInfo ci) {
        var be = (BlockEntity) (Object) this;
        if (ae2ltpp$resumeRecipe != null && be.getLevel() != null && !be.getLevel().isClientSide) {
            recipe = be.getLevel().getRecipeManager().byKey(ae2ltpp$resumeRecipe).orElse(null);
            ae2ltpp$resumeRecipe = null;
        }
        ae2ltpp$wasRunning = recipe != null;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void ae2ltpp$releaseCancelledCraft(CallbackInfo ci) {
        if (ae2ltpp$wasRunning && recipe == null) NaturesAuraCraftState.cancelWithoutResult((BlockEntity) (Object) this);
    }
}
