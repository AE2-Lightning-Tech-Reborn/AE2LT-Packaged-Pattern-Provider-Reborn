package com.moakiee.ae2lt.packaged.logic.multiblock;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.moakiee.ae2lt.packaged.logic.DispatchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DispatchExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DispatchExecutor.class);

    private DispatchExecutor() {
    }

    public static DispatchResult<Void> execute(DispatchPlan plan,
                                               IActionSource source,
                                               Consumer<List<GenericStack>> retainUndelivered) {
        var targets = plan.targets();
        if (targets.isEmpty()) {
            LOG.debug("Dispatch skipped: plan contained no insertion targets.");
            return DispatchResult.failure("Dispatch plan contained no insertion targets.");
        }

        // Simulation is a preflight, not a reservation: the real insert may accept less.
        // Simulate first so the caller can fall over to the next
        // lane candidate cleanly if any target rejects the plan. Logging the
        // rejection reason is intentional - it makes "why did the provider
        // skip this lane this tick?" investigable without a debugger.
        for (var target : targets) {
            var simulation = simulateTarget(target, source);
            if (simulation.failure()) {
                return simulation;
            }
        }

        boolean acceptedAny = false;
        for (int targetIndex = 0; targetIndex < targets.size(); targetIndex++) {
            var target = targets.get(targetIndex);
            for (int stackIndex = 0; stackIndex < target.stacks().size(); stackIndex++) {
                var stack = target.stacks().get(stackIndex);
                long accepted;
                try {
                    accepted = insertOne(target, stack, Actionable.MODULATE, source);
                } catch (RuntimeException | LinkageError failure) {
                    // Insertions must report their receipt and must not throw after accepting a
                    // quantity. A later failed call cannot undo earlier, acknowledged transfers.
                    if (!acceptedAny) {
                        LOG.error("Dispatch failed before accepting inputs at {}", target.pos(), failure);
                        return DispatchResult.failure("Target at " + target.pos() + " failed before accepting inputs.");
                    }
                    retainUndelivered.accept(remainingInputs(targets, targetIndex, stackIndex, 0));
                    LOG.error("Dispatch stopped at {}; retained the failed and unvisited inputs", target.pos(), failure);
                    return DispatchResult.success(null);
                }
                if (accepted < 0 || accepted > stack.amount()) {
                    throw new IllegalStateException("Invalid adapter insertion receipt: " + accepted);
                }
                acceptedAny |= accepted > 0;
                if (accepted < stack.amount()) {
                    if (!acceptedAny) {
                        return DispatchResult.failure("Target at " + target.pos() + " rejected the dispatch.");
                    }
                    // AE2 must not retry the full inputs after any amount has
                    // entered a machine. Own ALL remaining inputs, including
                    // stacks and targets that have not yet been visited.
                    var remaining = remainingInputs(targets, targetIndex, stackIndex, accepted);
                    retainUndelivered.accept(remaining);
                    LOG.warn("Partial dispatch at {}; retained {} undelivered stacks. "
                            + "The incomplete machine input may need recovery.", target.pos(), remaining.size());
                    // onCommit belongs only to a fully populated machine.
                    return DispatchResult.success(null);
                }
            }
        }

        if (plan.onCommit() != null) {
            try {
                plan.onCommit().run();
            } catch (RuntimeException | LinkageError failure) {
                // The inputs have already been transferred. Returning failure
                // (or throwing into AE2) would allow the CPU to refund them again.
                LOG.error("Dispatch completed, but its machine completion hook failed", failure);
            }
        }
        return DispatchResult.success(null);
    }

    private static List<GenericStack> remainingInputs(List<TargetSlot> targets, int targetIndex,
            int stackIndex, long accepted) {
        var target = targets.get(targetIndex);
        var stack = target.stacks().get(stackIndex);
        var remaining = new ArrayList<GenericStack>();
        remaining.add(new GenericStack(stack.what(), stack.amount() - accepted));
        remaining.addAll(target.stacks().subList(stackIndex + 1, target.stacks().size()));
        for (int next = targetIndex + 1; next < targets.size(); next++) {
            remaining.addAll(targets.get(next).stacks());
        }
        return List.copyOf(remaining);
    }

    private static DispatchResult<Void> simulateTarget(TargetSlot target, IActionSource source) {
        for (var stack : target.stacks()) {
            long accepted = insertOne(target, stack, Actionable.SIMULATE, source);
            if (accepted < stack.amount()) {
                LOG.debug("Dispatch simulation rejected at {}: {} x{} (only {} would fit)",
                        target.pos(), stack.what(), stack.amount(), accepted);
                return DispatchResult.failure(
                        "Target at " + target.pos() + " rejected "
                                + stack.what() + " x" + stack.amount() + " during simulation.");
            }
        }
        return DispatchResult.success(null);
    }

    private static long insertOne(TargetSlot target, GenericStack stack,
                                  Actionable mode, IActionSource source) {
        return switch (target.strategy()) {
            case STANDARD -> {
                var be = target.level().getBlockEntity(target.pos());
                if (be == null) {
                    yield 0L;
                }
                var ppt = PatternProviderTarget.get(target.level(), target.pos(), be, target.face(), source);
                if (ppt == null) {
                    yield 0L;
                }
                yield ppt.insert(stack.what(), stack.amount(), mode);
            }
            case CUSTOM -> {
                if (target.customInserter() == null) {
                    yield 0L;
                }
                yield target.customInserter().apply(stack, mode);
            }
        };
    }
}
