package com.moakiee.ae2lt.packaged.client.jei;

import java.util.ArrayList;
import java.util.List;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

/** Mirrors the loaded native JEI recipes, including datapack changes and non-item ingredients. */
@JeiPlugin
public final class PackagedCoreJeiPlugin implements IModPlugin {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PackagedCoreJeiPlugin.class);
    public static final RecipeType<CoreRecipe> TYPE = RecipeType.create("ae2ltpp", "packaged_cores", CoreRecipe.class);
    private static IJeiRuntime runtime;
    private static long runtimeGeneration;
    private static mezz.jei.api.helpers.IJeiHelpers helpers;
    public record Slot(RecipeIngredientRole role, List<ITypedIngredient<?>> ingredients) {}
    public record CoreRecipe(ItemStack result, IRecipeCategory<Object> category, Object nativeRecipe,
                             List<Slot> slots) {
        public boolean installedUpgrade() {
            return category == null && slots.stream().anyMatch(slot -> slot.role() == RecipeIngredientRole.CATALYST);
        }
    }
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.parse("ae2ltpp:cores"); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        FAJeiRecipes.categories(registration);
        MaidAltarJeiRecipes.categories(registration);
        RavenInfusionJeiRecipes.categories(registration);
        helpers = registration.getJeiHelpers();
        registration.addRecipeCategories(new Category(helpers.getGuiHelper()
                .createDrawableItemStack(PPItems.BASIC_PACKAGED_CORE.toStack())));
    }
    @Override public void registerIngredients(IModIngredientRegistration registration) {
        PackagedResourceIngredients.register(registration);
    }
    @Override public void onRuntimeUnavailable() {
        runtime = null;
        runtimeGeneration++;
    }
    @Override public void onRuntimeAvailable(IJeiRuntime available) {
        runtime = available;
        long generation = ++runtimeGeneration;
        // JEI publishes its internal runtime after calling onRuntimeAvailable. Native
        // recipe layouts may already need it for slot backgrounds, so queue this work.
        // tell() always queues; execute() may run inline on the client thread.
        Minecraft.getInstance().tell(() -> {
            if (runtime == available && runtimeGeneration == generation) {
                registerRuntimeRecipes(available);
            }
        });
    }
    private static void registerRuntimeRecipes(IJeiRuntime available) {
        FAJeiRecipes.recipes(available);
        MaidAltarJeiRecipes.recipes(available);
        RavenInfusionJeiRecipes.recipes(available);
        var manager = available.getRecipeManager();
        var results = new ArrayList<ItemStack>();
        results.add(PPItems.BASIC_PACKAGED_CORE.toStack());
        for (var definition : PackagedCoreDefinition.all()) results.add(definition.runtimeItem().get().getDefaultInstance());
        var recipes = new ArrayList<CoreRecipe>();
        for (var result : results) {
            var focus = helpers.getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, result);
            manager.createRecipeCategoryLookup().limitFocus(List.of(focus)).get().filter(c -> !c.getRecipeType().equals(TYPE))
                    .filter(c -> !result.is(PPItems.FA_FORGE_ADAPTER.get()) || c.getRecipeType().equals(FAJeiRecipes.TYPE))
                    .filter(c -> !result.is(PPItems.APOTHIC_INFUSION_ADAPTER.get()) || c.getRecipeType().equals(RavenInfusionJeiRecipes.TYPE))
                    .filter(c -> !result.is(PPItems.MAID_ALTAR_ADAPTER.get()) || c.getRecipeType().equals(MaidAltarJeiRecipes.TYPE))
                    .forEach(category -> collect(available, category, result, focus, recipes));
        }
        var level = Minecraft.getInstance().level;
        if (level != null) for (var holder : level.getRecipeManager().getAllRecipesFor(
                com.moakiee.ae2lt.packaged.recipe.CoreUpgradeRecipes.TYPE.get())) {
            var upgrade = holder.value();
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
            // Give JEI users an explicit transferable recipe for ordinary/bulk manufacture as well.
            var supplied = slots.stream().map(slot -> slot.role() == RecipeIngredientRole.CATALYST
                    ? new Slot(RecipeIngredientRole.INPUT, slot.ingredients()) : slot).toList();
            recipes.add(new CoreRecipe(upgrade.result().copy(), null, holder, supplied));
        }
        manager.addRecipes(TYPE, recipes);
    }
    @SuppressWarnings("unchecked")
    private static <T> void collect(IJeiRuntime available, IRecipeCategory<T> category, ItemStack result,
                                   mezz.jei.api.recipe.IFocus<ItemStack> focus, List<CoreRecipe> out) {
        var manager = available.getRecipeManager();
        // The same native object may have different ingredients in different categories.
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<T, Boolean>());
        try {
            manager.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get().forEach(recipe -> {
                if (!seen.add(recipe)) return;
                try {
                    manager.createRecipeLayoutDrawable(category, recipe, helpers.getFocusFactory().getEmptyFocusGroup())
                            .ifPresent(layout -> out.add(new CoreRecipe(result.copy(),
                                    (IRecipeCategory<Object>) category, recipe, JeiRecipeSnapshots.slots(layout))));
                } catch (RuntimeException | LinkageError failure) {
                    LOGGER.warn("Cannot mirror native JEI recipe in {} for {}", category.getRecipeType().getUid(), result, failure);
                }
            });
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("Cannot query native JEI category {} for {}", category.getRecipeType().getUid(), result, failure);
        }
    }
    private record Category(IDrawable icon) implements IRecipeCategory<CoreRecipe> {
        @Override public RecipeType<CoreRecipe> getRecipeType() { return TYPE; }
        @Override public Component getTitle() { return Component.translatable("ae2ltpp.jei.core_title"); }
        @Override public IDrawable getIcon() { return icon; }
        private static final int COLUMNS = 7;
        private static final int VISIBLE_ROWS = 3;
        @Override public int getWidth() { return 176; }
        @Override public int getHeight() { return 140; }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, CoreRecipe recipe, IFocusGroup focuses) {
            int input = 0, output = 0;
            for (var slot : recipe.slots()) {
                boolean isOutput = slot.role() == RecipeIngredientRole.OUTPUT;
                int index = isOutput ? output++ : input++;
                int x = isOutput ? 154 : 3 + index % COLUMNS * 18;
                int y = 20 + (isOutput ? index : index / COLUMNS) * 18;
                var builderSlot = builder.addSlot(slot.role(), x, y).setStandardSlotBackground().addTypedIngredients(slot.ingredients());
                if (recipe.category() == null && slot.role() == RecipeIngredientRole.CATALYST) {
                    builderSlot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("ae2ltpp.jei.installed_core")));
                }
            }
        }
        @Override public void draw(CoreRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mx, double my) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, font.plainSubstrByWidth((recipe.category() == null ? Component.translatable(recipe.installedUpgrade() ? "ae2ltpp.jei.upgrade" : "ae2ltpp.jei.spare") : recipe.category().getTitle()).getString(), getWidth() - 4), 2, 2, 0x404040, false);
            graphics.drawWordWrap(font, Component.translatable(recipe.category() == null ? recipe.installedUpgrade() ? "ae2ltpp.jei.installed_core_short" : "ae2ltpp.jei.supplied_core_short" : "ae2ltpp.jei.native_recipe"), 2, getHeight() - 49, getWidth() - 4, 0x305F9C);
            graphics.drawWordWrap(font, Component.translatable("ae2ltpp.jei.order_hint"), 2, getHeight() - 24, getWidth() - 4, 0x404040);
        }
        @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, CoreRecipe recipe, IFocusGroup focuses) {
            var slots = builder.getRecipeSlots().getSlots();
            var inputs = slots.stream().filter(slot -> slot.getRole() != RecipeIngredientRole.OUTPUT).toList();
            var outputs = slots.stream().filter(slot -> slot.getRole() == RecipeIngredientRole.OUTPUT).toList();
            if (inputs.size() > COLUMNS * VISIBLE_ROWS) {
                builder.addScrollGridWidget(inputs, COLUMNS, VISIBLE_ROWS).setPosition(2, 19);
            }
            if (outputs.size() > VISIBLE_ROWS) {
                builder.addScrollGridWidget(outputs, 1, VISIBLE_ROWS).setPosition(145, 19);
            }
            if (recipe.category() == null) return;
            var area = new ScreenRectangle(0, getHeight() - 49, getWidth(), 23);
            builder.addInputHandler(new IJeiInputHandler() {
                @Override public ScreenRectangle getArea() { return area; }
                @Override public boolean handleInput(double mx, double my, IJeiUserInput input) {
                    var key = input.getKey();
                    if (key.getType() != InputConstants.Type.MOUSE || key.getValue() != 0 || runtime == null) return false;
                    // Newer JEI probes handlers before committing the click.
                    if (!input.isSimulate()) {
                        runtime.getRecipesGui().showRecipes(recipe.category(), List.of(recipe.nativeRecipe()), List.of());
                    }
                    return true;
                }
            });
        }
        @Override public void getTooltip(ITooltipBuilder tooltip, CoreRecipe recipe, IRecipeSlotsView slots, double mx, double my) {
            if (my >= getHeight() - 25) tooltip.add(Component.translatable("ae2ltpp.core_order.help"));
            if (recipe.result().is(PPItems.APOTHIC_INFUSION_ADAPTER.get()) && my >= getHeight() - 25) {
                tooltip.add(Component.translatable("ae2ltpp.jei.raven.core_help"));
            }
        }
    }
}
