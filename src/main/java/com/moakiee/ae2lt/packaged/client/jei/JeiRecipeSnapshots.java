package com.moakiee.ae2lt.packaged.client.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import mezz.jei.api.gui.IRecipeLayoutDrawable;

/** Extracts transfer data through the public API shared by supported JEI 19.x releases. */
final class JeiRecipeSnapshots {
    private JeiRecipeSnapshots() {}

    static List<PackagedCoreJeiPlugin.Slot> slots(IRecipeLayoutDrawable<?> layout) {
        // Native categories can move slots into widgets in createRecipeExtras. Initialize
        // those extras first, then read the full transfer view, including widget slots.
        // tick() initializes extras without depending on JEI's internal layout class.
        layout.tick();
        var result = new ArrayList<PackagedCoreJeiPlugin.Slot>();
        for (var slot : layout.getRecipeSlotsView().getSlotViews()) {
            // Unlike getAllIngredientsList(), this API excludes blank rotation entries.
            // Keep every alternative and its original quantity; never normalize here.
            var ingredients = slot.getAllIngredients().filter(Objects::nonNull).toList();
            if (!ingredients.isEmpty()) {
                result.add(new PackagedCoreJeiPlugin.Slot(slot.getRole(), ingredients));
            }
        }
        return List.copyOf(result);
    }
}
