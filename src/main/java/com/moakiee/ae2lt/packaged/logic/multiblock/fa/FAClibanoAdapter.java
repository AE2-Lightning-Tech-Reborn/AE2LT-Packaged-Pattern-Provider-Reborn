package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native Clibano cooking, fuel, fire tiers and enhancers remain authoritative. */
public final class FAClibanoAdapter implements MultiblockAdapter {
    private record Match(RecipeHolder<?> recipe, int copies) {}
    @Override public int priority() { return 130; }
    @Override public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) { return AdapterIds.FA_CLIBANO; }
    @Override public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return FAAccess.clibano(level, pos) != null;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        try {
            var type = (RecipeType) FAAccess.field(FAAccess.type(FAAccess.CLIBANO), "RECIPE_TYPE");
            var declared = FAPatterns.declared(pattern);
            if (declared == null) return null;
            for (var entry : level.getRecipeManager().getAllRecipesFor(type)) {
                var holder = (RecipeHolder<?>) entry;
                var result = holder.value().getResultItem(level.registryAccess());
                if (result.isEmpty() || pattern.getOutputs().size()!=1 || !AEItemKey.of(result).equals(pattern.getPrimaryOutput().what())) continue;
                long amount = pattern.getPrimaryOutput().amount();
                if (amount <= 0 || amount % result.getCount()!=0 || amount/result.getCount()>64) continue;
                int copies = (int)(amount/result.getCount());
                if (FAPatterns.assign(holder.value().getIngredients(),declared,copies,false)!=null)
                    return new BindingResult(new Match(holder,copies), BindingMode.REAL);
            }
        } catch (RuntimeException | LinkageError e) { failure("bind",e); }
        return null;
    }
    @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        var core = FAAccess.clibano(level,pos);
        return core != null && handle instanceof Match && FAAccess.stack(core,3).isEmpty() && FAAccess.stack(core,4).isEmpty();
    }
    @Override public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        try {
            if (!(handle instanceof Match match) || !canDispatch(level,pos,handle)) return null;
            var current = level.getRecipeManager().byKey(match.recipe().id()).orElse(null);
            if (current == null || current.value()!=match.recipe().value()) return null;
            var result = current.value().getResultItem(level.registryAccess());
            if (!FAPatterns.output(pattern,AEItemKey.of(result),(long)result.getCount()*match.copies())) return null;
            var keys = FAPatterns.assign(current.value().getIngredients(),FAPatterns.actual(inputs),match.copies(),false);
            if (keys==null || keys.isEmpty() || keys.size()>2) return null;
            var core = FAAccess.clibano(level,pos);
            var first = keys.getFirst().toStack(match.copies());
            var second = keys.size()==2 ? keys.get(1).toStack(match.copies()) : ItemStack.EMPTY;
            var recipeInput = FAAccess.construct(FAAccess.BASE+"common.item.crafting.ClibanoRecipeInput",first,second);
            var selected = (Optional<?>)FAAccess.call(FAAccess.field(core,"quickCheck"), keys.size()==2 ? "getAlloyRecipe" : "getRecipeFor", recipeInput,level);
            if (selected.isEmpty() || !(selected.get() instanceof RecipeHolder<?> recipe) || !recipe.id().equals(current.id())) return null;
            var fire = (Enum<?>)FAAccess.field(core, (int)FAAccess.field(core,"soulTime")>0 ? "fireType" : "nextFireType");
            if (fire.ordinal()<((Enum<?>)FAAccess.call(current.value(),"requiredFireType")).ordinal()) return null;
            if ((int)FAAccess.field(core,"burnTime")<=0 && FAAccess.stack(core,2).getBurnTime(RecipeType.BLASTING)<=0) return null;
            long room=0;
            for (int slot:new int[]{5,6}) {
                var existing=FAAccess.stack(core,slot);
                if (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing,result))
                    room+=Math.max(0,result.getMaxStackSize()-existing.getCount());
            }
            if (room<result.getCount()) return null;
            var inventory=FAAccess.inventory(core);
            var targets=new ArrayList<TargetSlot>();
            for(int i=0;i<keys.size();i++) {
                int slot=3+i; var key=keys.get(i);
                if (match.copies()>inventory.getSlotLimit(slot) || match.copies()>key.toStack().getMaxStackSize()) return null;
                targets.add(new TargetSlot(level,core.getBlockPos(),null,List.of(new GenericStack(key,match.copies())),InsertionStrategy.CUSTOM,
                        (stack,mode)-> {
                            if (!FAAccess.live(core) || !key.equals(stack.what()) || stack.amount()>Integer.MAX_VALUE) return 0L;
                            return stack.amount()-inventory.insertItem(slot,key.toStack((int)stack.amount()),mode==Actionable.SIMULATE).getCount();
                        }));
            }
            return new DispatchPlan(targets,core::setChanged,(accepted,sink)->{
                for(var receipt:accepted) {
                    int index=targets.indexOf(receipt.target());
                    if(index<0 || !FAAccess.live(core)) continue;
                    int slot=3+index; var actual=inventory.getStackInSlot(slot);
                    if(actual.isEmpty() || !receipt.stack().what().equals(AEItemKey.of(actual))) continue;
                    var recovered=inventory.extractItem(slot,(int)receipt.stack().amount(),false);
                    if(!recovered.isEmpty()) sink.accept(new GenericStack(AEItemKey.of(recovered),recovered.getCount()));
                }
            });
        } catch (RuntimeException | LinkageError e) { failure("plan",e); return null; }
    }
    @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos, AllowedOutputFilter filter, IActionSource source) {
        var out=new ArrayList<GenericStack>();
        try {
            var core=FAAccess.clibano(level,pos);
            if(core==null) return out;
            var inventory=FAAccess.inventory(core);
            for(int slot:new int[]{5,6}) {
                var stack=inventory.extractItem(slot,Integer.MAX_VALUE,false);
                if(!stack.isEmpty()) out.add(new GenericStack(AEItemKey.of(stack),stack.getCount()));
            }
            if(!out.isEmpty()) core.setChanged();
        } catch(RuntimeException | LinkageError e) { failure("return",e); }
        return out;
    }
    private static void failure(String phase, Throwable e) { CompatibilityDiagnostics.failure(FAClibanoAdapter.class,phase,e); }
}
