package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Provider-owned attribution for one exact result expected in a machine slot. */
public final class SlotHarvestOwnership {

    static final long STATE_TTL_TICKS = 20L * 60L * 60L * 6L;

    /** Marker tick of pre-TTL (three-part) payloads; never stored going forward. */
    static final long LEGACY_CREATED_TICK = Long.MIN_VALUE;

    private SlotHarvestOwnership() {
    }

    public static void store(ServerLevel level, BlockPos mainPos, AdapterPersistentScope scope,
                             String stateKey, ItemStack expectedOutput, ResourceLocation recipeId) {
        if (expectedOutput.isEmpty()) {
            return;
        }
        scope.setState(mainPos, qualifiedKey(level, stateKey), encode(
                level.dimension().location(), level.getGameTime(), expectedOutput, recipeId));
    }

    @Nullable
    public static State load(ServerLevel level, BlockPos mainPos,
                             AdapterPersistentScope scope, String stateKey) {
        return loadStored(scope, mainPos, qualifiedKey(level, stateKey),
                level.dimension().location(), level.getGameTime());
    }

    /**
     * Decodes one payload and upgrades pre-TTL (three-part) payloads in place.
     * Their true creation tick is unknowable, so the clock restarts at the
     * first evaluation after this change instead of freezing the claim (and
     * with it the machine's dispatch) forever; from then on the normal TTL and
     * the idle-and-empty cleanup in {@link #blocksDispatch} apply.
     */
    @Nullable
    static State loadStored(AdapterPersistentScope scope, BlockPos mainPos, String storageKey,
                            ResourceLocation dimension, long gameTime) {
        var encoded = scope.getState(mainPos, storageKey);
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        var state = decode(encoded, dimension);
        if (state == null || state.createdTick() != LEGACY_CREATED_TICK) {
            return state;
        }
        var migrated = new State(state.recipeId(), state.expectedOutput(), Math.max(0L, gameTime));
        scope.setState(mainPos, storageKey, encode(dimension, migrated.createdTick(),
                migrated.expectedOutput(), migrated.recipeId()));
        return migrated;
    }

    public static void clear(ServerLevel level, BlockPos mainPos,
                             AdapterPersistentScope scope, String stateKey) {
        scope.clearState(mainPos, qualifiedKey(level, stateKey));
        scope.clearState(mainPos, stateKey);
    }

    public static String qualifiedKey(ServerLevel level, String stateKey) {
        return stateKey + "@" + level.dimension().location();
    }

    public static boolean expired(ServerLevel level, State state) {
        return expired(level.getGameTime(), state);
    }

    static boolean expired(long gameTime, State state) {
        // Legacy-marker payloads stay unexpired here on purpose: they are
        // upgraded to a real clock by load/blocksDispatch before any caller
        // can reach this with a negative tick.
        if (state.createdTick() < 0L) {
            return false;
        }
        long age = gameTime - state.createdTick();
        return age < 0 || age > STATE_TTL_TICKS;
    }

    /**
     * True when a stored claim still forbids a new dispatch. An expired or
     * abandoned claim is cleared only when the machine is provably idle and
     * empty; malformed or live claims always block.
     */
    public static boolean blocksDispatch(ServerLevel level, BlockPos mainPos,
                                         AdapterPersistentScope scope, String stateKey,
                                         boolean machineIdleEmpty) {
        var qualified = qualifiedKey(level, stateKey);
        var encoded = scope.getState(mainPos, qualified);
        if (encoded == null || encoded.isBlank()) {
            if (scope.getState(mainPos, stateKey) == null) {
                return false;
            }
            if (!machineIdleEmpty) {
                return true;
            }
            clear(level, mainPos, scope, stateKey);
            return false;
        }
        var state = loadStored(scope, mainPos, qualified,
                level.dimension().location(), level.getGameTime());
        if (state == null) {
            return true;
        }
        if (!expired(level, state)) {
            return true;
        }
        if (!machineIdleEmpty) {
            return true;
        }
        clear(level, mainPos, scope, stateKey);
        return false;
    }

    static String encode(ResourceLocation dimension, long createdTick,
                         ItemStack expected, ResourceLocation recipeId) {
        var tag = new CompoundTag();
        expected.save(tag);
        var stack = Base64.getUrlEncoder().withoutPadding().encodeToString(
                tag.toString().getBytes(StandardCharsets.UTF_8));
        return dimension + "|" + recipeId + "|" + createdTick + "|" + stack;
    }

    @Nullable
    static State decode(String encoded, ResourceLocation dimension) {
        var parts = encoded.split("\\|", -1);
        if (parts.length != 3 && parts.length != 4) {
            return null;
        }
        if (!dimension.toString().equals(parts[0])) {
            return null;
        }
        try {
            var recipeId = ResourceLocation.tryParse(parts[1]);
            long createdTick;
            String stackPart;
            if (parts.length == 4) {
                createdTick = Long.parseLong(parts[2]);
                stackPart = parts[3];
                if (createdTick < 0) {
                    return null;
                }
            } else {
                createdTick = LEGACY_CREATED_TICK;
                stackPart = parts[2];
            }
            var nbt = new String(Base64.getUrlDecoder().decode(stackPart), StandardCharsets.UTF_8);
            var expected = ItemStack.of(TagParser.parseTag(nbt));
            return recipeId == null || expected.isEmpty()
                    ? null
                    : new State(recipeId, expected, createdTick);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record State(ResourceLocation recipeId, ItemStack expectedOutput, long createdTick) {
        public State {
            expectedOutput = expectedOutput.copy();
        }

        public int attributableCount(ItemStack current) {
            if (!ItemStack.isSameItemSameTags(current, expectedOutput)) {
                return 0;
            }
            return Math.min(current.getCount(), expectedOutput.getCount());
        }
    }
}
