package com.moakiee.ae2lt.packaged.logic.multiblock;

import net.minecraft.core.BlockPos;

import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;

/**
 * Provider-owned state keyed by target position and a namespaced adapter key.
 * The provider persists this state across chunk unloads and restarts. Store ownership
 * here because third-party machines may omit Forge persistent data when saving.
 *
 * {@link #NOOP} supplies no persistence for tests and legacy callers.
 */
public interface AdapterPersistentScope {

    AdapterPersistentScope NOOP = new AdapterPersistentScope() {
        @Override
        public void setFlag(BlockPos targetPos, String key) {
        }

        @Override
        public boolean hasFlag(BlockPos targetPos, String key) {
            return false;
        }

        @Override
        public void clearFlag(BlockPos targetPos, String key) {
        }
    };

    /** Output-return policy; legacy callers default to automatic harvesting. */
    default ReturnMode getReturnMode() {
        return ReturnMode.AUTO;
    }

    /** Marks {@code (targetPos, key)} as true. Idempotent. */
    void setFlag(BlockPos targetPos, String key);

    /** Whether the flag is currently set for this target and key. */
    boolean hasFlag(BlockPos targetPos, String key);

    /** Marks {@code (targetPos, key)} as false. Idempotent. */
    void clearFlag(BlockPos targetPos, String key);

    /** Stores a small adapter-owned value. Implementations may persist it across restarts. */
    default void setState(BlockPos targetPos, String key, String value) {
    }

    /** Returns the stored adapter-owned value, or {@code null} when none exists. */
    default String getState(BlockPos targetPos, String key) {
        return null;
    }

    /** Removes one adapter-owned value. Idempotent. */
    default void clearState(BlockPos targetPos, String key) {
    }

    /** Removes all adapter flags and values associated with a target position. */
    default void clearFlagsForTarget(BlockPos targetPos) {
    }
}
