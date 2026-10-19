package com.moakiee.ae2lt.packaged.gametest;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import com.moakiee.ae2lt.api.patternprovider.WirelessPatternProviderHost;
import com.moakiee.ae2lt.logic.wireless.support.WirelessConnectorTargetHelper;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;

@GameTestHolder("ae2ltpp_core")
@PrefixGameTestTemplate(false)
public final class WirelessTargetAdmissionTests {
    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void ordinaryTargetsAndNormalProvidersAreRejected(GameTestHelper helper) {
        var level = helper.getLevel();
        var hostRelative = new BlockPos(2, 1, 2);
        var targetRelative = new BlockPos(4, 1, 2);
        helper.setBlock(hostRelative, PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(helper.absolutePos(hostRelative));
        WirelessPatternProviderHost host = provider;
        var target = helper.absolutePos(targetRelative);
        helper.setBlock(targetRelative, Blocks.CHEST);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!host.acceptsWirelessTarget(level, target), "Missing core accepted a target");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!host.addOrUpdateConnection(level.dimension(), target, Direction.UP),
                "Direct connection bypassed missing-core admission");
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.OCCULTISM_SPIRIT_FIRE_ADAPTER.get()));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!host.acceptsWirelessTarget(level, target), "Core accepted an unrelated block entity");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!host.addOrUpdateConnection(level.dimension(), target, Direction.UP),
                "Direct connection bypassed core compatibility");
        helper.setBlock(targetRelative, Blocks.STONE);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!host.acceptsWirelessTarget(level, target), "Core accepted an ordinary block");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!host.acceptsWirelessTarget(level, new BlockPos(30000000, 64, 30000000)),
                "Unloaded target was accepted");
        helper.setBlock(hostRelative, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(helper.absolutePos(hostRelative));
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.OCCULTISM_SPIRIT_FIRE_ADAPTER.get()));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!provider.acceptsWirelessTarget(level, target), "Normal provider accepted a wireless target");
        helper.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void blockOnlyTargetsRequireMatchingCoreAndSurviveCoreSwap(GameTestHelper helper) {
        var spiritFireId = new ResourceLocation("occultism", "spirit_fire");
        if (!ForgeRegistries.BLOCKS.containsKey(spiritFireId)) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        var hostRelative = new BlockPos(2, 1, 2);
        var targetRelative = new BlockPos(4, 1, 2);
        var secondRelative = targetRelative.east();
        var spiritFire = ForgeRegistries.BLOCKS.getValue(spiritFireId);
        helper.setBlock(hostRelative, PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(targetRelative.below(), Blocks.STONE);
        helper.setBlock(secondRelative.below(), Blocks.STONE);
        helper.setBlock(targetRelative, spiritFire.defaultBlockState());
        helper.setBlock(secondRelative, spiritFire.defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(helper.absolutePos(hostRelative));
        WirelessPatternProviderHost host = provider;
        var target = helper.absolutePos(targetRelative);
        var second = helper.absolutePos(secondRelative);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(level.getBlockState(target).is(spiritFire), "Unsupported spirit fire disappeared");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(MultiblockAdapterRegistry.find(level, target, null) != null,
                "Spirit fire adapter is not available");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!provider.isRemoved() && provider.isWirelessProvider() && provider.getLevel() == level,
                "Wireless test provider is not attached to the server level");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(level.getBlockEntity(target) == null, "Spirit fire unexpectedly has a block entity");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!host.acceptsWirelessTarget(level, target), "Missing core accepted spirit fire");
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.ARS_APPARATUS_ADAPTER.get()));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!host.acceptsWirelessTarget(level, target), "Wrong core accepted spirit fire");
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.OCCULTISM_SPIRIT_FIRE_ADAPTER.get()));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(host.acceptsWirelessTarget(level, target), "Matching core rejected spirit fire");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(WirelessConnectorTargetHelper.collectTargets(level, target, true, 1024,
                        pos -> host.acceptsWirelessTarget(level, pos)).equals(Set.of(target, second)),
                "Upstream contiguous collector did not admit both block-only targets");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(host.addOrUpdateConnection(level.dimension(), target, Direction.UP),
                "Matching core could not connect spirit fire");
        provider.getAdapterInv().setItemDirect(0, ItemStack.EMPTY);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(provider.clearInvalidConnections() == 0 && host.getConnections().size() == 1,
                "Core removal erased an existing connection");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!host.addOrUpdateConnection(level.dimension(), second, Direction.UP),
                "Core removal allowed a new connection");
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.ARS_APPARATUS_ADAPTER.get()));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(provider.clearInvalidConnections() == 0 && host.getConnections().size() == 1,
                "Core swap erased an existing connection");
        helper.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn")
    public static void budgetedPruningClearsOnlyRemovedTargetState(GameTestHelper helper) {
        var spiritFireId = new ResourceLocation("occultism", "spirit_fire");
        if (!ForgeRegistries.BLOCKS.containsKey(spiritFireId)) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        var hostRelative = new BlockPos(2, 1, 2);
        var targetRelatives = List.of(new BlockPos(4, 1, 2), new BlockPos(6, 1, 2), new BlockPos(8, 1, 2));
        var spiritFire = ForgeRegistries.BLOCKS.getValue(spiritFireId);
        helper.setBlock(hostRelative, PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(helper.absolutePos(hostRelative));
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.OCCULTISM_SPIRIT_FIRE_ADAPTER.get()));
        for (var relative : targetRelatives) {
            helper.setBlock(relative.below(), Blocks.STONE);
            helper.setBlock(relative, spiritFire.defaultBlockState());
            var target = helper.absolutePos(relative);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(provider.addOrUpdateConnection(level.dimension(), target, Direction.UP),
                    "Could not connect pruning fixture");
            provider.setFlag(target, "pruning-test");
            provider.setState(target, "pruning-test", "owned");
        }
        var first = helper.absolutePos(targetRelatives.get(0));
        var retained = helper.absolutePos(targetRelatives.get(1));
        var last = helper.absolutePos(targetRelatives.get(2));
        helper.setBlock(targetRelatives.get(0), Blocks.STONE);
        helper.setBlock(targetRelatives.get(2), Blocks.STONE);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(provider.pruneInvalidConnections(1) == 1 && provider.getConnections().size() == 2,
                "Pruning exceeded its one-target budget");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!provider.hasFlag(first, "pruning-test") && provider.getState(first, "pruning-test") == null,
                "Removed target retained ownership state");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(provider.hasFlag(last, "pruning-test") && "owned".equals(provider.getState(last, "pruning-test")),
                "Unscanned target lost ownership state");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(provider.pruneInvalidConnections(1) == 0 && provider.getConnections().size() == 2,
                "Cursor skipped a retained target");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(provider.pruneInvalidConnections(1) == 1 && provider.getConnections().size() == 1,
                "Cursor failed to reach the last invalid target");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!provider.hasFlag(last, "pruning-test") && provider.getState(last, "pruning-test") == null,
                "Last removed target retained ownership state");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(provider.hasFlag(retained, "pruning-test")
                        && "owned".equals(provider.getState(retained, "pruning-test")),
                "Valid target lost ownership state");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(provider.clearInvalidConnections() == 0 && provider.getConnections().get(0).pos().equals(retained),
                "Manual cleanup removed a valid block-only target");
        helper.succeed();
    }
}
