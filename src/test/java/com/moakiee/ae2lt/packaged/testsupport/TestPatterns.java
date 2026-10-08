package com.moakiee.ae2lt.packaged.testsupport;

import java.lang.reflect.Proxy;
import java.util.Set;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails;

public final class TestPatterns {
    private TestPatterns() {}

    public static IPatternDetails.IInput input(long multiplier, GenericStack... choices) {
        return (IPatternDetails.IInput) Proxy.newProxyInstance(TestPatterns.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.IInput.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPossibleInputs" -> choices;
                    case "getMultiplier" -> multiplier;
                    case "isValid" -> true;
                    default -> null;
                });
    }

    public static IPatternDetails pattern(GenericStack[] outputs, IPatternDetails.IInput... inputs) {
        return pattern(outputs, inputs, Set.of(), false);
    }

    public static IPatternDetails pattern(GenericStack[] outputs, IPatternDetails.IInput[] inputs,
                                         Set<Integer> fuzzyInputs, boolean fuzzyOutput) {
        var type = fuzzyInputs.isEmpty() && !fuzzyOutput ? IPatternDetails.class : OverloadedPatternDetails.class;
        return (IPatternDetails) Proxy.newProxyInstance(TestPatterns.class.getClassLoader(),
                type == IPatternDetails.class ? new Class<?>[]{type} : new Class<?>[]{IPatternDetails.class, type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getInputs" -> inputs;
                    case "getOutputs" -> outputs;
                    case "isFuzzyInput", "acceptsSameIdVariants" -> fuzzyInputs.contains((Integer) args[0]);
                    case "isFuzzyOutput", "producesSameIdVariants" -> fuzzyOutput;
                    case "hasFuzzyInputs" -> !fuzzyInputs.isEmpty();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "TestPattern";
                    default -> null;
                });
    }
}
