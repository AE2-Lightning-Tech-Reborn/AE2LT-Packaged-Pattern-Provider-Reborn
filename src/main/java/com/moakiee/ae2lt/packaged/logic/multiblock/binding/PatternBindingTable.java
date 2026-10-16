package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;

/**
 * Lazy cache keyed by pattern identity, since pattern updates replace instances.
 * Matched entries expire after 200 ticks; unmatched entries after 40 ticks.
 * Structure changes and recipe reloads also invalidate bindings.
 */
public final class PatternBindingTable {

    private static final long NEGATIVE_BINDING_TTL_TICKS = 40;
    private static final long POSITIVE_BINDING_TTL_TICKS = 200;
    private static final AtomicLong RECIPE_GENERATION = new AtomicLong();

    private long observedRecipeGeneration = RECIPE_GENERATION.get();

    /** Invalidates every provider cache after a server recipe/data reload. */
    public static void invalidateAllForRecipeReload() {
        RECIPE_GENERATION.incrementAndGet();
    }

    // The per-provider lock protects map access and atomic expiry/removal.
    private final Map<IPatternDetails, PatternBinding> bindings = new IdentityHashMap<>();
    private final Object lock = new Object();

    @Nullable
    public PatternBinding get(IPatternDetails pattern) {
        synchronized (lock) {
            checkRecipeGeneration();
            return bindings.get(pattern);
        }
    }

    @Nullable
    public PatternBinding getFresh(IPatternDetails pattern, long gameTick) {
        synchronized (lock) {
            checkRecipeGeneration();
            var binding = bindings.get(pattern);
            if (binding == null) {
                return null;
            }
            long ttl = binding.isMatched()
                    ? POSITIVE_BINDING_TTL_TICKS
                    : NEGATIVE_BINDING_TTL_TICKS;
            long elapsed;
            try {
                elapsed = Math.subtractExact(gameTick, binding.computedAtTick());
            } catch (ArithmeticException overflow) {
                bindings.remove(pattern);
                return null;
            }
            if (elapsed < 0 || elapsed >= ttl) {
                bindings.remove(pattern);
                return null;
            }
            return binding;
        }
    }

    public void put(IPatternDetails pattern, PatternBinding binding) {
        synchronized (lock) {
            checkRecipeGeneration();
            bindings.put(pattern, binding);
        }
    }

    public void invalidate(IPatternDetails pattern) {
        synchronized (lock) {
            bindings.remove(pattern);
        }
    }

    public void invalidateAll() {
        synchronized (lock) {
            bindings.clear();
        }
    }

    public int size() {
        synchronized (lock) {
            checkRecipeGeneration();
            return bindings.size();
        }
    }

    /** Snapshot for debug; not part of the hot path. */
    public Map<IPatternDetails, PatternBinding> snapshot() {
        synchronized (lock) {
            checkRecipeGeneration();
            return new IdentityHashMap<>(bindings);
        }
    }

    /** Called with lock held; never carry old entries into a new generation. */
    private void checkRecipeGeneration() {
        long generation = RECIPE_GENERATION.get();
        if (observedRecipeGeneration != generation) {
            bindings.clear();
            observedRecipeGeneration = generation;
        }
    }
}
