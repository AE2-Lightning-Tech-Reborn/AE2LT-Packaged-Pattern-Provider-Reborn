package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.List;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.ExpectedStackHarvestOwnership;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp_batch")
@PrefixGameTestTemplate(false)
public final class MekPhysicalBatchGameTests {
    @GameTest(templateNamespace="ae2ltpp_batch",template="adapter_conn",timeoutTicks=300)
    public static void nativeMekMmBatchInputsProduceExactlyOwnedOutputs(GameTestHelper h) {
        var level=h.getLevel(); var main=h.absolutePos(new BlockPos(2,2,2)); var provider=main.east();
        var block=BuiltInRegistries.BLOCK.get(new ResourceLocation("mekmm:large_chemical_infuser"));
        level.setBlockAndUpdate(main,block.defaultBlockState());
        var machine=(com.jerry.meklm.common.tile.machine.TileEntityLargeChemicalInfuser)level.getBlockEntity(main);
        var facing=MekReflection.getFacing(machine);
        var spec=MekmmMachines.ALL.get(1);
        for(var port:java.util.stream.Stream.concat(spec.modes().get(0).inputs().stream(),spec.modes().get(0).outputs().stream()).toList()) {
            var p=main.relative(facing.getOpposite(),port.spec().backSteps()).relative(facing.getClockWise(),port.spec().leftSteps()).above(port.spec().y());
            level.setBlockAndUpdate(p,MekanismBlocks.BOUNDING_BLOCK.getBlock().defaultBlockState());
            ((mekanism.common.tile.TileEntityBoundingBlock)level.getBlockEntity(p)).setMainLocation(main);
        }
        level.setBlockAndUpdate(provider,PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(provider.east(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var be=(PackagedPatternProviderBlockEntity)level.getBlockEntity(provider);
        be.setReturnMode(ReturnMode.OFF);
        be.getAdapterInv().setItemDirect(0,new ItemStack(PPItems.MEKMM_ADAPTER.get()));
        var logic=(PackagedPatternProviderLogic)be.getLogic();
        var hydrogen=AppmekReflection.fromChemicalStack(MekanismGases.HYDROGEN.getStack(1));
        var chlorine=AppmekReflection.fromChemicalStack(MekanismGases.CHLORINE.getStack(1));
        var product=AppmekReflection.fromChemicalStack(MekanismGases.HYDROGEN_CHLORIDE.getStack(1));
        logic.getPatternInv().setItemDirect(0,PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(chlorine,1),new GenericStack(hydrogen,1)},
                new GenericStack[]{new GenericStack(product,1)}));
        h.runAfterDelay(50,() -> {
            logic.updatePatterns(); var pattern=logic.getAvailablePatterns().get(0);
            var inputs=new KeyCounter[]{new KeyCounter(),new KeyCounter()}; inputs[0].add(chlorine,1);inputs[1].add(hydrogen,1);
            h.assertTrue(logic.getBatchCapacity(pattern)>=64,"real MekMM adapter advertises bounded batch capacity");
            long leftover=logic.pushBatch(pattern,inputs,70);
            h.assertTrue(leftover==6,"real MekMM consumes 64 reversed-input copies, leftover="+leftover);
            h.assertTrue(machine.leftTank.getStored()==64 && machine.rightTank.getStored()==64,"native bounding gas ports receive exact batches");
            var claim=ExpectedStackHarvestOwnership.load(level,main,be,"mekmm:harvest");
            h.assertTrue(claim!=null && claim.remaining().get(0).amount()==64,"MekMM owns scaled outputs, not a single recipe result");
            machine.getEnergyContainer().setEnergy(mekanism.api.math.FloatingLong.create(100000000));
            h.runAfterDelay(100,() -> {
                h.assertTrue(machine.centerTank.getStored()==64 && machine.leftTank.isEmpty() && machine.rightTank.isEmpty(),
                        "native machine processed inputs: output="+machine.centerTank.getStored()+" left="+machine.leftTank.getStored()+" right="+machine.rightTank.getStored()+" energy="+machine.getEnergyContainer().getEnergy());
                var adapter=new MekanismMoreMachinesAdapter(); var filter=new AllowedOutputFilter(); filter.allowStrict(product);
                var harvested=adapter.extractOutputs(level,main,filter,new appeng.me.helpers.MachineSource(be),be);
                h.assertTrue(harvested.stream().mapToLong(GenericStack::amount).sum()==64 && machine.centerTank.isEmpty(),
                        "native output port yields exactly 64 owned products");
                h.assertTrue(ExpectedStackHarvestOwnership.load(level,main,be,"mekmm:harvest")==null,
                        "completed harvest clears ownership without blocking the next job");
                h.assertTrue(adapter.extractOutputs(level,main,filter,new appeng.me.helpers.MachineSource(be),be).isEmpty(),"outputs cannot be harvested twice");
                h.succeed();
            });
        });
    }

    @GameTest(templateNamespace="ae2ltpp_batch",template="adapter_conn")
    public static void chemicalMutationExceptionsCannotBecomeZeroReceipts(GameTestHelper h) {
        var mutated=new java.util.concurrent.atomic.AtomicBoolean();
        var type=mekanism.api.chemical.gas.IGasHandler.class;
        Object handler=java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args) -> {
            if(method.getName().equals("insertChemical")) {mutated.set(true);throw new IllegalStateException("after chemical mutation");}
            throw new AssertionError("unexpected native handler method: "+method);
        });
        boolean threw=false;
        try {MekReflection.insertChemical(handler,MekanismGases.HYDROGEN.getStack(64),false);}
        catch(IllegalStateException expected){threw=true;}
        h.assertTrue(mutated.get()&&threw,"reflection surfaces unknown mutation instead of silently authorizing a retry");
        h.succeed();
    }
}
