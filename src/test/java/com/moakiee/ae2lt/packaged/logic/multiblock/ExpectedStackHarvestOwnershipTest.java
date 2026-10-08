package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

class ExpectedStackHarvestOwnershipTest {

    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void roundTripsRemainingStacksAndTick() {
        var iron = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4);
        var gold = new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2);
        var decoded = ExpectedStackHarvestOwnership.decode(
                ExpectedStackHarvestOwnership.encode(OVERWORLD, 99L, List.of(iron, gold)), OVERWORLD);

        assertNotNull(decoded);
        assertEquals(99L, decoded.createdTick());
        assertEquals(2, decoded.remaining().size());
        assertEquals(iron.what(), decoded.remaining().get(0).what());
        assertEquals(4, decoded.remaining().get(0).amount());
        assertEquals(gold.what(), decoded.remaining().get(1).what());
        assertEquals(2, decoded.remaining().get(1).amount());
    }

    @Test
    void rejectsWrongDimensionAndLegacyTickToken() {
        var iron = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1);
        var encoded = ExpectedStackHarvestOwnership.encode(OVERWORLD, 1L, List.of(iron));
        assertNull(ExpectedStackHarvestOwnership.decode(encoded, new ResourceLocation("minecraft", "the_nether")));
        assertNull(ExpectedStackHarvestOwnership.decode("12345", OVERWORLD));
    }

    @Test
    void claimableCountCapsToRemainingAndDebitPersistsLeftover() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var remaining = new ArrayList<>(List.of(
                new GenericStack(iron, 3),
                new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 5)));

        assertEquals(3, ExpectedStackHarvestOwnership.claimableCount(remaining, iron, 8));
        assertEquals(0, ExpectedStackHarvestOwnership.claimableCount(
                remaining, AEItemKey.of(Items.DIAMOND), 8));

        ExpectedStackHarvestOwnership.debit(remaining, iron, 2);
        var leftover = ExpectedStackHarvestOwnership.compact(remaining);
        assertEquals(2, leftover.size());
        assertEquals(1, leftover.get(0).amount());
        assertEquals(5, leftover.get(1).amount());

        ExpectedStackHarvestOwnership.debit(remaining, iron, 1);
        leftover = ExpectedStackHarvestOwnership.compact(remaining);
        assertEquals(1, leftover.size());
        assertEquals(AEItemKey.of(Items.GOLD_INGOT), leftover.get(0).what());
    }

    @Test
    void expiresAfterTtl() {
        var claim = new ExpectedStackHarvestOwnership.Claim(
                10L, List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)));

        assertFalse(ExpectedStackHarvestOwnership.expired(10L, claim));
        assertTrue(ExpectedStackHarvestOwnership.expired(
                10L + ExpectedStackHarvestOwnership.STATE_TTL_TICKS + 1L, claim));
    }

    @Test
    void claimableCountStaysWithinIntAvailabilityWhenRemainingExceedsLongRange() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var remaining = List.of(new GenericStack(iron, Long.MAX_VALUE), new GenericStack(iron, 1));

        assertEquals(Integer.MAX_VALUE,
                ExpectedStackHarvestOwnership.claimableCount(remaining, iron, Integer.MAX_VALUE));
        assertEquals(0, ExpectedStackHarvestOwnership.claimableCount(remaining, iron, 0));
        assertEquals(0, ExpectedStackHarvestOwnership.claimableCount(remaining, iron, -1));
    }
}
