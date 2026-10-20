package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.registry.*;
import com.moakiee.ae2lt.packaged.resource.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("ae2ltpp") @PrefixGameTestTemplate(false)
public final class FAGameTests {
    static boolean absent(GameTestHelper h){if(ModList.get().isLoaded("forbidden_arcanus"))return false;h.succeed();return true;}
    static ResourceLocation id(String s){return ResourceLocation.parse(s);}
    static ItemStack item(String s){return new ItemStack(BuiltInRegistries.ITEM.get(id(s)));}
    static GenericStack stack(String s,long n){return new GenericStack(AEItemKey.of(item(s)),n);}
    static BlockEntity block(GameTestHelper h,BlockPos pos,String s){h.getLevel().setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(id(s)).defaultBlockState());return h.getLevel().getBlockEntity(pos);}
    static IPatternDetails pattern(GameTestHelper h,List<GenericStack> inputs,List<GenericStack> outputs){return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs,outputs),h.getLevel());}
    static KeyCounter[] inputs(List<GenericStack> stacks){var c=new KeyCounter();for(var s:stacks)c.add(s.what(),s.amount());return new KeyCounter[]{c};}
    static BlockEntity forge(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(5,3,5));
        String[] rows={"***PPP***","*PPPAPPP*","*PAPPPAP*","PPPPCPPPP","PAPCACPAP","PPPPCPPPP","*PAPPPAP*","*PPPAPPP*","***PPP***"};
        for(int z=0;z<9;z++)for(int x=0;x<9;x++){
            String path=switch(rows[z].charAt(x)){case 'P'->"polished_darkstone";case 'A'->"gilded_chiseled_polished_darkstone";case 'C'->"chiseled_arcane_polished_darkstone";default->null;};
            if(path!=null)block(h,pos.offset(x-4,-1,z-4),"forbidden_arcanus:"+path);
        }
        var forge=block(h,pos,"forbidden_arcanus:hephaestus_forge_tier_1");
        for(var offset:List.of(new BlockPos(-3,0,0),new BlockPos(3,0,0),new BlockPos(0,0,3)))block(h,pos.offset(offset),"forbidden_arcanus:darkstone_pedestal");
        return forge;
    }
    static List<GenericStack> ritualItems(){return new ArrayList<>(List.of(new GenericStack(AEItemKey.of(PPItems.BASIC_PACKAGED_CORE.get()),1),stack("forbidden_arcanus:deorum_ingot",2),stack("forbidden_arcanus:dark_matter",1)));}
    static PackagedPatternProviderBlockEntity provider(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(12,3,5));
        h.getLevel().setBlockAndUpdate(pos,PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.east(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        return (PackagedPatternProviderBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    @GameTest(template="core_test",timeoutTicks=700)
    public static void forgeRitualPriorityAndNativeCompletion(GameTestHelper h){
        if(absent(h))return;var forge=forge(h);var provider=provider(h);var adapter=new FAForgeAdapter();
        var list=ritualItems();list.add(new GenericStack(ForbiddenEssenceResources.AUREAL,20));list.add(new GenericStack(ForbiddenEssenceResources.BLOOD,30));
        var pattern=pattern(h,list,List.of(new GenericStack(AEItemKey.of(PPItems.FA_FORGE_ADAPTER.get()),1)));
        h.runAfterDelay(100,()->{
            h.assertTrue(FAAccess.structure(h.getLevel(),forge.getBlockPos()),"native forge base pattern");
            int[] before={5,2,40,0};for(int i=0;i<4;i++)FAAccess.amount(forge,FAForgeConversion.name(i),before[i]);
            var reserve=new appeng.api.storage.MEStorage(){
                final KeyCounter stock=new KeyCounter();{for(var key:ForbiddenEssenceResources.ALL)stock.add(key,100);}
                public long extract(AEKey key,long n,Actionable mode,IActionSource source){long out=Math.min(n,stock.get(key));if(mode==Actionable.MODULATE)stock.add(key,-out);return out;}
                public long insert(AEKey key,long n,Actionable mode,IActionSource source){if(mode==Actionable.MODULATE)stock.add(key,n);return n;}
                public void getAvailableStacks(KeyCounter out){out.addAll(stock);}
                public net.minecraft.network.chat.Component getDescription(){return net.minecraft.network.chat.Component.literal("FA test reserve");}
            };
            // Actual ME grid, with both the executing forge and a separate reserve mounted.
            var nativeStore=h.getLevel().getCapability(PackagedResourceStorage.CAPABILITY,forge.getBlockPos(),Direction.UP);
            provider.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(mounts->{mounts.mount(nativeStore,100);mounts.mount(reserve,0);});
            var source=IActionSource.ofMachine(provider);var binding=adapter.bind(h.getLevel(),forge.getBlockPos(),pattern);
            h.assertTrue(binding!=null,"native ritual bound");
            h.assertTrue(adapter.canDispatch(h.getLevel(),forge.getBlockPos(),binding.handle()),"idle forge");
            h.assertTrue(FAAccess.call(FAForgeAdapter.class,"pedestals",h.getLevel(),forge.getBlockPos(),3)!=null,"three linked native pedestals");
            var plan=adapter.planWithBinding(h.getLevel(),forge.getBlockPos(),pattern,inputs(list),binding.handle(),source);
            h.assertTrue(plan!=null,"native ritual plan");
            var dispatch=DispatchExecutor.execute(plan,source,s->h.fail("unexpected retained inputs "+s));
            h.assertTrue(dispatch.success(),"ritual dispatch "+dispatch);
            h.assertTrue(FAAccess.active(forge),"native ritual started");
            int[] remaining={5,1,0,0};long[] net={100,100,70,90};
            for(int i=0;i<4;i++){h.assertTrue(FAAccess.amount(forge,FAForgeConversion.name(i))==remaining[i],"pattern then existing priority "+i);h.assertTrue(reserve.getAvailableStacks().get(ForbiddenEssenceResources.ALL.get(i))==net[i],"ME only deficit "+i);}
            // Save/load metadata along with FA's own active ritual, without editing native progress.
            var saved=forge.saveWithFullMetadata(h.getLevel().registryAccess());forge.loadWithComponents(saved,h.getLevel().registryAccess());
            h.assertTrue(forge.getPersistentData().contains(FAForgeAdapter.JOB),"owned return survives native save/load");
            h.succeedWhen(()->{
                var outputs=adapter.extractOutputs(h.getLevel(),forge.getBlockPos(),new AllowedOutputFilter(),source);
                h.assertTrue(outputs.size()==1&&outputs.getFirst().what().equals(AEItemKey.of(PPItems.FA_FORGE_ADAPTER.get())),"native ritual result collected");
                h.assertTrue(adapter.extractOutputs(h.getLevel(),forge.getBlockPos(),new AllowedOutputFilter(),source).isEmpty(),"no double collection");
            });
        });
    }
    @GameTest(template="core_test",timeoutTicks=180)
    public static void insufficientForgeDoesNotConsumeInputs(GameTestHelper h){
        if(absent(h))return;var forge=forge(h);var provider=provider(h);var adapter=new FAForgeAdapter();
        var list=ritualItems();var pattern=pattern(h,list,List.of(new GenericStack(AEItemKey.of(PPItems.FA_FORGE_ADAPTER.get()),1)));
        h.runAfterDelay(100,()->{
            var nativeStore=h.getLevel().getCapability(PackagedResourceStorage.CAPABILITY,forge.getBlockPos(),Direction.UP);
            provider.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m->m.mount(nativeStore,0));
            int[] before={20,1,60,10};for(int i=0;i<4;i++)FAAccess.amount(forge,FAForgeConversion.name(i),before[i]);
            var source=IActionSource.ofMachine(provider);var binding=adapter.bind(h.getLevel(),forge.getBlockPos(),pattern);h.assertTrue(binding!=null,"ritual bound despite temporary resource shortage");
            var plan=adapter.planWithBinding(h.getLevel(),forge.getBlockPos(),pattern,inputs(list),binding.handle(),source);
            if(plan!=null)h.assertTrue(DispatchExecutor.execute(plan,source,s->h.fail("rollback must recover all "+s)).failure(),"self-mounted points cannot be counted twice");
            h.assertTrue(!FAAccess.active(forge)&&FAAccess.stack(forge,4).isEmpty()&&!forge.getPersistentData().contains(FAForgeAdapter.JOB),"no start and no input remains");
            for(int i=0;i<4;i++)h.assertTrue(FAAccess.amount(forge,FAForgeConversion.name(i))==before[i],"shortage preserves original points "+i);
            h.succeed();
        });
    }
    @GameTest(template="core_test",timeoutTicks=180)
    public static void fourNativeConversionSlotsAndContainer(GameTestHelper h){
        if(absent(h))return;var forge=forge(h);var adapter=new FAForgeAdapter();
        var blood=(ItemStack)FAAccess.call(FAAccess.type(FAAccess.BASE+"common.essence.EssenceHelper"),"createStorageItem",BuiltInRegistries.ITEM.get(id("forbidden_arcanus:blood_test_tube")),FAAccess.essence("blood"),50,1000);
        var list=List.of(stack("forbidden_arcanus:aureal_bottle",2),stack("forbidden_arcanus:soul",3),new GenericStack(AEItemKey.of(blood),1),stack("forbidden_arcanus:xpetrified_orb",1));
        var outputs=new ArrayList<GenericStack>();int[] expected={70,3,50,91};for(int i=0;i<4;i++)outputs.add(new GenericStack(ForbiddenEssenceResources.ALL.get(i),expected[i]));outputs.add(stack("forbidden_arcanus:test_tube",1));
        var pattern=pattern(h,list,outputs);
        h.runAfterDelay(5,()->{
            for(int i=0;i<4;i++)FAAccess.amount(forge,FAForgeConversion.name(i),7);
            var binding=adapter.bind(h.getLevel(),forge.getBlockPos(),pattern);h.assertTrue(binding!=null,"native conversion bound");
            var plan=adapter.planWithBinding(h.getLevel(),forge.getBlockPos(),pattern,inputs(list),binding.handle(),IActionSource.empty());h.assertTrue(plan!=null,"conversion planned");
            h.assertTrue(DispatchExecutor.execute(plan,IActionSource.empty(),s->h.fail("unexpected retention")).success(),"conversion dispatched");
            for(int i=0;i<4;i++)h.assertTrue(!FAAccess.stack(forge,i+5).isEmpty(),"actual native slot populated "+i);
            var storage=h.getLevel().getCapability(PackagedResourceStorage.CAPABILITY,forge.getBlockPos(),Direction.UP);
            h.assertTrue(storage.extract(ForbiddenEssenceResources.BLOOD,100,Actionable.MODULATE,IActionSource.empty())==0,"ME cannot steal in-flight essence");
            h.succeedWhen(()->{
                var result=adapter.extractOutputs(h.getLevel(),forge.getBlockPos(),new AllowedOutputFilter(),IActionSource.empty());h.assertTrue(!result.isEmpty(),"wait native slot conversion");
                var actual=new KeyCounter();for(var out:result)actual.add(out.what(),out.amount());
                for(var out:outputs)h.assertTrue(actual.get(out.what())==out.amount(),"exact actual native output "+out);
                for(int i=0;i<4;i++)h.assertTrue(FAAccess.amount(forge,FAForgeConversion.name(i))==7,"preexisting points preserved "+i);
            });
        });
    }
    @GameTest(template="core_test",timeoutTicks=250)
    public static void clibanoNativeDispatchAndCollection(GameTestHelper h){
        if(absent(h))return;var level=h.getLevel();var front=h.absolutePos(new BlockPos(5,3,4));
        var interaction=FAAccess.construct(FAAccess.BASE+"common.item.mundabitur.CreateClibanoInteraction",(java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState>)s->true,FAAccess.field(FAAccess.type(FAAccess.BASE+"common.block.ModBlockPatterns"),"CLIBANO_COMBUSTION_BASE"));
        var context=FAAccess.construct(FAAccess.BASE+"common.item.mundabitur.TransformPatternInteraction$TransformPatternContext",level,front,InteractionHand.MAIN_HAND,Direction.NORTH);
        FAAccess.call(interaction,"interact",context);var core=FAAccess.clibano(level,front);h.assertTrue(core!=null,"native built Clibano frame resolves core");
        FAAccess.setStack(core,2,new ItemStack(Items.COAL,2));
        var adapter=new FAClibanoAdapter();var list=List.of(stack("minecraft:ancient_debris",1));var pattern=pattern(h,list,List.of(stack("minecraft:netherite_scrap",1)));
        var binding=adapter.bind(level,front,pattern);h.assertTrue(binding!=null,"Clibano native recipe bound");
        var plan=adapter.planWithBinding(level,front,pattern,inputs(list),binding.handle(),IActionSource.empty());h.assertTrue(plan!=null,"Clibano plan");
        h.assertTrue(DispatchExecutor.execute(plan,IActionSource.empty(),s->h.fail("unexpected retention")).success(),"Clibano dispatch");
        h.succeedWhen(()->{var out=adapter.extractOutputs(level,front,new AllowedOutputFilter(),IActionSource.empty());h.assertTrue(out.size()==1&&out.getFirst().what().equals(AEItemKey.of(Items.NETHERITE_SCRAP)),"native furnace output collected");h.assertTrue(FAAccess.stack(core,2).getCount()<2,"native fuel consumed");});
    }
    @GameTest(template="core_test")
    public static void blackHoleVirtualFluidAmountAndNoNativeXpMutation(GameTestHelper h){
        if(absent(h))return;
        var pos=h.absolutePos(new BlockPos(4,3,4));var hole=block(h,pos,"forbidden_arcanus:black_hole");
        var adapter=new FABlackHoleAdapter();var key=AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        var output=stack("forbidden_arcanus:xpetrified_orb",2);var list=List.of(new GenericStack(key,2400));
        var pattern=pattern(h,list,List.of(output));var binding=adapter.bind(h.getLevel(),pos,pattern);
        h.assertTrue(binding!=null,"datapack XP fluid tag and exact ratio bind");
        var result=adapter.planVirtualWithBinding(h.getLevel(),pos,pattern,inputs(list),binding.handle(),IActionSource.empty());
        h.assertTrue(result!=null&&result.outputs().equals(List.of(output)),"2400 mB produces two real orbs");
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(),pos,pattern,inputs(List.of(new GenericStack(key,2399))),binding.handle(),IActionSource.empty())==null,"short fluid cannot create output");
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(),pos,pattern,inputs(List.of(new GenericStack(AEFluidKey.of(net.minecraft.world.level.material.Fluids.LAVA),2400))),binding.handle(),IActionSource.empty())==null,"unrelated fluid rejected");
        h.assertTrue((double)FAAccess.field(hole,"stored_xp")==0,"virtual conversion never modifies native stored XP");
        h.succeed();
    }

    @GameTest(template="core_test",timeoutTicks=1400)
    public static void realCpuConvertsEssenceThenRunsForgeRitual(GameTestHelper h){cpu(h,false);}
    @GameTest(template="core_test",timeoutTicks=1400)
    public static void firstForgeCoreNativeCpuAutoInstalls(GameTestHelper h){cpu(h,true);}
    private static void cpu(GameTestHelper h,boolean first){
        if(absent(h))return;var forge=forge(h);var pos=h.absolutePos(new BlockPos(12,3,5));var level=h.getLevel();
        level.setBlockAndUpdate(pos,PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(pos.north(),AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        var provider=(PackagedPatternProviderBlockEntity)level.getBlockEntity(pos);
        provider.setReturnMode(com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode.AUTO);
        if(!first)provider.getAdapterInv().setItemDirect(0,PPItems.FA_FORGE_ADAPTER.toStack());
        var logic=(com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic)provider.getLogic();
        var ritual=ritualItems();int[] costs={20,1,100,10};for(int n=0;n<4;n++)ritual.add(new GenericStack(ForbiddenEssenceResources.ALL.get(n),costs[n]));
        logic.getPatternInv().setItemDirect(0,PatternDetailsHelper.encodeProcessingPattern(ritual,List.of(new GenericStack(AEItemKey.of(PPItems.FA_FORGE_ADAPTER.get()),1))));
        if(!first)logic.getPatternInv().setItemDirect(1,PatternDetailsHelper.encodeProcessingPattern(List.of(stack("forbidden_arcanus:aureal_bottle",1)),List.of(new GenericStack(ForbiddenEssenceResources.AUREAL,35))));
        var stock=new KeyCounter();for(var input:ritualItems())stock.add(input.what(),input.amount());if(first)stock.add(ForbiddenEssenceResources.AUREAL,20);else stock.add(stack("forbidden_arcanus:aureal_bottle",1).what(),1);
        for(int n=1;n<4;n++)stock.add(ForbiddenEssenceResources.ALL.get(n),costs[n]);
        var storage=new MEStorage(){
            public long extract(AEKey key,long n,Actionable mode,IActionSource source){long out=Math.min(n,stock.get(key));if(mode==Actionable.MODULATE)stock.add(key,-out);return out;}
            public long insert(AEKey key,long n,Actionable mode,IActionSource source){if(mode==Actionable.MODULATE)stock.add(key,n);return n;}
            public void getAvailableStacks(KeyCounter out){out.addAll(stock);}
            public net.minecraft.network.chat.Component getDescription(){return net.minecraft.network.chat.Component.literal("FA CPU reserve");}
        };
        h.runAfterDelay(100,()->{
            provider.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m->m.mount(storage,0));
            logic.updatePatterns();h.assertTrue(provider.addOrUpdateConnection(level.dimension(),forge.getBlockPos(),Direction.WEST),"wireless forge connected");
            provider.getCoreOrder().request(PPItems.FA_FORGE_ADAPTER.get());
            h.succeedWhen(()->{
                h.assertTrue(provider.getCoreOrder().status().equals(first?"installed":"returned"),"FA CPU chain state "+provider.getCoreOrder().status());
                h.assertTrue(stock.get(AEItemKey.of(PPItems.FA_FORGE_ADAPTER.get()))==(first?0:1),"CPU delivered manufactured core to ME");
                h.assertTrue(stock.get(ForbiddenEssenceResources.AUREAL)==(first?0:15),"CPU consumed 20 of 35 native converted aureal");
                h.assertTrue(stock.get(stack("forbidden_arcanus:aureal_bottle",1).what())==0,"raw conversion input consumed once");
                h.assertTrue(provider.getInstalledAdapterStack().is(PPItems.FA_FORGE_ADAPTER.get()),"correct installed core");
                System.out.println("FA_CPU_E2E_PASS first="+first+" status="+provider.getCoreOrder().status());
            });
        });
    }

}
