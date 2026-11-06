package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import java.lang.reflect.Method;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Full native Cucumber inventory; sided capability wrappers cannot replace its slot layout. */
public final class CucumberInventoryAccess {
    private CucumberInventoryAccess() {}

    @Nullable
    public static IItemHandler get(BlockEntity be, @Nullable Method getInventory) {
        if (getInventory == null || !getInventory.getDeclaringClass().isInstance(be)) return null;
        try {
            var value = getInventory.invoke(be);
            return value instanceof IItemHandler handler ? handler : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return null;
        }
    }
}
