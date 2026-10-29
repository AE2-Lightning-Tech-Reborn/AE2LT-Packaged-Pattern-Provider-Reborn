package com.moakiee.ae2lt.packaged.mixin;

import com.moakiee.ae2lt.packaged.logic.multiblock.ars.ArsScribesMachine;
import com.moakiee.ae2lt.packaged.logic.multiblock.ars.ArsScribesState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.common.block.tile.ScribesTile", remap = false)
public abstract class ArsScribesMixin implements ArsScribesMachine {
    @Shadow private ItemStack stack;
    @Shadow public List<ItemStack> consumedStacks;
    @Shadow public RecipeHolder<?> recipe;
    @Shadow ResourceLocation recipeID;
    @Shadow public boolean crafting;
    @Shadow public int craftingTicks;

    @Override public boolean ae2ltpp$isScribeIdle() {
        return recipe == null && (recipeID == null || recipeID.getPath().isEmpty())
                && consumedStacks.isEmpty() && !crafting && craftingTicks <= 0 && stack.isEmpty()
                && !ArsScribesState.hasOutput((BlockEntity) (Object) this);
    }
    @Override public void ae2ltpp$beginScribing(RecipeHolder<?> selected, List<ItemStack> inputs) {
        if (!ae2ltpp$isScribeIdle()) throw new IllegalStateException("Scribes table is occupied");
        var copies = new ArrayList<ItemStack>(inputs.size());
        inputs.forEach(input -> copies.add(input.copy()));
        // Equivalent paid native state, replacing only the player-XP payment with fluid.
        // No world callbacks occur between these assignments; the next native tick
        // validates remaining ingredients and starts its normal 120-tick animation.
        recipe = selected;
        recipeID = selected.id();
        consumedStacks = copies;
        crafting = false;
        craftingTicks = 0;
        ArsScribesState.begin((BlockEntity) (Object) this);
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean ae2ltpp$captureGlyph(Level level, Entity entity) {
        return ArsScribesState.capture((BlockEntity) (Object) this, entity) || level.addFreshEntity(entity);
    }
    @Inject(method = "refundConsumed", at = @At("HEAD"))
    private void ae2ltpp$releasePaidRecipe(CallbackInfo ci) {
        ArsScribesState.beforeNativeRefund((BlockEntity) (Object) this);
    }
    @Inject(method = "saveAdditional", at = @At("RETURN"))
    private void ae2ltpp$saveUnresolvedRecipe(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        // Native loading resolves recipeID on the next tick. Saving before that tick
        // must not erase a paid recipe and strand its consumed items.
        if (ArsScribesState.active((BlockEntity) (Object) this) && recipe == null && recipeID != null)
            tag.putString("recipe", recipeID.toString());
    }
}
