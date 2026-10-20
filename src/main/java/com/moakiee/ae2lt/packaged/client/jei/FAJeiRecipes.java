package com.moakiee.ae2lt.packaged.client.jei;

import java.util.*;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.fa.FARecipeViews;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.packaged.resource.PackagedResourceKey;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/** Processing views include real essence ingredients that FA's decorative gauges don't transfer. */
final class FAJeiRecipes {
    static final RecipeType<FARecipeViews.View> TYPE=RecipeType.create("ae2ltpp","fa_processing",FARecipeViews.View.class);
    private FAJeiRecipes(){}
    static void categories(IRecipeCategoryRegistration registration){
        if(ModList.get().isLoaded("forbidden_arcanus"))registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper().createDrawableItemStack(PPItems.FA_FORGE_ADAPTER.toStack())));
    }
    static void recipes(IJeiRuntime runtime){
        var level=Minecraft.getInstance().level;
        if(level==null||!ModList.get().isLoaded("forbidden_arcanus"))return;
        runtime.getRecipeManager().addRecipes(TYPE,FARecipeViews.recipes(level,runtime.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK)));
    }
    private record Category(IDrawable icon) implements IRecipeCategory<FARecipeViews.View>{
        @Override public RecipeType<FARecipeViews.View> getRecipeType(){return TYPE;}
        @Override public Component getTitle(){return Component.translatable("ae2ltpp.jei.fa.title");}
        @Override public IDrawable getIcon(){return icon;}
        @Override public int getWidth(){return 216;}
        @Override public int getHeight(){return 110;}
        @Override public void setRecipe(IRecipeLayoutBuilder builder,FARecipeViews.View recipe,IFocusGroup focus){
            int index=0;
            for(var options:recipe.inputs()){
                var slot=builder.addSlot(RecipeIngredientRole.INPUT,4+(index%12)*18,20+(index/12)*18);index++;
                for(var stack:options){
                    if(stack.what() instanceof AEItemKey item)slot.addItemStack(item.toStack((int)stack.amount()));
                    else if(stack.what() instanceof AEFluidKey fluid)slot.addFluidStack(fluid.getFluid(),stack.amount());
                    else if(stack.what() instanceof PackagedResourceKey resource)slot.addIngredient(PackagedResourceIngredients.TYPE,new PackagedResourceIngredients.Resource(resource,stack.amount()));
                }
            }
            index=0;
            for(var stack:recipe.outputs()){
                var slot=builder.addSlot(RecipeIngredientRole.OUTPUT,4+index++*20,68);
                if(stack.what() instanceof AEItemKey item)slot.addItemStack(item.toStack((int)stack.amount()));
                else if(stack.what() instanceof PackagedResourceKey resource)slot.addIngredient(PackagedResourceIngredients.TYPE,new PackagedResourceIngredients.Resource(resource,stack.amount()));
            }
            var machine=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(
                    recipe.kind().equals("black_hole")?"forbidden_arcanus:dark_matter":"forbidden_arcanus:hephaestus_forge_tier_1"));
            builder.addSlot(RecipeIngredientRole.CATALYST,194,68).addItemStack(new net.minecraft.world.item.ItemStack(machine));
            for(int i=0;i<recipe.enhancers().size();i++)builder.addSlot(RecipeIngredientRole.CATALYST,174-i*18,68).addItemStack(recipe.enhancers().get(i));
        }
        @Override public void draw(FARecipeViews.View recipe,IRecipeSlotsView slots,GuiGraphics graphics,double mx,double my){
            var font=Minecraft.getInstance().font;
            graphics.drawString(font,Component.translatable("ae2ltpp.jei.fa."+recipe.kind()),4,3,0x444444,false);
            graphics.drawString(font,Component.translatable("ae2ltpp.jei.fa.output"),4,56,0x555555,false);
            if(recipe.kind().equals("black_hole"))graphics.drawString(font,
                    recipe.inputs().getFirst().getFirst().amount()+" mB",26,24,0x444444,false);
            graphics.drawString(font,recipe.kind().equals("ritual")?recipe.requirement():Component.translatable("ae2ltpp.jei.fa.amount_rules"),4,94,0x555555,false);
        }
    }
}
