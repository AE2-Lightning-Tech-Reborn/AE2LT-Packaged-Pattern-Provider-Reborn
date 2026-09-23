package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import net.minecraft.resources.Identifier;

public record AdapterRegistration(
        Identifier id,
        int priority,
        BooleanSupplier enabled,
        MultiblockAdapter adapter) {

    public AdapterRegistration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(adapter, "adapter");
    }

    public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    public static AdapterRegistration of(Identifier id, MultiblockAdapter adapter) {
        return new AdapterRegistration(id, 0, () -> true, adapter);
    }

    public static AdapterRegistration of(
            Identifier id,
            int priority,
            BooleanSupplier enabled,
            MultiblockAdapter adapter) {
        return new AdapterRegistration(id, priority, enabled, adapter);
    }
}
