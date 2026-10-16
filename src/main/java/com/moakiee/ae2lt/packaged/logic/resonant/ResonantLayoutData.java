package com.moakiee.ae2lt.packaged.logic.resonant;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Forge 1.20.1 item tags provide persistence and normal menu synchronization. */
public final class ResonantLayoutData {
    private static final String KEY = "ae2ltpp_resonant_layout";

    private ResonantLayoutData() {}

    @Nullable
    public static ResonantLayout get(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(KEY, Tag.TAG_COMPOUND)) return null;
        try {
            return ResonantLayout.CODEC.parse(NbtOps.INSTANCE, tag.get(KEY)).result().orElse(null);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    public static void set(ItemStack stack, ResonantLayout layout) {
        var encoded = ResonantLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout)
                .getOrThrow(false, message -> {});
        stack.getOrCreateTag().put(KEY, encoded);
    }
}
