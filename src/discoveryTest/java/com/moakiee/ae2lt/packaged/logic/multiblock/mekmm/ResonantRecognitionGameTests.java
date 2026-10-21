package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.List;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.resonant.ResonantLayout;
import com.moakiee.ae2lt.packaged.logic.resonant.ResonantLayoutData;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp_discovery")
@PrefixGameTestTemplate(false)
public final class ResonantRecognitionGameTests {
    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void adjacentResonantRecognitionUsesTheBoundAnchorType(GameTestHelper h) { check(h, false); }

    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void wirelessResonantRecognitionClearsRemovedAnchors(GameTestHelper h) { check(h, true); }

    private static void check(GameTestHelper h, boolean wireless) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(6, 2, 6));
        net.minecraft.world.level.block.Block block = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get() : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        var first = wireless ? pos.east(3) : pos.east();
        var second = wireless ? pos.west(3) : pos.west();
        level.setBlockAndUpdate(first, Blocks.FURNACE.defaultBlockState());
        level.setBlockAndUpdate(second, Blocks.FURNACE.defaultBlockState());
        level.setBlockAndUpdate(pos.south(), Blocks.CHEST.defaultBlockState());
        var core = new ItemStack(PPItems.RESONANT_ADAPTER.get());
        ResonantLayoutData.set(core, new ResonantLayout(GlobalPos.of(level.dimension(), first),
                BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE), Direction.NORTH, Direction.UP, 0, List.of()));
        provider.getAdapterInv().setItemDirect(0, core);
        if (wireless) {
            h.assertTrue(provider.addOrUpdateConnection(level.dimension(), first, Direction.UP), "first resonant link");
            h.assertTrue(provider.addOrUpdateConnection(level.dimension(), second, Direction.UP), "second resonant link");
            h.assertTrue(!provider.addOrUpdateConnection(level.dimension(), pos.south(), Direction.UP), "unmatched anchor rejected");
        }
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        h.assertTrue(logic.getMachineRecognition().count() == 2, "both matching anchors recognized without a recipe or port layout");
        h.assertTrue(logic.getMachineRecognition().names().getString().equals(
                Blocks.FURNACE.getName().getString() + ", " + Blocks.FURNACE.getName().getString()), "names identify matching anchors");
        level.setBlockAndUpdate(first, Blocks.AIR.defaultBlockState());
        h.assertTrue(logic.getMachineRecognition().count() == 1, "removed first anchor clears immediately");
        level.setBlockAndUpdate(second, Blocks.CHEST.defaultBlockState());
        h.assertTrue(logic.getMachineRecognition().count() == 0, "replaced anchor and unrelated chest are excluded");
        h.succeed();
    }
}
