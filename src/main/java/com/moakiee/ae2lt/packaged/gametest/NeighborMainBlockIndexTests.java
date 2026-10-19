package com.moakiee.ae2lt.packaged.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import com.moakiee.ae2lt.packaged.logic.multiblock.NeighborMainBlockIndex;

@GameTestHolder("ae2ltpp_core")
@PrefixGameTestTemplate(false)
public final class NeighborMainBlockIndexTests {
    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void blockOnlyNeighborSnapshotsSurviveInvalidation(GameTestHelper helper) {
        var spiritFireId = new ResourceLocation("occultism", "spirit_fire");
        if (!ForgeRegistries.BLOCKS.containsKey(spiritFireId)) {
            helper.succeed();
            return;
        }
        var originRelative = new BlockPos(4, 1, 4);
        var targetRelative = originRelative.east();
        var level = helper.getLevel();
        var spiritFire = ForgeRegistries.BLOCKS.getValue(spiritFireId);
        helper.setBlock(targetRelative.below(), Blocks.STONE);
        helper.setBlock(targetRelative, spiritFire.defaultBlockState());
        var index = new NeighborMainBlockIndex(helper.absolutePos(originRelative));
        var snapshot = index.adapterFaces(level);
        var previous = index.getAdapter(Direction.EAST);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(snapshot.equals(List.of(Direction.EAST)) && previous != null,
                "Block-only neighbor was not indexed");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(level.getBlockEntity(helper.absolutePos(targetRelative)) == null,
                "Block-only test fixture unexpectedly has a block entity");
        for (int lookup = 0; lookup < 1000; lookup++) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(index.adapterFaces(level) == snapshot, "Cache hit allocated a new snapshot");
        }
        boolean immutable = false;
        try {
            snapshot.add(Direction.WEST);
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(immutable, "Caller could mutate the cached snapshot");
        helper.setBlock(targetRelative, Blocks.STONE);
        index.invalidate();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(index.getAdapter(Direction.EAST) == previous,
                "Invalidation discarded the observation needed for ownership cleanup");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(index.adapterFaces(level).isEmpty() && index.getAdapter(Direction.EAST) == null,
                "Invalidation did not refresh the removed neighbor");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(snapshot.equals(List.of(Direction.EAST)), "Old snapshot changed during rebuild");
        helper.setBlock(targetRelative, spiritFire.defaultBlockState());
        index.invalidate();
        var restored = index.adapterFaces(level);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(restored.equals(List.of(Direction.EAST)) && index.getAdapter(Direction.EAST) != null,
                "Replaced block-only neighbor was not rediscovered");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(index.adapterFaces(level) == restored, "Rebuilt snapshot was not reused");
        helper.succeed();
    }
}
