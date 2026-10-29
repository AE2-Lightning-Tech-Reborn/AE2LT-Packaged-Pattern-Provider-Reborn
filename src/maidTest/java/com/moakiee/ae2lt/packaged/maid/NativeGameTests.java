package com.moakiee.ae2lt.packaged.maid;

import java.util.*;
import appeng.api.behaviors.PickupSink;
import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.*;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.maid.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.apothic.*;
import com.moakiee.ae2lt.packaged.recipe.*;
import com.moakiee.ae2lt.packaged.registry.*;
import com.moakiee.ae2lt.packaged.resource.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("ae2ltpp") @PrefixGameTestTemplate(false)
public final class NativeGameTests {
    static ResourceLocation id(String s) { return ResourceLocation.parse(s); }
    static boolean absent(GameTestHelper h, String mod) { if (ModList.get().isLoaded(mod)) return false; h.succeed(); return true; }
    static GenericStack item(Item i, long n) { return new GenericStack(AEItemKey.of(i),n); }
    static GenericStack item(String i, long n) { return item(BuiltInRegistries.ITEM.get(id(i)),n); }
    static IPatternDetails pattern(GameTestHelper h,List<GenericStack> in,List<GenericStack> out) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(in,out),h.getLevel());
    }
    static KeyCounter[] inputs(List<GenericStack> in) { var k=new KeyCounter();in.forEach(s->k.add(s.what(),s.amount()));return new KeyCounter[]{k}; }
    static BlockEntity block(GameTestHelper h,BlockPos pos,String id) {
        h.getLevel().setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(id(id)).defaultBlockState());return h.getLevel().getBlockEntity(pos);
    }
    static final class Store implements MEStorage {
        final KeyCounter stock=new KeyCounter(); boolean reject;
        public long insert(AEKey key,long amount,Actionable mode,IActionSource source) { if(mode==Actionable.MODULATE)stock.add(key,amount);return amount; }
        public long extract(AEKey key,long amount,Actionable mode,IActionSource source) { long n=reject&&mode==Actionable.MODULATE?0:Math.min(amount,stock.get(key));if(mode==Actionable.MODULATE)stock.add(key,-n);return n; }
        public void getAvailableStacks(KeyCounter out){out.addAll(stock);}
        public Component getDescription(){return Component.literal("Native test reserves");}
    }
    static PackagedPatternProviderBlockEntity provider(GameTestHelper h,BlockPos pos) {
        var p=(PackagedPatternProviderBlockEntity)block(h,pos,"ae2ltpp:packaged_pattern_provider");
        h.getLevel().setBlockAndUpdate(pos.west(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.north(),AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());return p;
    }
    @GameTest(template="maid_test") public static void powerKeyRoundTripAndOptionalRegistration(GameTestHelper h) {
        boolean maid=ModList.get().isLoaded("touhou_little_maid"),apothic=ModList.get().isLoaded("apothic_enchanting");
        h.assertTrue(AEKeyTypes.getAll().contains(MaidPowerKeyType.INSTANCE)==maid,"P channel only with maid");
        var imports=appeng.parts.automation.StackWorldBehaviors.withImportStrategy();
        var exports=appeng.parts.automation.StackWorldBehaviors.withExportStrategy();
        h.assertTrue(imports.contains(MaidPowerKeyType.INSTANCE)==maid
                &&exports.contains(MaidPowerKeyType.INSTANCE)==maid,"P bus strategies conditional");
        h.assertTrue(MultiblockAdapterRegistry.getAll().stream().anyMatch(a->a instanceof MaidAltarAdapter)==maid,"maid adapter only with native mod");
        h.assertTrue(MultiblockAdapterRegistry.getAll().stream().anyMatch(a->a instanceof RavenInfusionAdapter)==apothic,"raven adapter only with native mod");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("ae2ltpp:packaged_core/maid_altar_packaged_core")).isPresent()==maid,"maid recipe conditional");
        h.assertTrue(!h.getLevel().getRecipeManager().getAllRecipesFor(RavenRecipes.TYPE.get()).isEmpty()==apothic,"raven recipe conditional");
        if (!maid) { h.succeed(); return; }
        var key=MaidPowerKey.INSTANCE;var registries=h.getLevel().registryAccess();
        h.assertTrue(AEKey.fromTagGeneric(registries,key.toTagGeneric(registries))==key,"P NBT round trip preserves singleton key");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),registries);
        try { AEKey.writeKey(buffer,key);h.assertTrue(AEKey.readKey(buffer)==key,"P network codec"); }
        finally { buffer.release(); }
        var pattern=pattern(h,List.of(new GenericStack(key,60)),List.of(item(Items.DIAMOND,1)));
        h.assertTrue(pattern.getInputs()[0].getPossibleInputs()[0].what()==key
                &&pattern.getInputs()[0].getPossibleInputs()[0].amount()*pattern.getInputs()[0].getMultiplier()==60,"P pattern retains fractional displayed amount");
        h.succeed();
    }
    @GameTest(template="maid_test") public static void experienceSelectionMergedRemainderAndReload(GameTestHelper h) {
        var water=AEFluidKey.of(Fluids.WATER);var lava=AEFluidKey.of(Fluids.LAVA);var stock=new KeyCounter();stock.add(water,200);stock.add(lava,300);
        var strategy=new ExperienceOrbPickupStrategy(()->stock);
        if(ExperienceOrbPickupStrategy.select(stock)==null) {
            var untouched=new ExperienceOrb(h.getLevel(),0,0,0,7);
            h.assertTrue(!strategy.pickUpEntity(null,(k,n,m)->{throw new AssertionError("no XP fluid must not insert");},untouched)&&untouched.isAlive(),"no registered XP fluid preserves orb");h.succeed();return;
        }
        h.assertTrue(strategy.select(stock).equals(lava),"largest network XP fluid wins");
        var orb=new ExperienceOrb(h.getLevel(),0,0,0,7);((ExperienceOrbAccess)orb).ae2ltpp$setCount(2);
        var received=new KeyCounter();
        PickupSink partial=(key,n,mode)->{long take=mode==Actionable.SIMULATE?n:31;if(mode==Actionable.MODULATE)received.add(key,take);return take;};
        h.assertTrue(strategy.pickUpEntity(null,partial,orb),"partial real receipt");
        h.assertTrue(ExperienceOrbPickupStrategy.pending(orb)&&((ExperienceOrbAccess)orb).ae2ltpp$getCount()==2,"partial member retained");
        var saved=new net.minecraft.nbt.CompoundTag();orb.saveWithoutId(saved);var restored=new ExperienceOrb(h.getLevel(),0,0,0,7);restored.load(saved);
        stock.set(water,9999);
        PickupSink complete=(key,n,mode)->{if(mode==Actionable.MODULATE)received.add(key,n);return n;};
        h.assertTrue(strategy.pickUpEntity(null,complete,restored),"resume exact fluid after reload");
        h.assertTrue(received.get(lava)==140&&((ExperienceOrbAccess)restored).ae2ltpp$getCount()==1,"one paid merged member");
        h.assertTrue(strategy.pickUpEntity(null,complete,restored)&&!restored.isAlive()&&received.get(water)==140,"next member selects current largest");
        var blocked=new ExperienceOrb(h.getLevel(),0,0,0,7);
        h.assertTrue(!strategy.pickUpEntity(null,(k,n,m)->0,blocked)&&blocked.isAlive(),"full storage preserves XP");h.succeed();
    }
    @GameTest(template="maid_test") public static void powerPickupAndNativeBeaconReceipts(GameTestHelper h) throws Exception {
        if(absent(h,"touhou_little_maid"))return;
        var type=BuiltInRegistries.ENTITY_TYPE.get(id("touhou_little_maid:power_point"));var orb=type.create(h.getLevel());
        h.assertTrue(orb!=null,"native P entity registered");var value=orb.getClass().getField("value");value.setInt(orb,60);
        var strategy=new MaidPowerPickupStrategy();h.assertTrue(strategy.pickUpEntity(null,(k,n,m)->17,orb),"P partial receipt");
        h.assertTrue(value.getInt(orb)==43&&orb.isAlive(),"P exact remaining integer");
        h.assertTrue(strategy.pickUpEntity(null,(k,n,m)->n,orb)&&!orb.isAlive(),"P full receipt");
        var bottom=h.absolutePos(new BlockPos(2,2,2));var beaconBlock=BuiltInRegistries.BLOCK.get(id("touhou_little_maid:maid_beacon"));
        h.getLevel().setBlock(bottom,beaconBlock.defaultBlockState(),2);
        beaconBlock.setPlacedBy(h.getLevel(),bottom,beaconBlock.defaultBlockState(),null,beaconBlock.asItem().getDefaultInstance());
        var beacon=h.getLevel().getBlockEntity(bottom.above());
        var storage=h.getLevel().getCapability(PackagedResourceStorage.CAPABILITY,beacon.getBlockPos(),Direction.UP);
        h.assertTrue(storage!=null,"native beacon ME capability");
        h.assertTrue(storage.insert(MaidPowerKey.INSTANCE,60,Actionable.SIMULATE,IActionSource.empty())==60,"P storage simulation");
        h.assertTrue(storage.getAvailableStacks().get(MaidPowerKey.INSTANCE)==0,"simulation preserves beacon");
        h.assertTrue(storage.insert(MaidPowerKey.INSTANCE,60,Actionable.MODULATE,IActionSource.empty())==60,"beacon deposit");
        h.assertTrue(storage.extract(MaidPowerKey.INSTANCE,60,Actionable.MODULATE,IActionSource.empty())==60,"beacon withdrawal");h.succeed();
    }
    static MaidAltarAccess.Layout altar(GameTestHelper h) throws Exception {
        var builder=Class.forName("com.github.tartaricacid.touhoulittlemaid.block.multiblock.MultiBlockAltar").getConstructor().newInstance();
        var start=h.absolutePos(new BlockPos(2,2,2));var direction=Direction.NORTH;
        var template=(StructureTemplate)builder.getClass().getMethod("getTemplate",net.minecraft.server.level.ServerLevel.class,Direction.class).invoke(builder,h.getLevel(),direction);
        template.placeInWorld(h.getLevel(),start,start,new StructurePlaceSettings(),h.getLevel().random,3);
        builder.getClass().getMethod("build",Level.class,BlockPos.class,Direction.class,StructureTemplate.class).invoke(builder,h.getLevel(),start,direction,template);
        for(var pos:BlockPos.betweenClosed(start,start.offset(9,5,9))) {var layout=MaidAltarAccess.layout(h.getLevel(),pos,false);if(layout!=null)return layout;}
        throw new AssertionError("No native altar layout");
    }
    @GameTest(template="maid_test") public static void nativeAltarCraftAndConversion(GameTestHelper h) throws Exception {
        if(absent(h,"touhou_little_maid"))return;var layout=altar(h);var adapter=new MaidAltarAdapter();var pos=layout.anchor().getBlockPos();
        var recipe=h.getLevel().getRecipeManager().byKey(id("ae2ltpp:packaged_core/maid_altar_packaged_core")).orElseThrow();
        var in=new ArrayList<GenericStack>();for(var ingredient:recipe.value().getIngredients())in.add(new GenericStack(AEItemKey.of(ingredient.getItems()[0]),1));in.add(new GenericStack(MaidPowerKey.INSTANCE,100));
        var out=List.of(item(PPItems.MAID_ALTAR_ADAPTER.get(),1));var p=pattern(h,in,out);var binding=adapter.bind(h.getLevel(),pos,p);
        h.assertTrue(binding!=null,"native altar core binds");
        var oldDrop=new ItemEntity(h.getLevel(),layout.output().getX()+0.5,layout.output().getY()+0.5,layout.output().getZ()+0.5,new ItemStack(Items.DIRT));h.getLevel().addFreshEntity(oldDrop);
        var result=new ArrayList<GenericStack>();var plan=adapter.planWithBinding(h.getLevel(),pos,p,inputs(in),binding.handle(),IActionSource.empty());
        h.assertTrue(plan!=null&&DispatchExecutor.execute(plan,IActionSource.empty(),result::addAll).success(),"native altar commit");
        h.assertTrue(result.equals(out)&&oldDrop.isAlive(),"only own output returned");
        for(var pillar:layout.pillars())h.assertTrue(MaidAltarAccess.inventory(pillar).getStackInSlot(0).isEmpty(),"native pillar consumed");
        var conversion=List.of(item("touhou_little_maid:power_point",1));var target=List.of(new GenericStack(MaidPowerKey.INSTANCE,60));var cp=pattern(h,conversion,target);var cb=adapter.bind(h.getLevel(),pos,cp);
        h.assertTrue(cb!=null&&adapter.planVirtualWithBinding(h.getLevel(),pos,cp,inputs(conversion),cb.handle(),IActionSource.empty())!=null,"exact 0.6 P conversion");h.succeed();
    }
    @GameTest(template="maid_test",timeoutTicks=500) public static void ravenFirstCoreCpuAndFull100Levels(GameTestHelper h) {
        if(absent(h,"apothic_enchanting"))return;var p=provider(h,h.absolutePos(new BlockPos(3,2,5)));var pos=p.getBlockPos().east();block(h,pos,ApothicAccess.RAVEN.toString());
        var input=List.of(item(PPItems.BASIC_PACKAGED_CORE.get(),1),item(Items.LAPIS_LAZULI,3),new GenericStack(AEFluidKey.of(Fluids.WATER),619400));var output=List.of(item(PPItems.APOTHIC_INFUSION_ADAPTER.get(),1));
        var logic=(PackagedPatternProviderLogic)p.getLogic();logic.getPatternInv().setItemDirect(0,PatternDetailsHelper.encodeProcessingPattern(input,output));
        var store=new Store();input.forEach(s->store.stock.add(s.what(),s.amount()));
        h.runAfterDelay(60,()->{
            p.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m->m.mount(store,0));logic.updatePatterns();p.getCoreOrder().request(PPItems.APOTHIC_INFUSION_ADAPTER.get());
            h.succeedWhen(()->{h.assertTrue(p.getInstalledAdapterStack().is(PPItems.APOTHIC_INFUSION_ADAPTER.get()),"raven core installed: "+p.getCoreOrder().status());
                for(var s:input)h.assertTrue(store.stock.get(s.what())==0,"full core cost consumed");h.assertTrue(p.getMainNode().getGrid().getCraftingService().getCpus().stream().noneMatch(cpu->cpu.isBusy()),"CPU complete");});
        });
    }
    @GameTest(template="maid_test",timeoutTicks=150) public static void ravenMixedSourcesAndOrdinaryRejection(GameTestHelper h) {
        if(absent(h,"apothic_enchanting"))return;var p=provider(h,h.absolutePos(new BlockPos(3,2,5)));var pos=p.getBlockPos().east();var table=block(h,pos,ApothicAccess.RAVEN.toString());
        var adapter=new RavenInfusionAdapter();var input=List.of(item(PPItems.BASIC_PACKAGED_CORE.get(),1),item(Items.LAPIS_LAZULI,1),new GenericStack(AEFluidKey.of(Fluids.WATER),400));var output=List.of(item(PPItems.APOTHIC_INFUSION_ADAPTER.get(),1));var pattern=pattern(h,input,output);
        ApothicAccess.inventory(table).insertItem(0,new ItemStack(Items.LAPIS_LAZULI,1),false);var store=new Store();store.stock.add(AEItemKey.of(Items.LAPIS_LAZULI),10);store.stock.add(AEFluidKey.of(Fluids.WATER),619000);
        h.runAfterDelay(60,()->{
            p.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m->m.mount(store,0));var source=IActionSource.ofMachine(p);var binding=adapter.bind(h.getLevel(),pos,pattern);h.assertTrue(binding!=null,"mixed source binding");
            var plan=adapter.planWithBinding(h.getLevel(),pos,pattern,inputs(input),binding.handle(),source);var returns=new ArrayList<GenericStack>();h.assertTrue(plan!=null&&DispatchExecutor.execute(plan,source,returns::addAll).success(),"mixed source craft");
            h.assertTrue(returns.equals(output)&&ApothicAccess.inventory(table).getStackInSlot(0).isEmpty()&&store.stock.get(AEItemKey.of(Items.LAPIS_LAZULI))==9&&store.stock.get(AEFluidKey.of(Fluids.WATER))==0,"exact pattern/table/network debit");
            block(h,pos,"minecraft:enchanting_table");h.assertTrue(adapter.bind(h.getLevel(),pos,pattern)==null&&!adapter.canDispatch(h.getLevel(),pos,binding.handle()),"ordinary table rejects stale and new binding");h.succeed();
        });
    }
    @GameTest(template="maid_test",timeoutTicks=150) public static void actualPlaneUsesNetworkAndNativeEntities(GameTestHelper h) {
        var p=provider(h,h.absolutePos(new BlockPos(3,2,5)));var pos=p.getBlockPos().east();
        var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        appeng.api.parts.PartHelper.setPart(h.getLevel(),pos,null,actor,AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
        var plane=appeng.api.parts.PartHelper.setPart(h.getLevel(),pos,Direction.EAST,actor,AEParts.ANNIHILATION_PLANE.asItem());
        var store=new Store();store.stock.add(AEFluidKey.of(Fluids.WATER),100);store.stock.add(AEFluidKey.of(Fluids.LAVA),500);
        h.runAfterDelay(60,()->{
            var grid=p.getMainNode().getGrid();grid.getStorageService().addGlobalStorageProvider(m->m.mount(store,0));
            h.assertTrue(plane.getGridNode().isActive(),"powered actual annihilation plane");
            h.runAfterDelay(10,()->{
                var orb=new ExperienceOrb(h.getLevel(),pos.getX()+0.95,pos.getY()+0.4,pos.getZ()+0.5,7);
                plane.onEntityCollision(orb);
                if(ExperienceOrbPickupStrategy.select(store.stock)==null) h.assertTrue(orb.isAlive()&&store.stock.get(AEFluidKey.of(Fluids.LAVA))==500,"plane leaves XP without a registered XP fluid");
                else h.assertTrue(!orb.isAlive()&&store.stock.get(AEFluidKey.of(Fluids.LAVA))==640&&store.stock.get(AEFluidKey.of(Fluids.WATER))==100,"plane selects actual largest network fluid");
                if(ModList.get().isLoaded("touhou_little_maid")) {
                    var entity=BuiltInRegistries.ENTITY_TYPE.get(id("touhou_little_maid:power_point")).create(h.getLevel());
                    try {entity.getClass().getField("value").setInt(entity,60);}catch(Exception e){throw new RuntimeException(e);}
                    entity.setPos(pos.getX()+0.95,pos.getY()+0.4,pos.getZ()+0.5);plane.onEntityCollision(entity);
                    h.assertTrue(!entity.isAlive()&&store.stock.get(MaidPowerKey.INSTANCE)==60,"actual plane collects native P");
                }
                h.succeed();
            });
        });
    }
    @GameTest(template="maid_test") public static void nativeInfusionKeepsComponentsAndRejectsWrongCosts(GameTestHelper h) {
        if(absent(h,"apothic_enchanting"))return;
        var pos=h.absolutePos(new BlockPos(3,2,3));block(h,pos,ApothicAccess.RAVEN.toString());var adapter=new RavenInfusionAdapter();
        var recipes=ApothicAccess.recipes(h.getLevel());h.assertTrue(!recipes.isEmpty(),"native infusion registry populated");
        int tested=0;
        for(var holder:recipes) {
            if(holder.value().getClass().getSimpleName().equals("KeepNBTInfusionRecipe")) {
                var nativeInput=ApothicAccess.input(holder.value()).getItems()[0].copy();nativeInput.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("retained library"));
                var result=ApothicAccess.assemble(holder.value(),nativeInput);h.assertTrue(result.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString().equals("retained library"),"native subclass keeps components");
                var in=List.of(new GenericStack(AEItemKey.of(nativeInput),1),item(Items.LAPIS_LAZULI,3),new GenericStack(AEFluidKey.of(Fluids.WATER),(long)ApothicAccess.experience(holder.value())*20));
                var out=List.of(new GenericStack(AEItemKey.of(result),result.getCount()));var pattern=pattern(h,in,out);var binding=adapter.bind(h.getLevel(),pos,pattern);h.assertTrue(binding!=null,"component-aware recipe binding");
                var ret=new ArrayList<GenericStack>();h.assertTrue(DispatchExecutor.execute(adapter.planWithBinding(h.getLevel(),pos,pattern,inputs(in),binding.handle(),IActionSource.empty()),IActionSource.empty(),ret::addAll).success()&&ret.equals(out),"native infusion actual result");tested++;
            }
        }
        h.assertTrue(tested>0,"keep-NBT recipe tested");
        var bad=List.of(item(PPItems.BASIC_PACKAGED_CORE.get(),1),item(Items.LAPIS_LAZULI,4),new GenericStack(AEFluidKey.of(Fluids.WATER),619400));
        var coreOutput=List.of(item(PPItems.APOTHIC_INFUSION_ADAPTER.get(),1));
        h.assertTrue(adapter.bind(h.getLevel(),pos,pattern(h,bad,coreOutput))==null,"overpayment is not silently destroyed");
        for (var wrongCore : List.of(Items.DIAMOND, PPItems.MAID_ALTAR_ADAPTER.get())) {
            var withoutBasic=List.of(item(wrongCore,1),item(Items.LAPIS_LAZULI,3),new GenericStack(AEFluidKey.of(Fluids.WATER),619400));
            h.assertTrue(adapter.bind(h.getLevel(),pos,pattern(h,withoutBasic,coreOutput))==null,"raven core requires its basic core");
        }
        var withoutCore=List.of(item(Items.LAPIS_LAZULI,3),new GenericStack(AEFluidKey.of(Fluids.WATER),619400));
        h.assertTrue(adapter.bind(h.getLevel(),pos,pattern(h,withoutCore,coreOutput))==null,"raven core cannot omit basic core");
        h.succeed();
    }
    @GameTest(template="maid_test",timeoutTicks=150) public static void ravenChangedNetworkRollsBackLocalStock(GameTestHelper h) {
        if(absent(h,"apothic_enchanting"))return;var p=provider(h,h.absolutePos(new BlockPos(3,2,5)));var pos=p.getBlockPos().east();var table=block(h,pos,ApothicAccess.RAVEN.toString());
        ApothicAccess.inventory(table).insertItem(0,new ItemStack(Items.LAPIS_LAZULI,3),false);
        var store=new Store();store.stock.add(AEFluidKey.of(Fluids.WATER),619400);var in=List.of(item(PPItems.BASIC_PACKAGED_CORE.get(),1));var out=List.of(item(PPItems.APOTHIC_INFUSION_ADAPTER.get(),1));var pattern=pattern(h,in,out);var adapter=new RavenInfusionAdapter();
        h.runAfterDelay(60,()->{
            p.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m->m.mount(store,0));var source=IActionSource.ofMachine(p);var binding=adapter.bind(h.getLevel(),pos,pattern);var plan=adapter.planWithBinding(h.getLevel(),pos,pattern,inputs(in),binding.handle(),source);h.assertTrue(plan!=null,"simulation sees enough stock");store.reject=true;
            var ret=new ArrayList<GenericStack>();h.assertTrue(DispatchExecutor.execute(plan,source,ret::addAll).failure(),"changed reserve rejects commit");
            h.assertTrue(ret.isEmpty()&&ApothicAccess.inventory(table).getStackInSlot(0).getCount()==3&&store.stock.get(AEFluidKey.of(Fluids.WATER))==619400,"local lapis restored and no output minted");h.succeed();
        });
    }

    @GameTest(template="maid_test",timeoutTicks=500) public static void nativeMaidCoreCpuOrder(GameTestHelper h) throws Exception {
        if(absent(h,"touhou_little_maid"))return;var layout=altar(h);var machine=layout.anchor().getBlockPos();
        // Wireless connection exercises a real altar position without modifying its formation.
        var pos=h.absolutePos(new BlockPos(13,2,5));var p=(PackagedPatternProviderBlockEntity)block(h,pos,"ae2ltpp:wireless_packaged_pattern_provider");
        h.getLevel().setBlockAndUpdate(pos.west(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());h.getLevel().setBlockAndUpdate(pos.north(),AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        var recipe=h.getLevel().getRecipeManager().byKey(id("ae2ltpp:packaged_core/maid_altar_packaged_core")).orElseThrow();var in=new ArrayList<GenericStack>();
        for(var ingredient:recipe.value().getIngredients())in.add(new GenericStack(AEItemKey.of(ingredient.getItems()[0]),1));in.add(new GenericStack(MaidPowerKey.INSTANCE,100));
        var logic=(PackagedPatternProviderLogic)p.getLogic();logic.getPatternInv().setItemDirect(0,PatternDetailsHelper.encodeProcessingPattern(in,List.of(item(PPItems.MAID_ALTAR_ADAPTER.get(),1))));
        var store=new Store();in.forEach(s->store.stock.add(s.what(),s.amount()));
        h.runAfterDelay(60,()->{
            p.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m->m.mount(store,0));logic.updatePatterns();h.assertTrue(p.addOrUpdateConnection(h.getLevel().dimension(),machine,Direction.UP),"wireless coreless native altar binding");
            p.getCoreOrder().request(PPItems.MAID_ALTAR_ADAPTER.get());h.succeedWhen(()->{
                h.assertTrue(p.getInstalledAdapterStack().is(PPItems.MAID_ALTAR_ADAPTER.get()),"native maid core installed: "+p.getCoreOrder().status());for(var input:in)h.assertTrue(store.stock.get(input.what())==0,"exact native maid CPU input payment");
                h.assertTrue(p.getMainNode().getGrid().getCraftingService().getCpus().stream().noneMatch(cpu->cpu.isBusy()),"native maid CPU complete");
            });
        });
    }

    @GameTest(template="maid_test",timeoutTicks=150) public static void ravenFluidCapabilityAndNetworkShareCost(GameTestHelper h) {
        if(absent(h,"apothic_enchanting"))return;var p=provider(h,h.absolutePos(new BlockPos(3,2,5)));var pos=p.getBlockPos().east();var table=block(h,pos,ApothicAccess.RAVEN.toString());
        RavenFluidFixture.tank(table).fill(new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER,1000),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        ApothicAccess.inventory(table).insertItem(0,new ItemStack(Items.LAPIS_LAZULI,2),false);
        var in=List.of(item(PPItems.BASIC_PACKAGED_CORE.get(),1),item(Items.LAPIS_LAZULI,1),new GenericStack(AEFluidKey.of(Fluids.WATER),400));var out=List.of(item(PPItems.APOTHIC_INFUSION_ADAPTER.get(),1));var pattern=pattern(h,in,out);var adapter=new RavenInfusionAdapter();
        var store=new Store();store.stock.add(AEFluidKey.of(Fluids.WATER),618000);
        h.runAfterDelay(60,()->{
            p.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m->m.mount(store,0));var source=IActionSource.ofMachine(p);var binding=adapter.bind(h.getLevel(),pos,pattern);var plan=adapter.planWithBinding(h.getLevel(),pos,pattern,inputs(in),binding.handle(),source);var ret=new ArrayList<GenericStack>();
            h.assertTrue(plan!=null&&DispatchExecutor.execute(plan,source,ret::addAll).success()&&ret.equals(out),"pattern + table fluid capability + network craft");
            h.assertTrue(RavenFluidFixture.tank(table).isEmpty()&&ApothicAccess.inventory(table).getStackInSlot(0).isEmpty()&&store.stock.get(AEFluidKey.of(Fluids.WATER))==0,"all three sources charged exactly");h.succeed();
        });
    }

}
