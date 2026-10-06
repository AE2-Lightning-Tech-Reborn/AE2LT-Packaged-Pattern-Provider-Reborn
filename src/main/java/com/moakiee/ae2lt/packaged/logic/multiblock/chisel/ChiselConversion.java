package com.moakiee.ae2lt.packaged.logic.multiblock.chisel;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

/** Exact amounts and components for one processing-pattern copy. */
record ChiselConversion(AEItemKey input, long inputAmount, AEItemKey output, long outputAmount) {
    @Nullable
    static ChiselConversion fromPattern(IPatternDetails pattern) {
        if (pattern.getInputs().length != 1 || pattern.getOutputs().size() != 1) return null;
        var input = pattern.getInputs()[0];
        var possible = input.getPossibleInputs();
        var output = pattern.getOutputs().getFirst();
        if (possible.length == 0 || !(possible[0].what() instanceof AEItemKey in)
                || !(output.what() instanceof AEItemKey out)
                || possible[0].amount() <= 0 || input.getMultiplier() <= 0 || output.amount() <= 0
                || in.equals(out)) return null;
        try {
            return new ChiselConversion(in, Math.multiplyExact(possible[0].amount(), input.getMultiplier()),
                    out, output.amount());
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    boolean matchesInputs(KeyCounter[] inputs) {
        if (inputs == null) return false;
        long total = 0;
        try {
            for (var counter : inputs) {
                if (counter == null) return false;
                for (var entry : counter) {
                    long amount = entry.getLongValue();
                    if (amount == 0) continue;
                    if (amount < 0 || !input.equals(entry.getKey())) return false;
                    total = Math.addExact(total, amount);
                }
            }
        } catch (ArithmeticException overflow) {
            return false;
        }
        return total == inputAmount;
    }

    boolean hasDefaultOutput() {
        return output.equals(AEItemKey.of(output.getItem()));
    }
}
