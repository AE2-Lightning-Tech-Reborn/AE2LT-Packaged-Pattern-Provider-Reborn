package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp_batch")
@PrefixGameTestTemplate(false)
public final class MekRecipeBindingGameTests {
    @GameTest(templateNamespace="ae2ltpp_batch",template="adapter_conn")
    public static void nativeChemicalRecipeOrdersReversedPortsAndRejectsUnrelatedGas(GameTestHelper helper) {
        var level=helper.getLevel(); var pos=helper.absolutePos(new BlockPos(2,2,2));
        level.setBlockAndUpdate(pos,MekanismBlocks.CHEMICAL_INFUSER.getBlock().defaultBlockState());
        var machine=level.getBlockEntity(pos); var spec=MekmmMachines.ALL.get(1);
        var h=AppmekReflection.fromChemicalStack(MekanismGases.HYDROGEN.getStack(1));
        var c=AppmekReflection.fromChemicalStack(MekanismGases.CHLORINE.getStack(1));
        var out=AppmekReflection.fromChemicalStack(MekanismGases.HYDROGEN_CHLORIDE.getStack(1));
        helper.assertTrue(h!=null && c!=null && out!=null,"native Applied Mekanistics converts chemical keys");
        var encoded=PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(c,1),new GenericStack(h,1)},new GenericStack[]{new GenericStack(out,1)});
        var pattern=PatternDetailsHelper.decodePattern(encoded,level);
        var binding=MekRecipeBinding.find(level,machine,spec,pattern);
        helper.assertTrue(binding!=null,"native controller recipe type binds expected chemical output");
        var inputs=new KeyCounter[]{new KeyCounter(),new KeyCounter()};inputs[0].add(c,1);inputs[1].add(h,1);
        var arranged=binding.arrange(level,spec.modes().get(0),pattern,inputs);
        helper.assertTrue(arranged!=null && arranged.size()==2,"generic ChemicalStack predicates assign both reversed ports");
        helper.assertTrue(inputs[0].get(c)==1 && inputs[1].get(h)==1,"binding borrows input without mutation");
        inputs[0].reset();inputs[0].add(AppmekReflection.fromChemicalStack(MekanismGases.OXYGEN.getStack(1)),1);
        helper.assertTrue(binding.arrange(level,spec.modes().get(0),pattern,inputs)==null,"unrelated gas cannot use a matching output binding");
        System.out.println("PP_MEK_BINDING_PASS native=10.4 reversed=accepted unrelated=rejected");
        helper.succeed();
    }

    @GameTest(templateNamespace="ae2ltpp_batch",template="adapter_conn")
    public static void nativeRotaryUsesGasApiInBothDirections(GameTestHelper helper) {
        var level=helper.getLevel(); var pos=helper.absolutePos(new BlockPos(2,2,2));
        level.setBlockAndUpdate(pos,MekanismBlocks.ROTARY_CONDENSENTRATOR.getBlock().defaultBlockState());
        var machine=level.getBlockEntity(pos); var spec=MekmmMachines.ALL.get(3);
        var gas=AppmekReflection.fromChemicalStack(MekanismGases.WATER_VAPOR.getStack(1));
        var fluid=appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        for (int index=0;index<2;index++) {
            var input=index==0 ? gas : fluid; var output=index==0 ? fluid : gas;
            var pattern=PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{new GenericStack(input,1)},new GenericStack[]{new GenericStack(output,1)}),level);
            var binding=MekRecipeBinding.find(level,machine,spec,pattern);
            helper.assertTrue(binding!=null && binding.supports(spec.modes().get(index)),"native rotary binds direction "+index);
            var counter=new KeyCounter();counter.add(input,1);
            helper.assertTrue(binding.arrange(level,spec.modes().get(index),pattern,new KeyCounter[]{counter})!=null,
                    "gas/fluid native predicates and output methods work for direction "+index);
            helper.assertTrue(binding.arrange(level,spec.modes().get(1-index),pattern,new KeyCounter[]{counter})==null,
                    "opposite direction cannot reuse a stale positive binding");
        }
        System.out.println("PP_MEK_ROTARY_PASS gas_api=both_directions stale_mode=rejected");
        helper.succeed();
    }
}
