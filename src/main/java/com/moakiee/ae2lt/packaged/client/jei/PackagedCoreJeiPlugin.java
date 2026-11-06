package com.moakiee.ae2lt.packaged.client.jei;

import java.util.ArrayList;
import java.util.List;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

/** Mirrors the loaded native JEI recipes, including datapack changes and non-item ingredients. */
@JeiPlugin
public final class PackagedCoreJeiPlugin implements IModPlugin {
    public static final RecipeType<CoreRecipe> TYPE = RecipeType.create("ae2ltpp", "packaged_cores", CoreRecipe.class);
    private static IJeiRuntime runtime;
    private static mezz.jei.api.helpers.IJeiHelpers helpers;
    private static int ingredientRows = 1;
    public record Slot(RecipeIngredientRole role, List<ITypedIngredient<?>> ingredients) {}
    public record CoreRecipe(ItemStack result, IRecipeCategory<Object> category, Object nativeRecipe,
                             List<Slot> slots) {
        public boolean installedUpgrade() {
            return category == null && slots.stream().anyMatch(slot -> slot.role() == RecipeIngredientRole.CATALYST);
        }
    }
    @Override public ResourceLocation getPluginUid() { return new ResourceLocation("ae2ltpp:cores"); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        MaidAltarJeiRecipes.categories(registration);
        helpers = registration.getJeiHelpers();
        registration.addRecipeCategories(new Category(helpers.getGuiHelper()
                .createDrawableItemStack(new ItemStack(PPItems.BASIC_PACKAGED_CORE.get()))));
    }
    @Override public void registerIngredients(IModIngredientRegistration registration) {
    }
    @Override public void onRuntimeUnavailable() { runtime = null; }
    @Override public void onRuntimeAvailable(IJeiRuntime available) {
        runtime = available;
        // JEI publishes its global runtime only after every plugin callback returns.
        // Vanilla crafting layouts read that runtime to build their output slots.
        Minecraft.getInstance().tell(() -> {
            if (runtime == available) registerRuntimeRecipes(available);
        });
    }
    private static void registerRuntimeRecipes(IJeiRuntime available) {
        MaidAltarJeiRecipes.recipes(available);
        var manager = available.getRecipeManager();
        var results = new ArrayList<ItemStack>();
        results.add(new ItemStack(PPItems.BASIC_PACKAGED_CORE.get()));
        for (var definition : PackagedCoreDefinition.all()) results.add(definition.runtimeItem().get().getDefaultInstance());
        var recipes = new ArrayList<CoreRecipe>();
        for (var result : results) {
            var focus = helpers.getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, result);
            var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            manager.createRecipeCategoryLookup().limitFocus(List.of(focus)).get().filter(c -> !c.getRecipeType().equals(TYPE))
                    .filter(category -> !result.is(PPItems.MAID_ALTAR_ADAPTER.get()) || category.getRecipeType().equals(MaidAltarJeiRecipes.TYPE))
                    .forEach(category -> collect(category, result, focus, recipes, seen));
        }
        var level = Minecraft.getInstance().level;
        if (level != null) for (var holder : level.getRecipeManager().getAllRecipesFor(
                com.moakiee.ae2lt.packaged.recipe.CoreUpgradeRecipes.TYPE.get())) {
            var upgrade = holder;
            var slots = new ArrayList<Slot>();
            for (var ingredient : upgrade.materials()) {
                var choices = new ArrayList<ITypedIngredient<?>>();
                for (var item : ingredient.getItems()) available.getIngredientManager()
                        .createTypedIngredient(VanillaTypes.ITEM_STACK, item).ifPresent(choices::add);
                slots.add(new Slot(RecipeIngredientRole.INPUT, choices));
            }
            available.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, upgrade.requiredCore())
                    .ifPresent(item -> slots.add(new Slot(RecipeIngredientRole.CATALYST, List.of(item))));
            available.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, upgrade.result())
                    .ifPresent(item -> slots.add(new Slot(RecipeIngredientRole.OUTPUT, List.of(item))));
            recipes.add(new CoreRecipe(upgrade.result().copy(), null, holder, slots));
            var supplied = slots.stream().map(slot -> slot.role() == RecipeIngredientRole.CATALYST
                    ? new Slot(RecipeIngredientRole.INPUT, slot.ingredients()) : slot).toList();
            recipes.add(new CoreRecipe(upgrade.result().copy(), null, holder, supplied));
        }
        ingredientRows = recipes.stream().mapToInt(recipe -> (int) recipe.slots().stream()
                .filter(slot -> slot.role() != RecipeIngredientRole.OUTPUT).count())
                .map(count -> (count + 11) / 12).max().orElse(1);
        manager.addRecipes(TYPE, recipes);
    }
    @SuppressWarnings("unchecked")
    private static <T> void collect(IRecipeCategory<T> category, ItemStack result,
                                   mezz.jei.api.recipe.IFocus<ItemStack> focus, List<CoreRecipe> out, java.util.Set<Object> seen) {
        var manager = runtime.getRecipeManager();
        manager.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get().forEach(recipe -> {
            if (!seen.add(recipe)) return;
            manager.createRecipeLayoutDrawable(category, recipe, helpers.getFocusFactory().getEmptyFocusGroup())
                    .ifPresent(layout -> {
                        var slots = layout.getRecipeSlotsView().getSlotViews().stream()
                                .filter(slot -> !slot.isEmpty()).map(slot -> new Slot(slot.getRole(), slot.getAllIngredients().toList())).toList();
                        // Never infer missing ingredients from Recipe#getIngredients: native categories know counts and fluids.
                        out.add(new CoreRecipe(result.copy(), (IRecipeCategory<Object>) category, recipe, slots));
                    });
        });
    }
    private record Category(IDrawable icon) implements IRecipeCategory<CoreRecipe> {
        @Override public RecipeType<CoreRecipe> getRecipeType() { return TYPE; }
        @Override public Component getTitle() { return Component.translatable("ae2ltpp.jei.core_title"); }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return 248; }
        @Override public int getHeight() { return Math.max(120, 84 + ingredientRows * 18); }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, CoreRecipe recipe, IFocusGroup focuses) {
            int input = 0, output = 0;
            for (var slot : recipe.slots()) {
                boolean isOutput = slot.role() == RecipeIngredientRole.OUTPUT;
                int index = isOutput ? output++ : input++;
                int x = isOutput ? 224 : 3 + index % 12 * 18;
                int y = 20 + (isOutput ? index : index / 12) * 18;
                var builderSlot = builder.addSlot(slot.role(), x, y).setStandardSlotBackground().addTypedIngredients(slot.ingredients());
                if (recipe.category() == null && slot.role() == RecipeIngredientRole.CATALYST) {
                    builderSlot.addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("ae2ltpp.jei.installed_core")));
                }
            }
        }
        @Override public void draw(CoreRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mx, double my) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, font.plainSubstrByWidth((recipe.category() == null ? Component.translatable(recipe.installedUpgrade() ? "ae2ltpp.jei.upgrade" : "ae2ltpp.jei.spare") : recipe.category().getTitle()).getString(), 244), 2, 2, 0x404040, false);
            graphics.drawWordWrap(font, Component.translatable(recipe.category() == null ? recipe.installedUpgrade() ? "ae2ltpp.jei.installed_core_short" : "ae2ltpp.jei.supplied_core_short" : "ae2ltpp.jei.native_recipe"), 2, getHeight() - 45, 244, 0x305F9C);
            graphics.drawWordWrap(font, Component.translatable("ae2ltpp.jei.order_hint"), 2, getHeight() - 24, 244, 0x404040);
        }
        @Override public boolean handleInput(CoreRecipe recipe, double mx, double my, InputConstants.Key input) {
            if (recipe.category() != null && input.getType() == InputConstants.Type.MOUSE && input.getValue() == 0 && my >= getHeight() - 49 && my < getHeight() - 26) {
                runtime.getRecipesGui().showRecipes(recipe.category(), List.of(recipe.nativeRecipe()), List.of());
                return true;
            }
            return false;
        }
        @Override public List<Component> getTooltipStrings(CoreRecipe recipe, IRecipeSlotsView slots, double mx, double my) {
            return my >= getHeight() - 25 ? List.of(Component.translatable("ae2ltpp.core_order.help")) : List.of();
        }
    }
}
