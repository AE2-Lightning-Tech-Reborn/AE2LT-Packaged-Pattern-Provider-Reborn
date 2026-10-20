package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.List;

import org.jetbrains.annotations.Nullable;

public record DispatchPlan(
        List<TargetSlot> targets,
        @Nullable Runnable onCommit,
        @Nullable DispatchCompensation onPartialCommit,
        @Nullable java.util.function.Consumer<java.util.function.Consumer<appeng.api.stacks.GenericStack>> outputCommit
) {
    public DispatchPlan(List<TargetSlot> targets, @Nullable Runnable onCommit,
                        @Nullable DispatchCompensation onPartialCommit) {
        this(targets, onCommit, onPartialCommit, null);
    }

    public DispatchPlan(List<TargetSlot> targets, @Nullable Runnable onCommit) {
        this(targets, onCommit, null);
    }
}
