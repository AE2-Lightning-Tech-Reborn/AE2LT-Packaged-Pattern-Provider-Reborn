package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.resource.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Native rituals and native four-slot essence conversion share one forge core. */
public final class FAForgeAdapter implements MultiblockAdapter {
    static final String JOB="ae2ltpp_forge_job";
    private record Ritual(ResourceLocation id,Object recipe){}
    @Override public int priority(){return 129;}
    @Override public ResourceLocation requiredAdapterId(ServerLevel level,BlockPos pos){return AdapterIds.FA_FORGE;}
    @Override public boolean recognizesMain(ServerLevel level,BlockPos pos,BlockEntity be){return FAAccess.is(be,FAAccess.FORGE);}
    @Override public BindingResult bind(ServerLevel level,BlockPos pos,IPatternDetails pattern){
        try{
            var amounts=FAPatterns.declared(pattern);
            var conversion=FAForgeConversion.match(level,pattern,amounts);
            if(conversion!=null)return new BindingResult(conversion,BindingMode.REAL);
            var registry=FAAccess.registry(level,"hephaestus_forge/ritual");
            for(var recipe:registry){
                var keys=FAPatterns.assign(ingredients(recipe),amounts,1,true);
                if(keys!=null && matchesOutput(pattern,recipe,keys.getFirst().toStack()))
                    return new BindingResult(new Ritual(registry.getKey(recipe),recipe),BindingMode.REAL);
            }
        }catch(RuntimeException|LinkageError e){failure("bind",e);}
        return null;
    }
    private static List<Ingredient> ingredients(Object recipe){
        var out=new ArrayList<Ingredient>();out.add((Ingredient)FAAccess.call(recipe,"mainIngredient"));
        for(var input:(List<?>)FAAccess.call(recipe,"inputs")){
            int amount=(int)FAAccess.call(input,"amount");if(amount<1||amount>8)return List.of();
            for(int i=0;i<amount;i++)out.add((Ingredient)FAAccess.call(input,"ingredient"));
        }
        return out.size()<=9?out:List.of();
    }
    private static ItemStack result(Object recipe,ItemStack main){return (ItemStack)FAAccess.call(FAAccess.call(recipe,"result"),"getResultItem",main.copy());}
    private static boolean matchesOutput(IPatternDetails pattern,Object recipe,ItemStack main){
        var result=result(recipe,main);return !result.isEmpty()&&FAPatterns.output(pattern,AEItemKey.of(result),result.getCount());
    }
    static boolean idle(BlockEntity forge){
        if(!FAAccess.live(forge)||FAAccess.active(forge)||forge.getPersistentData().contains(JOB))return false;
        for(int slot=4;slot<=8;slot++)if(!FAAccess.stack(forge,slot).isEmpty())return false;
        return ((List<?>)FAAccess.call(FAAccess.field(forge,"dataCache"),"cachedIngredients")).isEmpty();
    }
    @Override public boolean canDispatch(ServerLevel level,BlockPos pos,Object handle){
        var forge=level.getBlockEntity(pos);return recognizesMain(level,pos,forge)&&idle(forge);
    }
    @Override public DispatchPlan planWithBinding(ServerLevel level,BlockPos pos,IPatternDetails pattern,KeyCounter[] inputs,Object handle,IActionSource source){
        try{
            if(!canDispatch(level,pos,handle))return null;
            var forge=level.getBlockEntity(pos);var actual=FAPatterns.actual(inputs);
            if(handle instanceof FAForgeConversion.Match){
                var conversion=FAForgeConversion.match(level,pattern,actual);
                return conversion==null?null:FAForgeConversion.plan(level,forge,conversion);
            }
            if(!(handle instanceof Ritual ritual)||FAAccess.registry(level,"hephaestus_forge/ritual").get(ritual.id())!=ritual.recipe()
                    ||!FAAccess.structure(level,pos))return null;
            var keys=FAPatterns.assign(ingredients(ritual.recipe()),actual,1,true);
            if(keys==null||keys.isEmpty()||!matchesOutput(pattern,ritual.recipe(),keys.getFirst().toStack()))return null;
            var pedestals=pedestals(level,pos,keys.size()-1);if(pedestals==null)return null;
            var entries=new ArrayList<Object>();
            for(int i=0;i<pedestals.size();i++)entries.add(FAAccess.construct(FAAccess.BASE+"common.block.entity.forge.ForgeDataCache$IngredientEntry",pedestals.get(i).getBlockPos(),keys.get(i+1).toStack()));
            var cache=FAAccess.field(forge,"dataCache");
            var proposed=FAAccess.construct(FAAccess.BASE+"common.block.entity.forge.ForgeDataCache",entries,keys.getFirst().toStack(),FAAccess.call(cache,"enhancers"));
            var manager=FAAccess.call(forge,"getRitualManager");
            if(!(boolean)FAAccess.call(ritual.recipe(),"canStart",proposed,FAAccess.field(manager,"forgeTier")))return null;
            int[] cost=cost(ritual.recipe()),need=gate(ritual.recipe(),cache,cost),provided=new int[4];
            var storage=storage(source);
            for(int i=0;i<4;i++){
                long supplied=actual.get(ForbiddenEssenceResources.ALL.get(i));if(supplied>Integer.MAX_VALUE)return null;
                provided[i]=(int)supplied;int have=FAAccess.amount(forge,FAForgeConversion.name(i));
                if(need[i]>FAAccess.capacity(forge,FAForgeConversion.name(i)) || (long)have+supplied-cost[i]>FAAccess.capacity(forge,FAForgeConversion.name(i)))return null;
                long missing=Math.max(0L,(long)need[i]-have-supplied);
                if(missing>0&&(storage==null||storage.extract(ForbiddenEssenceResources.ALL.get(i),missing,Actionable.SIMULATE,source)<missing))return null;
            }
            var targets=new ArrayList<TargetSlot>();
            for(int i=0;i<keys.size();i++){
                int index=i;var key=keys.get(i);var target=i==0?forge:pedestals.get(i-1);
                targets.add(new TargetSlot(level,target.getBlockPos(),null,List.of(new GenericStack(key,1)),InsertionStrategy.CUSTOM,(stack,mode)->{
                    if(!FAAccess.live(target)||!key.equals(stack.what())||stack.amount()!=1||!physical(target,index).isEmpty())return 0L;
                    if(mode==Actionable.MODULATE)setPhysical(target,index,key.toStack());return 1L;
                }));
            }
            var escrow=new KeyCounter();
            for(int i=0;i<4;i++)if(provided[i]>0){var key=ForbiddenEssenceResources.ALL.get(i);int amount=provided[i];
                targets.add(new TargetSlot(level,pos,null,List.of(new GenericStack(key,amount)),InsertionStrategy.CUSTOM,(stack,mode)->{
                    if(!key.equals(stack.what())||stack.amount()!=amount)return 0L;
                    if(mode==Actionable.MODULATE)escrow.add(key,amount);return stack.amount();
                }));
            }
            return new DispatchPlan(targets,()->start(level,forge,ritual,cost,need,provided,storage,source,result(ritual.recipe(),keys.getFirst().toStack())),(accepted,sink)->{
                if(FAAccess.active(forge))return; // Native start already owns these physical inputs.
                forge.getPersistentData().remove(JOB);forge.setChanged();
                for(var receipt:accepted){
                    int index=targets.indexOf(receipt.target());
                    if(index>=0&&index<keys.size()){
                        var target=index==0?forge:pedestals.get(index-1);var current=physical(target,index);
                        if(!current.isEmpty()&&receipt.stack().what().equals(AEItemKey.of(current))&&current.getCount()==1){setPhysical(target,index,ItemStack.EMPTY);sink.accept(receipt.stack());}
                    }else if(escrow.get(receipt.stack().what())>=receipt.stack().amount()){
                        escrow.add(receipt.stack().what(),-receipt.stack().amount());sink.accept(receipt.stack());
                    }
                }
            });
        }catch(RuntimeException|LinkageError e){failure("plan",e);return null;}
    }
    private static MEStorage storage(IActionSource source){
        var host=source.machine().orElse(null);if(host==null||host.getActionableNode()==null)return null;
        return host.getActionableNode().getGrid().getStorageService().getInventory();
    }
    private static int[] cost(Object ritual){
        var definition=FAAccess.call(FAAccess.call(ritual,"requirements"),"essences");var cost=new int[4];
        for(int i=0;i<4;i++)cost[i]=(int)FAAccess.call(definition,"get",FAAccess.essence(FAForgeConversion.name(i)));return cost;
    }
    private static int[] gate(Object ritual,Object cache,int[] cost){
        var modifiers=new ArrayList<Object>();
        var target=FAAccess.field(FAAccess.type(FAAccess.BASE+"common.item.enhancer.EnhancerTarget"),"HEPHAESTUS_FORGE");
        for(var holder:(List<?>)FAAccess.call(cache,"enhancers")){
            var effects=(java.util.stream.Stream<?>)FAAccess.call(((Holder<?>)holder).value(),"getEffects",target);
            effects.filter(e->FAAccess.is(e,FAAccess.BASE+"common.block.entity.forge.essence.EssenceModifier")).forEach(modifiers::add);
        }
        var adjusted=FAAccess.call(FAAccess.call(FAAccess.call(ritual,"requirements"),"essences"),"applyModifiers",modifiers);
        int[] need=cost.clone();for(int i=0;i<4;i++)need[i]=Math.max(cost[i],(int)FAAccess.call(adjusted,"get",FAAccess.essence(FAForgeConversion.name(i))));return need;
    }
    private static void start(ServerLevel level,BlockEntity forge,Ritual ritual,int[] cost,int[] need,int[] supplied,MEStorage storage,IActionSource source,ItemStack output){
        int[] before=new int[4],taken=new int[4];boolean started=false;
        for(int i=0;i<4;i++)before[i]=FAAccess.amount(forge,FAForgeConversion.name(i));
        var state=new CompoundTag();state.putString("mode","ritual");state.put("output",output.save(level.registryAccess()));
        // Hide this forge from its own storage bus while asking ME for the deficit.
        forge.getPersistentData().put(JOB,state);forge.setChanged();
        try{
            for(int i=0;i<4;i++){
                int missing=(int)Math.max(0L,(long)need[i]-before[i]-supplied[i]);
                if(missing>0){taken[i]=storage==null?0:(int)storage.extract(ForbiddenEssenceResources.ALL.get(i),missing,Actionable.MODULATE,source);
                    if(taken[i]!=missing)throw new IllegalStateException("Insufficient FA essence after excluding the executing forge");}
            }
            for(int i=0;i<4;i++)FAAccess.amount(forge,FAForgeConversion.name(i),need[i]);
            var manager=FAAccess.call(forge,"getRitualManager");
            var selected=(Optional<?>)FAAccess.call(manager,"getValidRitual");
            if(selected.isEmpty()||((Holder<?>)selected.get()).value()!=ritual.recipe())throw new IllegalStateException("Native ritual selection changed");
            started=(boolean)FAAccess.call(manager,"startRitual",FakePlayerFactory.getMinecraft(level),FAAccess.call(FAAccess.call(forge,"getEssenceManager"),"getStorage"));
            if(!started)throw new IllegalStateException("Native ritual rejected start");
            for(int i=0;i<4;i++)FAAccess.amount(forge,FAForgeConversion.name(i),(int)((long)before[i]+supplied[i]+taken[i]-cost[i]));
            forge.setChanged();
        }catch(RuntimeException|LinkageError e){
            if(started||FAAccess.active(forge)){failure("native-start-after-commit",e);return;}
            // Return network reservations before DispatchExecutor refunds CPU inputs.
            for(int i=0;i<4;i++){
                FAAccess.amount(forge,FAForgeConversion.name(i),before[i]);
                long returned=taken[i]==0?0:storage.insert(ForbiddenEssenceResources.ALL.get(i),taken[i],Actionable.MODULATE,source);
                if(returned<taken[i])FAAccess.amount(forge,FAForgeConversion.name(i),before[i]+taken[i]-(int)returned);
            }
            forge.getPersistentData().remove(JOB);forge.setChanged();throw e;
        }
    }
    private static List<BlockEntity> pedestals(ServerLevel level,BlockPos forge,int count){
        var out=new ArrayList<BlockEntity>();if(count==0)return out;
        for(var pos:BlockPos.betweenClosed(forge.offset(-4,-1,-4),forge.offset(4,1,4))){
            if(!level.isLoaded(pos))continue;var be=level.getBlockEntity(pos);
            if(!FAAccess.is(be,FAAccess.PEDESTAL)||!((ItemStack)FAAccess.call(be,"getStack")).isEmpty())continue;
            var linked=(Optional<?>)FAAccess.call(FAAccess.type(FAAccess.BASE+"common.block.pedestal.effect.UpdateForgeIngredientsEffect"),"findHephaestusForge",level,pos);
            if(linked.isEmpty()||!forge.equals(linked.get()))continue;
            out.add(be);if(out.size()==count)return out;
        }return null;
    }
    private static ItemStack physical(BlockEntity be,int index){return index==0?FAAccess.stack(be,4):(ItemStack)FAAccess.call(be,"getStack");}
    private static void setPhysical(BlockEntity be,int index,ItemStack stack){
        if(index==0)FAAccess.setStack(be,4,stack);
        else {var trigger=FAAccess.field(FAAccess.type(FAAccess.BASE+"common.block.pedestal.effect.PedestalEffectTrigger"),stack.isEmpty()?"PLAYER_REMOVE_ITEM":"PLAYER_PLACE_ITEM");
            FAAccess.call(be,"setStack",stack,null,trigger);be.setChanged();}
    }
    @Override public List<GenericStack> extractOutputs(ServerLevel level,BlockPos pos,AllowedOutputFilter filter,IActionSource source){
        try{
            var forge=level.getBlockEntity(pos);if(!recognizesMain(level,pos,forge)||!forge.getPersistentData().contains(JOB))return List.of();
            var state=forge.getPersistentData().getCompound(JOB);
            if(state.getString("mode").equals("conversion"))return FAForgeConversion.collect(level,forge,state);
            if(FAAccess.active(forge))return List.of();
            var expected=ItemStack.parseOptional(level.registryAccess(),state.getCompound("output"));var current=FAAccess.stack(forge,4);
            if(!ItemStack.matches(expected,current)){
                // A manually interrupted native ritual has no promised output to collect.
                // Release our reservation without claiming or deleting the remaining input.
                forge.getPersistentData().remove(JOB);forge.setChanged();return List.of();
            }
            var output=new GenericStack(AEItemKey.of(current),current.getCount());FAAccess.setStack(forge,4,ItemStack.EMPTY);
            forge.getPersistentData().remove(JOB);forge.setChanged();return List.of(output);
        }catch(RuntimeException|LinkageError e){failure("return",e);return List.of();}
    }
    private static void failure(String phase,Throwable e){CompatibilityDiagnostics.failure(FAForgeAdapter.class,phase,e);}
}
