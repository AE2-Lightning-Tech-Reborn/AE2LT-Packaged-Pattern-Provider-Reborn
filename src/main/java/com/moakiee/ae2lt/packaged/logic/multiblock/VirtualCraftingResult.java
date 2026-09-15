package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.List;
import java.util.function.BooleanSupplier;

import appeng.api.stacks.GenericStack;

/**
 * Result of a virtual craft plan.
 *
 * <p>{@code planVirtualWithBinding} must not mutate the world. Any machine
 * side-effect (mana drain, basin empty, RF extract) belongs in {@link #commit()},
 * which the provider runs only after acquiring the virtual lane.
 */
public record VirtualCraftingResult(List<GenericStack> outputs, BooleanSupplier commitAction) {

    public VirtualCraftingResult {
        outputs = List.copyOf(outputs);
        commitAction = commitAction == null ? () -> true : commitAction;
    }

    public VirtualCraftingResult(List<GenericStack> outputs) {
        this(outputs, () -> true);
    }

    public boolean commit() {
        return commitAction.getAsBoolean();
    }
}
