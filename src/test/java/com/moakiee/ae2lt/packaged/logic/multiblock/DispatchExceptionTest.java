package com.moakiee.ae2lt.packaged.logic.multiblock;

import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueOutput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses a synthetic key; the production CUSTOM insertion path is unchanged. */
public class DispatchExceptionTest {
    private static final AEKey KEY = new TestKey();
    private static class TestKey extends AEKey {
        public AEKeyType getType() { return AEKeyType.items(); }
        public AEKey dropSecondary() { return this; }
        public Object getPrimaryKey() { return this; }
        public Identifier getId() { return Identifier.fromNamespaceAndPath("review", "input"); }
        public boolean hasComponents() { return false; }
        protected Component computeDisplayName() { return Component.literal("review"); }
        public void toTag(ValueOutput out) {}
        public void writeToPacket(RegistryFriendlyByteBuf out) {}
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
        public boolean isTagged(net.minecraft.tags.TagKey<?> tag) { return false; }
    }
    private TargetSlot throwingAfterInsertion(AtomicLong machine, long amount) {
        var stack = new GenericStack(KEY, amount);
        return new TargetSlot(null, BlockPos.ZERO, null, List.of(stack), InsertionStrategy.CUSTOM,
            (offered, mode) -> {
                if (mode == Actionable.SIMULATE) return offered.amount();
                machine.addAndGet(offered.amount());
                throw new IllegalStateException("machine callback threw after accepting input");
            });
    }
    @Test
    void firstThrowingInsertionMustNotAuthorizeAFullCpuRefund() {
        var machine = new AtomicLong();
        var retained = new ArrayList<GenericStack>();
        var result = DispatchExecutor.execute(new DispatchPlan(List.of(throwingAfterInsertion(machine, 10)), null),
                null, retained::addAll);
        System.out.println("REVIEW_FIRST_THROW machine=" + machine.get() + " status=" + result.status());
        assertFalse(result.failure(), "failure makes the CPU refund inputs already accepted by the machine");
    }
    @Test
    void laterThrowingInsertionMustNotRetainItsAlreadyInsertedInput() {
        var machine = new AtomicLong();
        var first = new TargetSlot(null, BlockPos.ZERO, null, List.of(new GenericStack(KEY, 5)),
                InsertionStrategy.CUSTOM, (s, m) -> { if (m == Actionable.MODULATE) machine.addAndGet(s.amount()); return s.amount(); });
        var retained = new ArrayList<GenericStack>();
        var result = DispatchExecutor.execute(new DispatchPlan(List.of(first, throwingAfterInsertion(machine, 10)), null),
                null, retained::addAll);
        long retainedAmount = retained.stream().mapToLong(GenericStack::amount).sum();
        System.out.println("REVIEW_LATER_THROW machine=" + machine.get() + " retained=" + retainedAmount + " status=" + result.status());
        assertEquals(15, machine.get() + retainedAmount, "the provider must not synthesize a duplicate of the throwing input");
    }

    @Test
    void unknownReceiptRetainsOnlyUnvisitedInputsAcrossStacksAndTargets() {
        var machine = new AtomicLong();
        var retained = new ArrayList<GenericStack>();
        var first = new TargetSlot(null, BlockPos.ZERO, null,
                List.of(new GenericStack(KEY, 2), new GenericStack(KEY, 3), new GenericStack(KEY, 5)),
                InsertionStrategy.CUSTOM, (stack, mode) -> {
                    if (mode == Actionable.SIMULATE) return stack.amount();
                    machine.addAndGet(stack.amount());
                    if (stack.amount() == 3) throw new IllegalStateException("after acceptance");
                    return stack.amount();
                });
        var tail = new TargetSlot(null, BlockPos.ZERO, null, List.of(new GenericStack(KEY, 7)),
                InsertionStrategy.CUSTOM, (stack, mode) -> {
                    assertEquals(Actionable.SIMULATE, mode);
                    return stack.amount();
                });
        var result = DispatchExecutor.execute(new DispatchPlan(List.of(first, tail),
                () -> fail("incomplete dispatch cannot run its completion hook")), null, retained::addAll);
        assertTrue(result.success());
        assertEquals(5, machine.get());
        assertEquals(List.of(5L, 7L), retained.stream().map(GenericStack::amount).toList());
    }

    @Test
    void invalidModulationReceiptDoesNotAuthorizeFullRefund() {
        for (long invalid : new long[] {-1, 11}) {
            var target = new TargetSlot(null, BlockPos.ZERO, null, List.of(new GenericStack(KEY, 10)),
                    InsertionStrategy.CUSTOM, (s, m) -> m == Actionable.SIMULATE ? s.amount() : invalid);
            var retained = new ArrayList<GenericStack>();
            var result = DispatchExecutor.execute(new DispatchPlan(List.of(target), null), null, retained::addAll);
            assertTrue(result.success());
            assertTrue(retained.isEmpty());
        }
    }

    @Test
    void confirmedZeroReceiptStillAllowsRetryAndPartialReceiptKeepsOnlyResidual() {
        for (long accepted : new long[] {0, 4}) {
            var target = new TargetSlot(null, BlockPos.ZERO, null, List.of(new GenericStack(KEY, 10)),
                    InsertionStrategy.CUSTOM, (s, m) -> m == Actionable.SIMULATE ? s.amount() : accepted);
            var retained = new ArrayList<GenericStack>();
            var result = DispatchExecutor.execute(new DispatchPlan(List.of(target), null), null, retained::addAll);
            assertEquals(accepted == 0, result.failure());
            assertEquals(accepted == 0 ? 0 : 6, retained.stream().mapToLong(GenericStack::amount).sum());
        }
    }
}
