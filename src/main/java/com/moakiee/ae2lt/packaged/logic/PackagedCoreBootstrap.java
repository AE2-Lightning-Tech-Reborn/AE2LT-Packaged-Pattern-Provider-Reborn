package com.moakiee.ae2lt.packaged.logic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A machine may manufacture its own unlocking core before one is installed.
 * This is only an admission rule: the adapter must still match the real recipe,
 * validate its result and consume its normal inputs, energy and world state.
 */
public final class PackagedCoreBootstrap {
    private PackagedCoreBootstrap() {}

    public static boolean isCorePattern(IPatternDetails pattern) {
        return outputCore(pattern) != null;
    }

    public static boolean allows(IPatternDetails pattern, @Nullable ResourceLocation requiredAdapter) {
        var core = outputCore(pattern);
        return core != null && core.covers(requiredAdapter);
    }

    @Nullable
    private static MultiblockAdapterItem outputCore(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        if (outputs.length == 0 || outputs[0].amount() <= 0
                || !(outputs[0].what() instanceof AEItemKey key)
                || !key.getId().getNamespace().equals("ae2ltpp")) return null;
        return key.getItem() instanceof MultiblockAdapterItem core ? core : null;
    }
}
