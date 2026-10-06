package com.moakiee.ae2lt.packaged.logic.multiblock.chisel;

import java.lang.reflect.Method;
import java.util.List;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** The two unrelated Chisel implementations share mod id "chisel". */
final class ChiselAccess {
    private static boolean searched;
    private static Method rebornItems;
    private static Method modernGroup;
    private static Method modernItems;

    private static void lookup() throws ReflectiveOperationException {
        if (searched) return;
        searched = true;
        try {
            var type = Class.forName("com.periut.chisel.block.ChiselGroupLookup");
            rebornItems = type.getMethod("getBlocksInGroup", Item.class);
        } catch (ClassNotFoundException absentReborn) {
            var type = Class.forName("com.leclowndu93150.chisel.carving.CarvingHelper");
            modernGroup = type.getMethod("getCarvingGroupForItem", ItemStack.class);
            modernItems = type.getMethod("getItemsInGroup", TagKey.class);
        }
    }

    static boolean permits(ChiselConversion conversion) {
        if (!ModList.get().isLoaded("chisel") || conversion.inputAmount() != conversion.outputAmount()
                || !conversion.hasDefaultOutput()) return false;
        try {
            lookup();
            Object items;
            if (rebornItems != null) {
                items = rebornItems.invoke(null, conversion.input().getItem());
            } else if (modernGroup != null && modernItems != null) {
                var group = modernGroup.invoke(null, conversion.input().toStack());
                if (group == null) return false;
                items = modernItems.invoke(null, group);
            } else return false;
            return items instanceof List<?> list && list.contains(conversion.output().getItem());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            CompatibilityDiagnostics.failure(ChiselAccess.class, "conversion", failure);
            return false;
        }
    }
}
