package com.moakiee.ae2lt.packaged;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server rules for portable resonant layouts. */
public final class PPConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue RESONANT_INPUT_RANGE;

    static {
        var builder = new ForgeConfigSpec.Builder();
        RESONANT_INPUT_RANGE = builder.comment(
                "Maximum straight-line distance in blocks from a resonant child input to its machine anchor.",
                "Legacy output bindings remain supported but cannot be created or edited.")
                .defineInRange("resonantInputRange", 32, 1, 1024);
        SPEC = builder.build();
    }

    private PPConfig() {}
}
