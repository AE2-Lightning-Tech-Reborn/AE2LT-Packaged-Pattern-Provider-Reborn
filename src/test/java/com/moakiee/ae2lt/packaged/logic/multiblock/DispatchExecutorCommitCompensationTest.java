package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

class DispatchExecutorCommitCompensationTest {
    @Test
    void simulationExceptionLeavesAllInputsWithCpu() {
        var retained = new ArrayList<GenericStack>();
        var mutations = new AtomicLong();
        var t = target(3, (s, mode) -> {
            if (mode == Actionable.SIMULATE) throw new IllegalStateException("preflight");
            mutations.incrementAndGet(); return s.amount();
        });
        assertTrue(DispatchExecutor.execute(new DispatchPlan(List.of(t), null), null, retained::addAll).failure());
        assertEquals(0, mutations.get()); assertTrue(retained.isEmpty());
    }

    @Test
    void confirmedRejectionLeavesAllInputsWithCpu() {
        var retained = new ArrayList<GenericStack>();
        var t = target(3, (s,m) -> m == Actionable.SIMULATE ? s.amount() : 0L);
        assertTrue(DispatchExecutor.execute(new DispatchPlan(List.of(t), null), null, retained::addAll).failure());
        assertTrue(retained.isEmpty());
    }

    @Test
    void fullCommitRollbackReturnsOwnershipWithoutRetainingASecondCopy() {
        var owned = new AtomicLong(); var retained = new ArrayList<GenericStack>();
        var t = target(3, (s,m) -> { if(m == Actionable.MODULATE) owned.addAndGet(s.amount()); return s.amount(); });
        var plan = new DispatchPlan(List.of(t), () -> {throw new DispatchCommitException("inactive");},
                (receipts,recover) -> { assertEquals(3,owned.getAndSet(0)); recover.accept(receipts.getFirst().stack()); });
        assertTrue(DispatchExecutor.execute(plan,null,retained::addAll).failure());
        assertEquals(0,owned.get()); assertTrue(retained.isEmpty());
    }

    @Test
    void partialCommitRecoveryRetainsOnlyConfirmedAmounts() {
        var retained = new ArrayList<GenericStack>();
        var t = target(3,(s,m) -> s.amount());
        var plan = new DispatchPlan(List.of(t), () -> {throw new DispatchCommitException("failed");},
                (receipts,recover) -> recover.accept(stack(1)));
        assertTrue(DispatchExecutor.execute(plan,null,retained::addAll).success());
        assertEquals(List.of(stack(1)), retained);
    }

    @Test
    void partialInsertionRecoversReceiptAndRetainsAllUnvisitedInputs() {
        var retained = new ArrayList<GenericStack>(); var commit = new AtomicLong();
        var t = target(5,(s,m) -> m == Actionable.SIMULATE ? s.amount() : 2L);
        var next = target(4,(s,m) -> {if(m == Actionable.MODULATE) throw new AssertionError("must not visit");return s.amount();});
        var plan = new DispatchPlan(List.of(t,next),commit::incrementAndGet,
                (receipts,recover) -> {assertEquals(1,receipts.size());assertEquals(2,receipts.getFirst().stack().amount());recover.accept(stack(2));});
        assertTrue(DispatchExecutor.execute(plan,null,retained::addAll).success());
        assertEquals(0,commit.get()); assertEquals(9,retained.stream().mapToLong(GenericStack::amount).sum());
    }

    @Test
    void firstThrowingInsertionNeverRefundsItsUnknownSideEffects() {
        var retained = new ArrayList<GenericStack>(); var owned = new AtomicLong();
        var t = target(5,(s,m) -> {if(m == Actionable.MODULATE){owned.addAndGet(2);throw new IllegalStateException("after insert");}return s.amount();});
        var next = target(4,(s,m) -> s.amount());
        assertTrue(DispatchExecutor.execute(new DispatchPlan(List.of(t,next),null),null,retained::addAll).success());
        assertEquals(2,owned.get()); assertEquals(List.of(stack(4)),retained);
    }

    @Test
    void laterThrowingInsertionRecoversPriorReceiptsButNeverUnknownCurrentStack() {
        var retained = new ArrayList<GenericStack>();
        var first = target(3,(s,m) -> s.amount());
        var broken = target(5,(s,m) -> {if(m == Actionable.MODULATE)throw new IllegalStateException("unknown");return s.amount();});
        var next = target(4,(s,m) -> s.amount());
        var plan = new DispatchPlan(List.of(first,broken,next),null,
                (receipts,recover) -> {assertEquals(1,receipts.size());recover.accept(receipts.getFirst().stack());});
        assertTrue(DispatchExecutor.execute(plan,null,retained::addAll).success());
        assertEquals(7,retained.stream().mapToLong(GenericStack::amount).sum());
    }

    @Test
    void compensationExceptionKeepsPriorRecoveryReceipts() {
        var retained = new ArrayList<GenericStack>();
        var t = target(3,(s,m) -> s.amount());
        var plan = new DispatchPlan(List.of(t),() -> {throw new DispatchCommitException("failed");},
                (receipts,recover) -> {recover.accept(stack(1));throw new IllegalStateException("later recovery");});
        assertTrue(DispatchExecutor.execute(plan,null,retained::addAll).success());
        assertEquals(List.of(stack(1)),retained);
    }

    @Test
    void commitFailureWithoutRecoveryNeverRefundsMachineOwnedInputs() {
        var retained = new ArrayList<GenericStack>();
        var plan = new DispatchPlan(List.of(target(3,(s,m) -> s.amount())),() -> {throw new DispatchCommitException("failed");});
        assertTrue(DispatchExecutor.execute(plan,null,retained::addAll).success()); assertTrue(retained.isEmpty());
    }

    @Test
    void invalidReceiptsCannotBeTreatedAsConfirmedRejections() {
        for (long invalid : new long[]{-1, 6}) {
            var retained = new ArrayList<GenericStack>();
            var t = target(5,(stack,mode) -> mode == Actionable.SIMULATE ? stack.amount() : invalid);
            var next = target(4,(stack,mode) -> stack.amount());
            assertTrue(DispatchExecutor.execute(new DispatchPlan(List.of(t,next),null),null,retained::addAll).success());
            assertEquals(List.of(stack(4)),retained);
        }
    }

    private static final TestKey KEY = new TestKey();
    private static GenericStack stack(long n) { return new GenericStack(KEY,n); }
    private static TargetSlot target(long n, java.util.function.BiFunction<GenericStack,Actionable,Long> insert) {
        return new TargetSlot(null,BlockPos.ZERO,null,List.of(stack(n)),InsertionStrategy.CUSTOM,insert);
    }

    private static final class TestKey extends AEKey {
        private static final Identifier ID = Identifier.fromNamespaceAndPath("test", "key");

        @Override
        public boolean hasComponents() { return false; }

        @Override
        public AEKeyType getType() {
            return AEKeyType.items();
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public void toTag(net.minecraft.world.level.storage.ValueOutput output) {}

        @Override
        public Object getPrimaryKey() {
            return ID;
        }

        @Override
        public Identifier getId() {
            return ID;
        }

        @Override
        public void writeToPacket(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal("test key");
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean isTagged(TagKey<?> tag) {
            return false;
        }
    }
}
