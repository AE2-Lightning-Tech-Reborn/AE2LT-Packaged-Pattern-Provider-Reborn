package com.moakiee.ae2lt.packaged.mixin;

import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraCraftState;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraManagedMachine;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "de.ellpeck.naturesaura.blocks.tiles.BlockEntityNatureAltar", remap = false)
public abstract class NaturesAuraAltarMixin implements NaturesAuraManagedMachine {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/items/ItemStackHandler;setStackInSlot(ILnet/minecraft/world/item/ItemStack;)V"))
    private void ae2ltpp$captureResult(ItemStackHandler inventory, int slot, ItemStack result) {
        inventory.setStackInSlot(slot, NaturesAuraCraftState.capture((BlockEntity) (Object) this, result)
                ? ItemStack.EMPTY : result);
    }
}
