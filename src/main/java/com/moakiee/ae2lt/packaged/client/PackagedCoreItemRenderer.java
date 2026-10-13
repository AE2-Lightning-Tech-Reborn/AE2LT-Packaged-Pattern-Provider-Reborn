package com.moakiee.ae2lt.packaged.client;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;

/** Resolves the target icon displayed on a packaged core. */
final class PackagedCoreItemRenderer {
    private PackagedCoreItemRenderer() {}

    static Optional<ItemStack> targetStack(ItemStack stack) {
        for (var definition : PackagedCoreDefinition.all()) {
            if (!stack.is(definition.runtimeItem().get())) {
                continue;
            }

            var id = definition.displayItemId();
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                return Optional.empty();
            }

            var item = BuiltInRegistries.ITEM.getValue(id);
            if (item == null || item == Items.AIR) {
                return Optional.empty();
            }
            return Optional.of(new ItemStack(item));
        }
        return Optional.empty();
    }
}
