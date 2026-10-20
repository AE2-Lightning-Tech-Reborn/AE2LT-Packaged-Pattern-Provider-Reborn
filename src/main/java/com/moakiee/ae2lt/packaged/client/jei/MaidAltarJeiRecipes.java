package com.moakiee.ae2lt.packaged.client.jei;

import java.util.*;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.maid.MaidAltarAccess;
import com.moakiee.ae2lt.packaged.logic.multiblock.maid.MaidAltarAdapter;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.packaged.resource.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/** Includes the P ingredient in transfer data rather than a decorative cost label. */
final class MaidAltarJeiRecipes {
    record View(List<List<GenericStack>> inputs, GenericStack output) {}
    static final RecipeType<View> TYPE = RecipeType.create("ae2ltpp", "maid_altar", View.class);
    static void categories(IRecipeCategoryRegistration registration) {
        if (ModList.get().isLoaded("touhou_little_maid")) registration.addRecipeCategories(
                new Category(registration.getJeiHelpers().getGuiHelper().createDrawableItemStack(PPItems.MAID_ALTAR_ADAPTER.toStack()),
                registration.getJeiHelpers().getGuiHelper().getSlotDrawable()));
    }
    static void recipes(IJeiRuntime runtime) {
        var level = Minecraft.getInstance().level;
        if (level == null || !ModList.get().isLoaded("touhou_little_maid")) return;
        var recipes = new ArrayList<View>();
        recipes.add(new View(List.of(List.of(new GenericStack(AEItemKey.of(BuiltInRegistries.ITEM.get(MaidAltarAdapter.POWER_ITEM)), 1))),
                new GenericStack(MaidPowerKey.INSTANCE, MaidAltarAdapter.POWER_PER_ITEM)));
        var type = MaidAltarAccess.recipeType();
        if (type != null) for (var holder : level.getRecipeManager().getAllRecipesFor(type)) {
            var recipe = holder.value();
            long cost = MaidPowerAmounts.recipeCost(MaidAltarAccess.power(recipe));
            var result = recipe.getResultItem(level.registryAccess());
            if (!MaidAltarAccess.itemCraft(recipe) || cost < 0 || result.isEmpty()) continue;
            var inputs = new ArrayList<List<GenericStack>>();
            for (var ingredient : recipe.getIngredients()) inputs.add(Arrays.stream(ingredient.getItems())
                    .map(item -> new GenericStack(AEItemKey.of(item), 1)).toList());
            if (cost > 0) inputs.add(List.of(new GenericStack(MaidPowerKey.INSTANCE, cost)));
            recipes.add(new View(List.copyOf(inputs), new GenericStack(AEItemKey.of(result), result.getCount())));
        }
        runtime.getRecipeManager().addRecipes(TYPE, recipes);
    }
    private record Category(IDrawable icon, IDrawable slotBackground) implements IRecipeCategory<View> {
        @Override public RecipeType<View> getRecipeType() { return TYPE; }
        @Override public Component getTitle() { return Component.translatable("ae2ltpp.jei.maid.title"); }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return 216; }
        @Override public int getHeight() { return 76; }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, View recipe, IFocusGroup focuses) {
            int index = 0;
            for (var options : recipe.inputs()) {
                var slot = builder.addSlot(RecipeIngredientRole.INPUT, 3 + index++ * 18, 12).setBackground(slotBackground, -1, -1);
                for (var input : options) {
                    if (input.what() instanceof AEItemKey item) slot.addItemStack(item.toStack((int) input.amount()));
                    else slot.addIngredient(PackagedResourceIngredients.TYPE, new PackagedResourceIngredients.Resource(input.what(), input.amount()));
                }
            }
            var slot = builder.addSlot(RecipeIngredientRole.OUTPUT, 190, 12).setBackground(slotBackground, -1, -1);
            var result = recipe.output();
            if (result.what() instanceof AEItemKey item) slot.addItemStack(item.toStack((int) result.amount()));
            else slot.addIngredient(PackagedResourceIngredients.TYPE, new PackagedResourceIngredients.Resource(result.what(), result.amount()));
        }
        @Override public void draw(View recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mx, double my) {
            var help = recipe.output().what() == MaidPowerKey.INSTANCE
                    ? Component.translatable("ae2ltpp.jei.maid.help")
                    : Component.translatable("ae2ltpp.jei.maid.crafting_help");
            graphics.drawWordWrap(Minecraft.getInstance().font, help, 3, 43, 210, 0x444444);
        }
    }
}
