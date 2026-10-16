package com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NaturesAuraCraftStateTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    private static BlockEntity machine() {
        var be = new BlockEntity(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) {};
        NaturesAuraCraftState.begin(be, BlockPos.ZERO);
        return be;
    }

    @Test
    void pollingAnUnfinishedAltarDoesNotDiscardOwnership() {
        var be = machine();
        assertTrue(NaturesAuraCraftState.drain(be, true, null).isEmpty());
        assertTrue(NaturesAuraCraftState.active(be));
    }

    @Test
    void offeringReleasesOwnershipAfterBonusQueueFinishes() {
        var be = machine();
        var output = new ItemStack(Items.DIAMOND, 2);
        NaturesAuraCraftState.storeExpectedOutput(be, output);
        assertTrue(NaturesAuraCraftState.capture(be, output));
        assertEquals(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 2)),
                NaturesAuraCraftState.drain(be, false, null));
        assertTrue(NaturesAuraCraftState.active(be));
        assertTrue(NaturesAuraCraftState.drain(be, true, null).isEmpty());
        assertFalse(NaturesAuraCraftState.active(be));
        assertTrue(NaturesAuraCraftState.drain(be, true, null).isEmpty());
    }

    @Test
    void mismatchedFilterRetainsResultsAcrossSaveAndLoad() {
        var be = machine();
        var output = new ItemStack(Items.DIAMOND, 3);
        output.getOrCreateTag().putString("test", "paid");
        NaturesAuraCraftState.storeExpectedOutput(be, output);
        assertFalse(NaturesAuraCraftState.capture(be, new ItemStack(Items.DIAMOND, 3)));
        assertTrue(NaturesAuraCraftState.capture(be, output));
        var denied = new AllowedOutputFilter();
        assertTrue(NaturesAuraCraftState.drain(be, true, denied).isEmpty());
        var restored = machine();
        restored.load(be.saveWithFullMetadata());
        var allowed = new AllowedOutputFilter();
        allowed.allowStrict(AEItemKey.of(output));
        assertEquals(List.of(new GenericStack(AEItemKey.of(output), 3)),
                NaturesAuraCraftState.drain(restored, true, allowed));
        assertFalse(NaturesAuraCraftState.active(restored));
    }

    @Test
    void storedResultsFromBeforeCompletionFlagStillReleaseOwnership() {
        var be = machine();
        assertTrue(NaturesAuraCraftState.capture(be, new ItemStack(Items.DIAMOND)));
        be.getPersistentData().getCompound(NaturesAuraCraftState.KEY).remove("completed");
        assertEquals(1, NaturesAuraCraftState.drain(be, true, null).size());
        assertFalse(NaturesAuraCraftState.active(be));
    }

    @Test
    void paidOfferingStartRoundTripsAndCanOnlyBeConsumedOnce() {
        var be = machine();
        var start = new ItemStack(Items.GOLD_INGOT);
        start.getOrCreateTag().putString("test", "start");
        NaturesAuraCraftState.storeOfferingStart(be, start);
        var restored = machine();
        restored.load(be.saveWithFullMetadata());
        assertTrue(ItemStack.matches(start, NaturesAuraCraftState.offeringStart(restored)));
        NaturesAuraCraftState.consumeOfferingStart(restored);
        NaturesAuraCraftState.consumeOfferingStart(restored);
        assertTrue(NaturesAuraCraftState.offeringStart(restored).isEmpty());
    }

    @Test
    void cancellingCannotDiscardAnAlreadyCapturedProduct() {
        var be = machine();
        assertTrue(NaturesAuraCraftState.capture(be, new ItemStack(Items.DIAMOND)));
        NaturesAuraCraftState.cancelWithoutResult(be);
        assertTrue(NaturesAuraCraftState.active(be));
        assertEquals(1, NaturesAuraCraftState.drain(be, true, null).size());
    }
}
