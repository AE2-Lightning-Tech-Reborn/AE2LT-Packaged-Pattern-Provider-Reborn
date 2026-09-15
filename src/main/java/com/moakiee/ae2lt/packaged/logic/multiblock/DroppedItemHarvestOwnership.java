package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

/** Provider-owned attribution for one expected dropped-item output. */
public final class DroppedItemHarvestOwnership {

    private static final long STATE_TTL = 20L * 60L * 60L * 6L;

    private DroppedItemHarvestOwnership() {
    }

    public static Snapshot capture(ServerLevel level, AABB bounds, ItemStack expectedOutput) {
        var counts = new HashMap<UUID, Integer>();
        for (var entity : matchingEntities(level, bounds, expectedOutput)) {
            counts.put(entity.getUUID(), entity.getItem().getCount());
        }
        return new Snapshot(counts);
    }

    public static void store(ServerLevel level, BlockPos mainPos, AdapterPersistentScope scope,
                             String stateKey, Snapshot snapshot, ItemStack expectedOutput,
                             ResourceLocation recipeId) {
        if (expectedOutput.isEmpty()) {
            return;
        }
        scope.setState(mainPos, qualifiedKey(level, stateKey), encode(
                level.dimension().location(), level.getGameTime(), snapshot, expectedOutput, recipeId));
    }

    @Nullable
    public static HarvestState load(ServerLevel level, BlockPos mainPos,
                                    AdapterPersistentScope scope, String stateKey) {
        var encoded = scope.getState(mainPos, qualifiedKey(level, stateKey));
        return encoded == null || encoded.isBlank()
                ? null
                : decode(encoded, level.dimension().location());
    }

    public static boolean hasMatchingExpectedOutput(ServerLevel level, AABB bounds,
                                                    HarvestState state) {
        return !matchingEntities(level, bounds, state.expectedOutput()).isEmpty();
    }

    public static List<GenericStack> collectExpectedOutput(ServerLevel level, AABB bounds,
                                                            HarvestState state) {
        var expected = state.expectedOutput();
        var attributable = new ArrayList<AttributableEntity>();
        long available = 0;
        for (var entity : matchingEntities(level, bounds, expected)) {
            int baseline = state.preexistingEntityCounts().getOrDefault(entity.getUUID(), 0);
            int delta = Math.max(0, entity.getItem().getCount() - baseline);
            if (delta > 0) {
                attributable.add(new AttributableEntity(entity, delta));
                available += delta;
            }
        }
        if (available < expected.getCount()) {
            return List.of();
        }

        attributable.sort(Comparator.comparing(
                entry -> state.preexistingEntityCounts().containsKey(entry.entity().getUUID())));
        int remaining = expected.getCount();
        for (var entry : attributable) {
            if (remaining == 0) {
                break;
            }
            var entity = entry.entity();
            var current = entity.getItem();
            int taken = Math.min(remaining, entry.attributableCount());
            if (taken == current.getCount()) {
                entity.discard();
            } else {
                var remainder = current.copy();
                remainder.shrink(taken);
                entity.setItem(remainder);
            }
            remaining -= taken;
        }
        return remaining == 0
                ? List.of(new GenericStack(AEItemKey.of(expected), expected.getCount()))
                : List.of();
    }

    public static void clear(ServerLevel level, BlockPos mainPos,
                             AdapterPersistentScope scope, String stateKey) {
        scope.clearState(mainPos, qualifiedKey(level, stateKey));
        scope.clearState(mainPos, stateKey);
    }

    public static String qualifiedKey(ServerLevel level, String stateKey) {
        return stateKey + "@" + level.dimension().location();
    }

    public static boolean expired(ServerLevel level, HarvestState state) {
        long age = level.getGameTime() - state.createdTick();
        return age < 0 || age > STATE_TTL;
    }

    private static String encode(ResourceLocation dimension, long createdTick, Snapshot snapshot,
                                 ItemStack expectedOutput, ResourceLocation recipeId) {
        var encoded = new StringBuilder(dimension.toString())
                .append('|').append(createdTick)
                .append('|').append(expectedOutput.getCount())
                .append('|').append(encodeStack(expectedOutput))
                .append('|').append(recipeId);
        snapshot.entityCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> encoded.append('|').append(entry.getKey())
                        .append('=').append(entry.getValue()));
        return encoded.toString();
    }

    @Nullable
    private static HarvestState decode(String encoded, ResourceLocation dimension) {
        var parts = encoded.split("\\|", -1);
        if (parts.length < 5 || !dimension.toString().equals(parts[0])) {
            return null;
        }
        try {
            long createdTick = Long.parseLong(parts[1]);
            int expectedCount = Integer.parseInt(parts[2]);
            var expected = decodeStack(parts[3]);
            var recipeId = ResourceLocation.tryParse(parts[4]);
            if (createdTick < 0 || expectedCount <= 0 || expected.isEmpty() || recipeId == null) {
                return null;
            }
            expected.setCount(expectedCount);
            var counts = new HashMap<UUID, Integer>();
            for (int i = 5; i < parts.length; i++) {
                int separator = parts[i].lastIndexOf('=');
                if (separator <= 0 || separator == parts[i].length() - 1) {
                    return null;
                }
                int count = Integer.parseInt(parts[i].substring(separator + 1));
                if (count < 0) {
                    return null;
                }
                counts.put(UUID.fromString(parts[i].substring(0, separator)), count);
            }
            return new HarvestState(expected, counts, recipeId, createdTick);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<ItemEntity> matchingEntities(ServerLevel level, AABB bounds,
                                                      ItemStack expectedOutput) {
        var result = new ArrayList<ItemEntity>();
        for (var entity : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            var current = entity.getItem();
            if (entity.isAlive() && !current.isEmpty()
                    && ItemStack.isSameItemSameTags(current, expectedOutput)) {
                result.add(entity);
            }
        }
        return result;
    }

    private static String encodeStack(ItemStack stack) {
        var tag = new CompoundTag();
        stack.save(tag);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                tag.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static ItemStack decodeStack(String encoded) throws Exception {
        var text = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        return ItemStack.of(TagParser.parseTag(text));
    }

    private record AttributableEntity(ItemEntity entity, int attributableCount) {
    }

    public record Snapshot(Map<UUID, Integer> entityCounts) {
        public Snapshot {
            entityCounts = Map.copyOf(entityCounts);
        }
    }

    public record HarvestState(ItemStack expectedOutput,
                               Map<UUID, Integer> preexistingEntityCounts,
                               ResourceLocation recipeId,
                               long createdTick) {
        public HarvestState {
            expectedOutput = expectedOutput.copy();
            preexistingEntityCounts = Map.copyOf(preexistingEntityCounts);
        }
    }
}
