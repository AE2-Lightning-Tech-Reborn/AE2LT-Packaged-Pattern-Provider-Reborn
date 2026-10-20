package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.util.List;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Pure virtual conversion: it never injects XP entities or changes the black hole's XP counter. */
public final class FABlackHoleAdapter implements LinearVirtualCraftingAdapter {
    @Override public int priority(){return 132;}
    @Override public ResourceLocation requiredAdapterId(ServerLevel level,BlockPos pos){return AdapterIds.FA_BLACK_HOLE;}
    @Override public boolean recognizesMain(ServerLevel level,BlockPos pos,BlockEntity be){return FAAccess.is(be,FAAccess.BASE+"common.block.entity.BlackHoleBlockEntity");}
    @Override public BindingResult bind(ServerLevel level,BlockPos pos,IPatternDetails pattern){
        var amounts=FAPatterns.declared(pattern);
        for(var recipe:level.getRecipeManager().getAllRecipesFor(FARecipes.BLACK_HOLE.get()))
            if(result(level,recipe.value(),pattern,amounts)!=null) return new BindingResult(recipe,BindingMode.VIRTUAL);
        return null;
    }
    @Override public boolean canDispatch(ServerLevel level,BlockPos pos,Object handle){return recognizesMain(level,pos,level.getBlockEntity(pos));}
    @Override public VirtualCraftingResult planVirtualWithBinding(ServerLevel level,BlockPos pos,IPatternDetails pattern,KeyCounter[] inputs,Object handle,IActionSource source){
        if(!canDispatch(level,pos,handle) || !(handle instanceof RecipeHolder<?> holder) || !(holder.value() instanceof FABlackHoleRecipe recipe)
                || level.getRecipeManager().byKey(holder.id()).map(RecipeHolder::value).orElse(null)!=recipe) return null;
        return result(level,recipe,pattern,FAPatterns.actual(inputs));
    }
    private static VirtualCraftingResult result(ServerLevel level,FABlackHoleRecipe recipe,IPatternDetails pattern,KeyCounter amounts){
        if(amounts==null)return null;
        var stacks=FAPatterns.stacks(amounts);
        if(stacks.size()!=1 || !(stacks.getFirst().what() instanceof AEFluidKey fluid)
                || !fluid.getFluid().is(TagKey.create(Registries.FLUID,recipe.fluidTag())))return null;
        long input=stacks.getFirst().amount();
        if(input<=0 || input%recipe.fluidAmount()!=0)return null;
        long count=input/recipe.fluidAmount();
        var item=recipe.getResultItem(level.registryAccess());
        if(item.isEmpty() || !FAPatterns.output(pattern,AEItemKey.of(item),count))return null;
        return new VirtualCraftingResult(List.of(new GenericStack(AEItemKey.of(item),count)));
    }
    @Override public DispatchPlan planWithBinding(ServerLevel level,BlockPos pos,IPatternDetails pattern,KeyCounter[] inputs,Object handle,IActionSource source){return null;}
    @Override public List<GenericStack> extractOutputs(ServerLevel level,BlockPos pos,AllowedOutputFilter filter,IActionSource source){return List.of();}
}
