package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

import net.minecraft.core.BlockPos;
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

    // decode/expired preserve the conservative marker semantics; loadStored is
    // what upgrades legacy payloads to a bounded clock before callers act.
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

    @Test
    void loadStoredMigratesLegacyPayloadOnceAndBoundsItsBlock() {
        var scope = new MapScope();
        var legacy = legacyThreePartPayload(2);

        var migrated = SlotHarvestOwnership.loadStored(scope, POS, KEY, OVERWORLD, 1000L);
        assertNull(migrated);

        scope.setState(POS, KEY, legacy);
        migrated = SlotHarvestOwnership.loadStored(scope, POS, KEY, OVERWORLD, 1000L);
        assertNotNull(migrated);
        assertEquals(1000L, migrated.createdTick());
        assertEquals(2, migrated.expectedOutput().getCount());

        // The stored payload was rewritten in place, so the migration is
        // idempotent across reloads instead of restarting the clock each load.
        var stored = scope.getState(POS, KEY);
        assertNotEquals(legacy, stored);
        var reloaded = SlotHarvestOwnership.loadStored(scope, POS, KEY, OVERWORLD, 5000L);
        assertNotNull(reloaded);
        assertEquals(1000L, reloaded.createdTick());
        assertEquals(stored, scope.getState(POS, KEY));

        // Once migrated, the normal TTL applies: the claim can now expire and
        // blocksDispatch's idle-and-empty cleanup can release the machine.
        assertFalse(SlotHarvestOwnership.expired(1000L + SlotHarvestOwnership.STATE_TTL_TICKS, migrated));
        assertTrue(SlotHarvestOwnership.expired(1000L + SlotHarvestOwnership.STATE_TTL_TICKS + 1L, migrated));
    }

    @Test
    void loadStoredLeavesTimedPayloadsUntouched() {
        var scope = new MapScope();
        var timed = SlotHarvestOwnership.encode(OVERWORLD, 42L, new ItemStack(Items.DIAMOND, 3), RECIPE);
        scope.setState(POS, KEY, timed);

        var state = SlotHarvestOwnership.loadStored(scope, POS, KEY, OVERWORLD, 1000L);

        assertNotNull(state);
        assertEquals(42L, state.createdTick());
        assertEquals(timed, scope.getState(POS, KEY));
    }

    @Test
    void loadStoredRejectsForeignDimensionPayloadsFailClosed() {
        var scope = new MapScope();
        scope.setState(POS, KEY, legacyThreePartPayload(2));

        var state = SlotHarvestOwnership.loadStored(scope, POS, KEY, NETHER, 1000L);

        assertNull(state);
        assertEquals(legacyThreePartPayload(2), scope.getState(POS, KEY));
    }

    private static String legacyThreePartPayload(int count) {
        var tag = new CompoundTag();
        new ItemStack(Items.DIAMOND, count).save(tag);
        var stack = Base64.getUrlEncoder().withoutPadding().encodeToString(
                tag.toString().getBytes(StandardCharsets.UTF_8));
        return OVERWORLD + "|" + RECIPE + "|" + stack;
    }

    private static final BlockPos POS = new BlockPos(1, 2, 3);
    private static final String KEY = "harvest";

    private static final class MapScope implements AdapterPersistentScope {
        final Map<String, String> states = new HashMap<>();

        @Override
        public void setFlag(BlockPos pos, String key) {
        }

        @Override
        public boolean hasFlag(BlockPos pos, String key) {
            return false;
        }

        @Override
        public void clearFlag(BlockPos pos, String key) {
        }

        @Override
        public void setState(BlockPos pos, String key, String value) {
            states.put(key, value);
        }

        @Override
        public String getState(BlockPos pos, String key) {
            return states.get(key);
        }

        @Override
        public void clearState(BlockPos pos, String key) {
            states.remove(key);
        }
    }

    private static ItemStack taggedDiamond(int count, String mark) {
        var stack = new ItemStack(Items.DIAMOND, count);
        stack.getOrCreateTag().putString("mark", mark);
        return stack;
    }
}
