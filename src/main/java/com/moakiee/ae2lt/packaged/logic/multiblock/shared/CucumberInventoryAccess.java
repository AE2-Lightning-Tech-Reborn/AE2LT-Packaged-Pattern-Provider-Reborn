package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
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
        var level = be.getLevel();
        if (level != null) {
            var inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(),
                    be.getBlockState(), be, null);
            // These callers also perform validated recipe remainder/slot updates.
            if (inventory instanceof IItemHandlerModifiable) return inventory;
        }
        // Legacy versions or wrappers may omit full modifiable capability access.
        return ReflectionSupport.findClassCached(BASE_INVENTORY)
                .filter(type -> type.isInstance(be))
                .flatMap(type -> ReflectionSupport.findMethodCached(type, "getInventory"))
                .flatMap(method -> ReflectionSupport.invoke(method, be))
                .filter(IItemHandler.class::isInstance)
                .map(IItemHandler.class::cast)
                .orElse(null);
    }
}
