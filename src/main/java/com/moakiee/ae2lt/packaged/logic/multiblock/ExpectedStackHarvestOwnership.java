package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * Provider-owned attribution for one expected set of {@link GenericStack}
 * outputs, including remaining amounts after partial extraction.
 */
public final class ExpectedStackHarvestOwnership {

    static final long STATE_TTL_TICKS = 20L * 60L * 60L * 6L;

    private ExpectedStackHarvestOwnership() {
    }

    public static void store(ServerLevel level, BlockPos mainPos, AdapterPersistentScope scope,
                             String stateKey, List<GenericStack> expected) {
        store(level, mainPos, scope, stateKey, level.getGameTime(), expected);
    }

    public static void store(ServerLevel level, BlockPos mainPos, AdapterPersistentScope scope,
                             String stateKey, long createdTick, List<GenericStack> expected) {
        var remaining = compact(expected);
        if (remaining.isEmpty()) {
            return;
        }
        scope.setState(mainPos, qualifiedKey(level, stateKey),
                encode(level.dimension().location(), createdTick, remaining));
    }

    @Nullable
    public static Claim load(ServerLevel level, BlockPos mainPos,
                             AdapterPersistentScope scope, String stateKey) {
        var encoded = scope.getState(mainPos, qualifiedKey(level, stateKey));
        return encoded == null || encoded.isBlank()
                ? null
                : decode(encoded, level.dimension().location());
    }

    public static void clear(ServerLevel level, BlockPos mainPos,
                             AdapterPersistentScope scope, String stateKey) {
        scope.clearState(mainPos, qualifiedKey(level, stateKey));
        scope.clearState(mainPos, stateKey);
    }

    public static String qualifiedKey(ServerLevel level, String stateKey) {
        return stateKey + "@" + level.dimension().location();
    }

    public static boolean expired(ServerLevel level, Claim claim) {
        return expired(level.getGameTime(), claim);
    }

    static boolean expired(long gameTime, Claim claim) {
        long age = gameTime - claim.createdTick();
        return age < 0 || age > STATE_TTL_TICKS;
    }

    public static long remainingOf(List<GenericStack> remaining, AEKey key) {
        long total = 0L;
        for (var stack : remaining) {
            if (stack == null || stack.what() == null || !key.equals(stack.what())) {
                continue;
            }
            try {
                total = Math.addExact(total, stack.amount());
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    public static int claimableCount(List<GenericStack> remaining, AEKey key, int available) {
        if (available <= 0) {
            return 0;
        }
        long claimed = remainingOf(remaining, key);
        if (claimed <= 0) {
            return 0;
        }
        return (int) Math.min(available, Math.min(Integer.MAX_VALUE, claimed));
    }

    public static long claimableAmount(List<GenericStack> remaining, AEKey key, long available) {
        if (available <= 0) {
            return 0L;
        }
        long claimed = remainingOf(remaining, key);
        if (claimed <= 0) {
            return 0L;
        }
        return Math.min(available, claimed);
    }

    public static void debit(List<GenericStack> remaining, AEKey key, long amount) {
        if (amount <= 0) {
            return;
        }
        for (int i = 0; i < remaining.size() && amount > 0; i++) {
            var stack = remaining.get(i);
            if (stack == null || stack.what() == null || stack.amount() <= 0
                    || !key.equals(stack.what())) {
                continue;
            }
            long take = Math.min(stack.amount(), amount);
            remaining.set(i, new GenericStack(stack.what(), stack.amount() - take));
            amount -= take;
        }
    }

    public static List<GenericStack> compact(List<GenericStack> remaining) {
        var compacted = new ArrayList<GenericStack>();
        for (var stack : remaining) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                continue;
            }
            compacted.add(new GenericStack(stack.what(), stack.amount()));
        }
        return List.copyOf(compacted);
    }

    static String encode(ResourceLocation dimension, long createdTick, List<GenericStack> remaining) {
        var list = new ListTag();
        for (var stack : remaining) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                continue;
            }
            var written = writeStack(stack);
            if (written == null) {
                continue;
            }
            list.add(written);
        }
        var tag = new CompoundTag();
        tag.putLong("t", createdTick);
        tag.put("o", list);
        // Binary NBT, not SNBT: GenericStack.writeTag stores amount under "#",
        // which TagParser treats as a comment and silently drops.
        try {
            var bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, bytes);
            var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            return dimension + "|" + payload;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nullable
    static Claim decode(String encoded, ResourceLocation dimension) {
        var separator = encoded.indexOf('|');
        if (separator <= 0 || separator == encoded.length() - 1) {
            return null;
        }
        if (!dimension.toString().equals(encoded.substring(0, separator))) {
            return null;
        }
        try {
            var bytes = Base64.getUrlDecoder().decode(encoded.substring(separator + 1));
            var tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes));
            long createdTick = tag.getLong("t");
            if (createdTick < 0 || !tag.contains("o", Tag.TAG_LIST)) {
                return null;
            }
            var list = tag.getList("o", Tag.TAG_COMPOUND);
            var remaining = new ArrayList<GenericStack>(list.size());
            for (int i = 0; i < list.size(); i++) {
                var stack = readStack(list.getCompound(i));
                if (stack == null || stack.what() == null || stack.amount() <= 0) {
                    return null;
                }
                remaining.add(new GenericStack(stack.what(), stack.amount()));
            }
            return remaining.isEmpty() ? null : new Claim(createdTick, List.copyOf(remaining));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Item and fluid keys round-trip through their own NBT so harvest claims
     * do not depend on {@code AEKeyTypes} being registered. Other keys keep
     * AE2's generic tag, which requires the live channel table.
     */
    @Nullable
    private static CompoundTag writeStack(GenericStack stack) {
        var tag = new CompoundTag();
        tag.putLong("a", stack.amount());
        if (stack.what() instanceof AEItemKey item) {
            tag.putString("k", "i");
            tag.put("v", item.toTag());
            return tag;
        }
        if (stack.what() instanceof AEFluidKey fluid) {
            tag.putString("k", "f");
            tag.put("v", fluid.toTag());
            return tag;
        }
        tag.putString("k", "g");
        tag.put("v", GenericStack.writeTag(stack));
        return tag;
    }

    @Nullable
    private static GenericStack readStack(CompoundTag tag) {
        var kind = tag.getString("k");
        var value = tag.getCompound("v");
        if ("i".equals(kind)) {
            var key = AEItemKey.fromTag(value);
            long amount = tag.getLong("a");
            return key == null || amount <= 0 ? null : new GenericStack(key, amount);
        }
        if ("f".equals(kind)) {
            var key = AEFluidKey.fromTag(value);
            long amount = tag.getLong("a");
            return key == null || amount <= 0 ? null : new GenericStack(key, amount);
        }
        if ("g".equals(kind)) {
            return GenericStack.readTag(value);
        }
        return null;
    }

    public record Claim(long createdTick, List<GenericStack> remaining) {
        public Claim {
            remaining = List.copyOf(remaining);
        }

        public boolean isEmpty() {
            for (var stack : remaining) {
                if (stack != null && stack.what() != null && stack.amount() > 0) {
                    return false;
                }
            }
            return true;
        }
    }
}
