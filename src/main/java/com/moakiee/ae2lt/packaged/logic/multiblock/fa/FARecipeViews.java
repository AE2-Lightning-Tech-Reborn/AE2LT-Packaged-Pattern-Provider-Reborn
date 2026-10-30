package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.util.*;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.packaged.resource.ForbiddenEssenceResources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/** Read-only JEI data derived from the forge's live ritual source and native item components. */
public final class FARecipeViews {
    public record View(String kind,List<List<GenericStack>> inputs,List<GenericStack> outputs,ItemStack core,
                       net.minecraft.network.chat.Component requirement,List<ItemStack> enhancers){}
    private FARecipeViews(){}
    public static List<View> recipes(Level level,Collection<ItemStack> items){
        var out=new ArrayList<View>();
        for(var ritual:FARituals.all(level)){
            var recipe=ritual.recipe();
            var main=(Ingredient)FAAccess.call(recipe,"mainIngredient");
            for(var item:main.getItems()){
                var result=FAAccess.ritualResult(recipe,item);
                if(result.isEmpty())continue;
                var inputs=new ArrayList<List<GenericStack>>();inputs.add(List.of(stack(item,1)));
                for(var input:(List<?>)FAAccess.call(recipe,"inputs")){
                    int amount=(int)FAAccess.call(input,"amount");
                    var options=Arrays.stream(((Ingredient)FAAccess.call(input,"ingredient")).getItems()).map(s->stack(s,amount)).toList();
                    inputs.add(options);
                }
                var cost=FAAccess.call(FAAccess.call(recipe,"requirements"),"essences");
                for(int i=0;i<4;i++){
                    int amount=(int)FAAccess.call(cost,"get",FAAccess.essence(FAForgeConversion.name(i)));
                    if(amount>0)inputs.add(List.of(new GenericStack(ForbiddenEssenceResources.ALL.get(i),amount)));
                }
                var requirements=FAAccess.call(recipe,"requirements");
                var tier=(net.minecraft.network.chat.Component)FAAccess.call(FAAccess.call(requirements,"tier"),"getDescription");
                var enhancers=new ArrayList<ItemStack>();
                for(var holder:(net.minecraft.core.HolderSet<?>)FAAccess.call(requirements,"enhancers")){
                    var display=(net.minecraft.core.Holder<?>)FAAccess.call(holder.value(),"displayItem");
                    enhancers.add(new ItemStack((net.minecraft.world.item.Item)display.value()));
                }
                out.add(new View("ritual",List.copyOf(inputs),List.of(stack(result,result.getCount())),
                        PPItems.FA_FORGE_ADAPTER.toStack(),tier,List.copyOf(enhancers)));
            }
        }
        var seen=new HashSet<AEItemKey>();
        for(var item:items){
            if(item.isEmpty()||!seen.add(AEItemKey.of(item)))continue;
            var input=FAForgeConversion.describe(level,AEItemKey.of(item),1);if(input==null)continue;
            var outputs=new ArrayList<GenericStack>();outputs.add(new GenericStack(ForbiddenEssenceResources.ALL.get(input.index()),input.min()));
            if(!input.remainder().isEmpty())outputs.add(stack(input.remainder(),input.remainder().getCount()));
            out.add(new View(input.min()==input.max()?"conversion":"conversion_random",List.of(List.of(stack(item,1))),outputs,PPItems.FA_FORGE_ADAPTER.toStack(),net.minecraft.network.chat.Component.empty(),List.of()));
        }
        for(var holder:level.getRecipeManager().getAllRecipesFor(com.moakiee.ae2lt.packaged.recipe.FARecipes.BLACK_HOLE.get())){
            var recipe=holder.value();var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID,recipe.fluidTag());
            var fluids=BuiltInRegistries.FLUID.stream().filter(f->f.is(tag)&&f.isSource(f.defaultFluidState())).map(f->new GenericStack(AEFluidKey.of(f),recipe.fluidAmount())).toList();
            if(!fluids.isEmpty())out.add(new View("black_hole",List.of(fluids),List.of(stack(recipe.getResultItem(level.registryAccess()),1)),PPItems.FA_BLACK_HOLE_ADAPTER.toStack(),net.minecraft.network.chat.Component.empty(),List.of()));
        }
        return List.copyOf(out);
    }
    private static GenericStack stack(ItemStack item,long count){return new GenericStack(AEItemKey.of(item),count);}
}
