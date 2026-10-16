package com.moakiee.ae2lt.packaged.logic.resonant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

/** Sparse processing inputs retain their original slot numbers, including duplicate ingredients. */
public final class ResonantPatternAccess {
    private static final String CRYSTAL_PATTERN =
            "io.github.lounode.ae2cs.common.me.crafting.ResonatingPatternDetails";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ResonantPatternAccess.class);
    private static boolean warned;
    public record AbsoluteTarget(GlobalPos pos, Direction face) {}
    public record Data(List<GenericStack> inputs, List<Optional<AbsoluteTarget>> targets) {
        public Data {
            inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
            targets = List.copyOf(targets);
            if (inputs.size() != targets.size() || inputs.size() > ResonantLayout.INPUTS) {
                throw new IllegalArgumentException("Invalid resonant input layout size");
            }
        }
    }

    private ResonantPatternAccess() {}

    @Nullable
    public static Data read(IPatternDetails pattern) {
        try {
            if (pattern instanceof AEProcessingPattern processing) {
                var inputs = Arrays.asList(processing.getSparseInputs());
                return new Data(inputs, Collections.nCopies(inputs.size(), Optional.empty()));
            }
            if (pattern != null && pattern.getClass().getName().equals(CRYSTAL_PATTERN)) {
                var type = pattern.getClass();
                var inputs = new ArrayList<GenericStack>();
                for (var entry : (List<?>) type.getMethod("getSparseInputs").invoke(pattern)) {
                    inputs.add((GenericStack) entry);
                }
                var targets = new ArrayList<Optional<AbsoluteTarget>>();
                for (var entry : (List<?>) type.getMethod("getInputTargets").invoke(pattern)) {
                    var target = (Optional<?>) entry;
                    if (target.isEmpty()) targets.add(Optional.empty());
                    else {
                        var value = target.get();
                        targets.add(Optional.of(new AbsoluteTarget(
                                (GlobalPos) value.getClass().getMethod("pos").invoke(value),
                                (Direction) value.getClass().getMethod("face").invoke(value))));
                    }
                }
                return new Data(inputs, targets);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!warned) {
                warned = true;
                LOG.warn("Cannot read resonant routing data; refusing this pattern", failure);
            }
        }
        return null;
    }
}
