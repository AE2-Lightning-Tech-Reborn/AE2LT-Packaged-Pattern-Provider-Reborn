package com.moakiee.ae2lt.packaged.client.jei;

import java.util.*;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.apothic.ApothicAccess;
import com.moakiee.ae2lt.packaged.recipe.*;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.packaged.resource.ExperienceOrbPickupStrategy;
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
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

final class RavenInfusionJeiRecipes {
    record View(List<ItemStack> inputs, ItemStack output, long fluid) {}
    static final RecipeType<View> TYPE = RecipeType.create("ae2ltpp", "raven_infusion", View.class);
    static void categories(IRecipeCategoryRegistration registration) {
        if (ModList.get().isLoaded("apothic_enchanting")) registration.addRecipeCategories(new Category(
                registration.getJeiHelpers().getGuiHelper().createDrawableItemStack(PPItems.APOTHIC_INFUSION_ADAPTER.toStack()),
                registration.getJeiHelpers().getGuiHelper().getSlotDrawable()));
    }
    static void recipes(IJeiRuntime runtime) {
        var level = Minecraft.getInstance().level;
        if (level == null || !ModList.get().isLoaded("apothic_enchanting")) return;
        var recipes = new ArrayList<View>();
        for (var ignored : level.getRecipeManager().getAllRecipesFor(RavenRecipes.TYPE.get()))
            recipes.add(new View(List.of(PPItems.BASIC_PACKAGED_CORE.toStack()), PPItems.APOTHIC_INFUSION_ADAPTER.toStack(), RavenCoreRecipe.FLUID_AMOUNT));
        for (var holder : ApothicAccess.recipes(level)) {
            var recipe = holder.value();
            long fluid = (long) ApothicAccess.experience(recipe) * ExperienceOrbPickupStrategy.MB_PER_XP;
            if (fluid <= 0) continue;
            // Split by assembled output: keep-NBT and subclass recipes may differ by input.
            for (var input : ApothicAccess.input(recipe).getItems()) {
                var result = ApothicAccess.assemble(recipe, input);
                if (!result.isEmpty()) recipes.add(new View(List.of(input.copyWithCount(1)), result, fluid));
            }
        }
        runtime.getRecipeManager().addRecipes(TYPE, recipes);
    }
    private record Category(IDrawable icon, IDrawable slotBackground) implements IRecipeCategory<View> {
        @Override public RecipeType<View> getRecipeType() { return TYPE; }
        @Override public Component getTitle() { return Component.translatable("ae2ltpp.jei.raven.title"); }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return 216; }
        @Override public int getHeight() { return 88; }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, View recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 3, 12).setBackground(slotBackground, -1, -1).addItemStacks(recipe.inputs());
            builder.addSlot(RecipeIngredientRole.INPUT, 25, 12).setBackground(slotBackground, -1, -1).addItemStack(new ItemStack(Items.LAPIS_LAZULI, 3));
            var fluidSlot = builder.addSlot(RecipeIngredientRole.INPUT, 47, 12).setBackground(slotBackground, -1, -1);
            BuiltInRegistries.FLUID.stream().filter(f -> f != Fluids.EMPTY && f.defaultFluidState().isSource()
                    && f.is(ExperienceOrbPickupStrategy.EXPERIENCE)).forEach(f -> fluidSlot.addFluidStack(f, recipe.fluid()));
            builder.addSlot(RecipeIngredientRole.OUTPUT, 190, 12).setBackground(slotBackground, -1, -1).addItemStack(recipe.output());
        }
        @Override public void draw(View recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mx, double my) {
            graphics.drawWordWrap(Minecraft.getInstance().font, Component.translatable("ae2ltpp.jei.raven.help", recipe.fluid()), 3, 40, 210, 0x444444);
        }
    }
}
