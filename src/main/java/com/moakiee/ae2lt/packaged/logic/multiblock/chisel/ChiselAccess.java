package com.moakiee.ae2lt.packaged.logic.multiblock.chisel;

import java.lang.reflect.Method;
import java.util.List;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/** Native Chisel Reborn 26.1.2 carving groups. */
final class ChiselAccess {
    private static boolean searched;
    private static Method rebornItems;

    private static void lookup() throws ReflectiveOperationException {
        if (searched) return;
        searched = true;
        var type = Class.forName("com.periut.chisel.block.ChiselGroupLookup");
        rebornItems = type.getMethod("getBlocksInGroup", Item.class);
    }

    static boolean permits(ChiselConversion conversion) {
        if (!ModList.get().isLoaded("chisel") || conversion.inputAmount() != conversion.outputAmount()
                || !conversion.hasDefaultOutput()) return false;
        try {
            lookup();
            if (rebornItems == null) return false;
            var items = rebornItems.invoke(null, conversion.input().getItem());
            return items instanceof List<?> list && list.contains(conversion.output().getItem());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            CompatibilityDiagnostics.failure(ChiselAccess.class, "conversion", failure);
            return false;
        }
    }
}
