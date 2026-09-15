package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class SlotHarvestOwnershipTest {

    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");
    private static final ResourceLocation RECIPE = new ResourceLocation("test", "craft");

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void roundTripsTimedStateIncludingNbt() {
        var expected = taggedDiamond(4, "mark");
        var decoded = SlotHarvestOwnership.decode(
                SlotHarvestOwnership.encode(OVERWORLD, 42L, expected, RECIPE), OVERWORLD);

        assertNotNull(decoded);
        assertEquals(RECIPE, decoded.recipeId());
        assertEquals(42L, decoded.createdTick());
        assertEquals(4, decoded.expectedOutput().getCount());
        assertTrue(ItemStack.isSameItemSameTags(expected, decoded.expectedOutput()));
    }

    @Test
    void rejectsWrongDimension() {
        var encoded = SlotHarvestOwnership.encode(OVERWORLD, 1L, new ItemStack(Items.DIAMOND), RECIPE);
        assertNull(SlotHarvestOwnership.decode(encoded, NETHER));
    }

    @Test
    void attributableCountRequiresSameItemAndTagsAndCapsToExpected() {
        var state = new SlotHarvestOwnership.State(RECIPE, taggedDiamond(3, "mark"), 10L);

        assertEquals(3, state.attributableCount(taggedDiamond(8, "mark")));
        assertEquals(0, state.attributableCount(taggedDiamond(3, "other")));
        assertEquals(0, state.attributableCount(new ItemStack(Items.DIAMOND, 3)));
        assertEquals(0, state.attributableCount(new ItemStack(Items.IRON_INGOT, 3)));
    }

    @Test
    void timedStateExpiresAfterTtlAndOnNegativeAge() {
        var state = new SlotHarvestOwnership.State(RECIPE, new ItemStack(Items.DIAMOND), 10L);

        assertFalse(SlotHarvestOwnership.expired(10L, state));
        assertFalse(SlotHarvestOwnership.expired(10L + SlotHarvestOwnership.STATE_TTL_TICKS, state));
        assertTrue(SlotHarvestOwnership.expired(10L + SlotHarvestOwnership.STATE_TTL_TICKS + 1L, state));
        assertTrue(SlotHarvestOwnership.expired(9L, state));
    }

    @Test
    void legacyThreePartStateNeverExpiresByTick() {
        var tag = new CompoundTag();
        new ItemStack(Items.DIAMOND, 2).save(tag);
        var stack = Base64.getUrlEncoder().withoutPadding().encodeToString(
                tag.toString().getBytes(StandardCharsets.UTF_8));
        var decoded = SlotHarvestOwnership.decode(OVERWORLD + "|" + RECIPE + "|" + stack, OVERWORLD);

        assertNotNull(decoded);
        assertTrue(decoded.createdTick() < 0L);
        assertFalse(SlotHarvestOwnership.expired(Long.MAX_VALUE, decoded));
        assertEquals(2, decoded.expectedOutput().getCount());
    }

    private static ItemStack taggedDiamond(int count, String mark) {
        var stack = new ItemStack(Items.DIAMOND, count);
        stack.getOrCreateTag().putString("mark", mark);
        return stack;
    }
}
