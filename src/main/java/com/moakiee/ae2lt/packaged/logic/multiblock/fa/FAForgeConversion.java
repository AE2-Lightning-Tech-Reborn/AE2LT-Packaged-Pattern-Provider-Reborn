package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.resource.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Inputs are converted by FA's server tick, never by an emulated conversion. */
final class FAForgeConversion {
    record Input(int index, AEItemKey item, int count, int min, int max, ItemStack remainder) {}
    record Match(List<Input> inputs) {}
    static Match match(ServerLevel level, IPatternDetails pattern, KeyCounter amounts) {
        if (amounts == null) return null;
        var inputs = new ArrayList<Input>();
        var expected = new KeyCounter();
        var occupied = new HashSet<Integer>();
        for (var stack : FAPatterns.stacks(amounts)) {
            if (!(stack.what() instanceof AEItemKey item) || stack.amount() > 64) return null;
            Input found = describe(level,item,(int)stack.amount());
            if(found==null || !occupied.add(found.index()))return null;
            inputs.add(found);
            expected.add(ForbiddenEssenceResources.ALL.get(found.index()),found.min());
            if(!found.remainder().isEmpty())expected.add(AEItemKey.of(found.remainder()),found.remainder().getCount());
        }
        if(inputs.isEmpty())return null;
        var outputs=new KeyCounter();
        for(var output:pattern.getOutputs())outputs.add(output.what(),output.amount());
        if(!FAPatterns.stacks(expected).equals(FAPatterns.stacks(outputs))) {
            // KeyCounter iteration order isn't part of the pattern contract.
            if(expected.size()!=outputs.size())return null;
            for(var entry:expected)if(outputs.get(entry.getKey())!=entry.getLongValue())return null;
        }
        return new Match(List.copyOf(inputs));
    }
    static Input describe(net.minecraft.world.level.Level level, AEItemKey item, int count) {
        Input found = null;
        for (int i=0; i<4 && found==null; i++) {
                var essence = FAAccess.essence(name(i));
                for (var nativeInput : FAAccess.registry(level,"hephaestus_forge/input")) {
                    if (!(boolean)FAAccess.call(nativeInput,"canInput",essence,item.toStack())) continue;
                    String kind = nativeInput.getClass().getSimpleName();
                    int min, max;
                    var original=item.toStack(count);
                    if (kind.equals("ExtractEnchantmentsInput")) {
                        if(count!=1)return null;
                        int xp=(int)FAAccess.call(nativeInput,"getExperienceFromItem",original);
                        min=(xp+1)/2; max=min*2-1;
                    } else if(kind.equals("EssenceDataInput") || kind.equals("EssenceStorageInput")) {
                        if(kind.equals("EssenceStorageInput") && count!=1)return null;
                        var value=FAAccess.call(nativeInput,"getMaxInputValue",original,RandomSource.create(0));
                        min=max=Math.multiplyExact((int)FAAccess.call(value,"amount"),count);
                    } else return null; // Unknown/random native extensions must supply a bounded contract first.
                    if(min<=0 || max<min)return null;
                    ItemStack remainder;
                    if(kind.equals("EssenceDataInput")) remainder=ItemStack.EMPTY;
                    else remainder=(ItemStack)FAAccess.call(nativeInput,"finishInput",original.copy(),max);
                    found=new Input(i,item,count,min,max,remainder);
                    break;
                }
            }
        return found;
    }
    static String name(int index){return ForbiddenEssenceResources.ALL.get(index).getId().getPath();}
    static DispatchPlan plan(ServerLevel level,BlockEntity forge,Match match){
        if(!FAForgeAdapter.idle(forge))return null;
        var targets=new ArrayList<TargetSlot>();
        var state=new CompoundTag();state.putString("mode","conversion");
        var jobs=new ListTag();
        for(var input:match.inputs()){
            int baseline=FAAccess.amount(forge,name(input.index()));
            if((long)baseline+input.max()>FAAccess.capacity(forge,name(input.index())))return null;
            int slot=5+input.index();
            if(input.count()>Math.min(FAAccess.inventory(forge).getSlotLimit(slot),input.item().toStack().getMaxStackSize()))return null;
            targets.add(new TargetSlot(level,forge.getBlockPos(),null,List.of(new GenericStack(input.item(),input.count())),InsertionStrategy.CUSTOM,
                    (stack,mode)->{
                        if(!FAAccess.live(forge) || !stack.what().equals(input.item()) || stack.amount()!=input.count()
                                || !FAAccess.stack(forge,slot).isEmpty())return 0L;
                        if(mode==Actionable.MODULATE)FAAccess.setStack(forge,slot,input.item().toStack(input.count()));
                        return stack.amount();
                    }));
            var job=new CompoundTag();job.putInt("index",input.index());job.putInt("baseline",baseline);
            job.putInt("min",input.min());job.putInt("max",input.max());
            job.put("remainder",input.remainder().saveOptional(level.registryAccess()));jobs.add(job);
        }
        state.put("inputs",jobs);
        return new DispatchPlan(targets,()->{forge.getPersistentData().put(FAForgeAdapter.JOB,state);forge.setChanged();},(accepted,sink)->{
            forge.getPersistentData().remove(FAForgeAdapter.JOB);
            for(var receipt:accepted){
                int i=targets.indexOf(receipt.target());if(i<0)continue;
                int slot=5+match.inputs().get(i).index();var current=FAAccess.stack(forge,slot);
                if(!current.isEmpty() && receipt.stack().what().equals(AEItemKey.of(current)) && current.getCount()==receipt.stack().amount()){
                    FAAccess.setStack(forge,slot,ItemStack.EMPTY);sink.accept(receipt.stack());
                }
            }
        });
    }
    static List<GenericStack> collect(ServerLevel level,BlockEntity forge,CompoundTag state){
        var jobs=state.getList("inputs",Tag.TAG_COMPOUND);
        for(int i=0;i<jobs.size();i++){
            var job=jobs.getCompound(i);int index=job.getInt("index");
            var expected=ItemStack.parseOptional(level.registryAccess(),job.getCompound("remainder"));
            if(!ItemStack.matches(expected,FAAccess.stack(forge,5+index)))return List.of();
            if(FAAccess.amount(forge,name(index))-job.getInt("baseline")<job.getInt("min"))return List.of();
        }
        var result=new ArrayList<GenericStack>();
        for(int i=0;i<jobs.size();i++){
            var job=jobs.getCompound(i);int index=job.getInt("index");int current=FAAccess.amount(forge,name(index));
            int amount=Math.min(job.getInt("max"),current-job.getInt("baseline"));
            FAAccess.amount(forge,name(index),current-amount);
            result.add(new GenericStack(ForbiddenEssenceResources.ALL.get(index),amount));
            var remainder=FAAccess.stack(forge,5+index).copy();
            if(!remainder.isEmpty()){FAAccess.setStack(forge,5+index,ItemStack.EMPTY);result.add(new GenericStack(AEItemKey.of(remainder),remainder.getCount()));}
        }
        forge.getPersistentData().remove(FAForgeAdapter.JOB);forge.setChanged();return result;
    }
}
