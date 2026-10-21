package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.util.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;

final class FAPatterns {
    private FAPatterns() {}
    static KeyCounter declared(IPatternDetails pattern) {
        var out = new KeyCounter();
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length != 1) return null;
            try { out.add(possible[0].what(), Math.multiplyExact(possible[0].amount(), input.getMultiplier())); }
            catch (ArithmeticException e) { return null; }
        }
        return out;
    }
    static KeyCounter actual(KeyCounter[] inputs) {
        if (inputs == null) return null;
        var out = new KeyCounter();
        for (var input : inputs) {
            if (input == null) return null;
            for (var entry : input) {
                if (entry.getLongValue() <= 0) return null;
                try { out.set(entry.getKey(), Math.addExact(out.get(entry.getKey()), entry.getLongValue())); }
                catch (ArithmeticException e) { return null; }
            }
        }
        return out;
    }
    static List<GenericStack> stacks(KeyCounter amounts) {
        var out = new ArrayList<GenericStack>();
        for (var entry : amounts) if (entry.getLongValue() > 0) out.add(new GenericStack(entry.getKey(),entry.getLongValue()));
        return List.copyOf(out);
    }
    static boolean output(IPatternDetails pattern, AEKey key, long amount) {
        return pattern.getOutputs().length == 1 && pattern.getPrimaryOutput().what().equals(key) && pattern.getPrimaryOutput().amount()==amount;
    }
}
