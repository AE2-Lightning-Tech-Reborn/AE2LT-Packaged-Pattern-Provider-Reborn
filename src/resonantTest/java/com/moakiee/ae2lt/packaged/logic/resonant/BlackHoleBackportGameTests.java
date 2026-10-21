package com.moakiee.ae2lt.packaged.logic.resonant;

import java.util.List;
import appeng.api.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.fa.FABlackHoleAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.*;

@GameTestHolder("ae2ltpp_resonant")
@PrefixGameTestTemplate(false)
public final class BlackHoleBackportGameTests {
    @GameTest(template="resonant_test")
    public static void blackHoleUsesReloadedRecipeAndExactFluidReceipt(GameTestHelper h) {
        var pos = new BlockPos(5,2,5);
        h.setBlock(pos,BuiltInRegistries.BLOCK.get(new ResourceLocation("forbidden_arcanus:black_hole")).defaultBlockState());
        var main = h.absolutePos(pos); var adapter = new FABlackHoleAdapter();
        var output = AEItemKey.of(BuiltInRegistries.ITEM.get(new ResourceLocation("forbidden_arcanus:xpetrified_orb")));
        var encoded = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEFluidKey.of(Fluids.WATER),1200)},new GenericStack[]{new GenericStack(output,1)});
        var pattern = PatternDetailsHelper.decodePattern(encoded,h.getLevel());
        var binding = adapter.bind(h.getLevel(),main,pattern);
        h.assertTrue(binding != null,"native datapack black-hole binding");
        var counter = new KeyCounter(); counter.add(AEFluidKey.of(Fluids.WATER),1200);
        var before = h.getBlockEntity(pos).saveWithoutMetadata();
        var result = adapter.planVirtualWithBinding(h.getLevel(),main,pattern,new KeyCounter[]{counter},binding.handle(),IActionSource.empty());
        h.assertTrue(result != null && result.outputs().equals(List.of(new GenericStack(output,1))) && result.commit(),"exact virtual conversion");
        h.assertTrue(before.equals(h.getBlockEntity(pos).saveWithoutMetadata()),"native black-hole XP remains unchanged");
        counter.add(AEFluidKey.of(Fluids.WATER),-1);
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(),main,pattern,new KeyCounter[]{counter},binding.handle(),IActionSource.empty())==null,"fractional conversion rejected");
        counter.add(AEItemKey.of(net.minecraft.world.item.Items.DIAMOND),1);
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(),main,pattern,new KeyCounter[]{counter},binding.handle(),IActionSource.empty())==null,"unrelated extra input rejected");
        h.succeed();
    }
}
