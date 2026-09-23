package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Full, stable slot access for MA/EC inventories. Their unsided capability exposes
 * the same inventory as getInventory; sided wrappers must not be substituted.
 */
public final class CucumberInventoryAccess {
    private static final String BASE_INVENTORY = "com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity";

    private CucumberInventoryAccess() {}

    @Nullable
    public static IItemHandler get(BlockEntity be) {
        return ReflectionSupport.findClassCached(BASE_INVENTORY)
                .filter(type -> type.isInstance(be))
                .flatMap(type -> ReflectionSupport.findMethodCached(type, "getInventory"))
                .flatMap(method -> ReflectionSupport.invoke(method, be))
                .map(CucumberInventoryAccess::wrap)
                .orElse(null);
    }

    @Nullable
    public static IItemHandler wrap(Object value) {
        if (value instanceof IItemHandler legacy) return legacy;
        if (value instanceof ItemStacksResourceHandler modern) {
            return new ModifiableResourceInventory(modern);
        }
        return null;
    }

    /** Keep the existing slot-based adapter contract over Cucumber's 26.1 inventory. */
    private static final class ModifiableResourceInventory implements IItemHandlerModifiable {
        private final ItemStacksResourceHandler handler;
        private final IItemHandler delegate;

        private ModifiableResourceInventory(ItemStacksResourceHandler handler) {
            this.handler = handler;
            this.delegate = IItemHandler.of(handler);
        }

        @Override public int getSlots() { return delegate.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return delegate.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return delegate.extractItem(slot, amount, simulate);
        }
        @Override public int getSlotLimit(int slot) { return delegate.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }
        @Override public void setStackInSlot(int slot, ItemStack stack) {
            handler.set(slot, ItemResource.of(stack), stack.getCount());
        }
    }
}
