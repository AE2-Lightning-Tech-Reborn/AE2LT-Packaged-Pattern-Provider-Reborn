package com.moakiee.ae2lt.packaged.benchmarks;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BatchCopies;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingBatchInputs;
import com.moakiee.ae2lt.packaged.logic.resonant.ResonantLayout;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntToLongFunction;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.LoadingModList;

/** Runtime methods with consumed results; a separate baseline classpath retains the pre-change code. */
public final class RuntimeCostAudit {
    private static final boolean BASELINE = Boolean.getBoolean("ae2ltpp.costBaseline");
    private static final Class<?>[] NO_TYPES = new Class<?>[0];
    private static final com.sun.management.ThreadMXBean BEAN =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final long THREAD = Thread.currentThread().threadId();
    private static volatile long sink;

    public static void main(String[] args) {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BEAN.setThreadAllocatedMemoryEnabled(true);
        for (int slots : new int[]{9, 36, 144}) layout(slots);
        reflection();
        for (int ports : new int[]{1, 4}) for (int capacity : new int[]{0, 1, 16, 63, 64, -1}) batch(ports, capacity);
    }

    private static void layout(int slots) {
        var bindings = new ArrayList<ResonantLayout.Binding>();
        for (int i = 0; i < (slots == 144 ? 180 : slots); i++) bindings.add(new ResonantLayout.Binding(i,
                new ResonantLayout.Target(new BlockPos(i + 1, i % 3, i % 7), Direction.NORTH, Optional.empty())));
        var layout = new ResonantLayout(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO),
                ResourceLocation.parse("minecraft:furnace"), Direction.NORTH, Direction.NORTH, 0, bindings);
        measure("layout-" + slots, 15_000, offset -> {
            long sum = 0;
            for (int j = 0; j < slots; j++) sum += layout.target((j + offset) % slots).orElseThrow().offset().getX();
            return sum;
        });
    }

    public static final class Fixture {
        public final Object value = new Object();
        final Object[] values = {new Object(), new Object(), new Object(), new Object()};
        public Object first() { return values[0]; }
        public Object second() { return values[1]; }
        public Object third() { return values[2]; }
        public Object fourth() { return values[3]; }
        public Object at(int i) { return values[i & 3]; }
    }

    private static void reflection() {
        var fixture = new Fixture();
        String[] names = {"first", "second", "third", "fourth"};
        measure("reflection-getter", 1_000_000, i -> System.identityHashCode(ReflectionSupport.invoke(
                // Explicit array retains the same ABI on the baseline classpath.
                ReflectionSupport.findMethodCached(fixture.getClass(), names[i & 3], NO_TYPES).orElseThrow(), fixture).orElseThrow()));
        measure("reflection-parameter", 1_000_000, i -> System.identityHashCode(ReflectionSupport.invoke(
                ReflectionSupport.findMethodCached(fixture.getClass(), "at", int.class).orElseThrow(), fixture, i & 3).orElseThrow()));
        measure("reflection-field", 1_000_000, i -> {
            try { return System.identityHashCode(ReflectionSupport.findFieldCached(Fixture.class, "value").orElseThrow().get(fixture)); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }

    private static void batch(int portCount, int capacity) {
        var ports = new ArrayList<PendingBatchInputs.Delivery>();
        for (int i = 0; i < portCount; i++) ports.add(new PendingBatchInputs.Delivery(
                ResourceLocation.parse("audit:adapter"), Level.OVERWORLD.location(), new BlockPos(i, 0, 0),
                Direction.NORTH, ResourceLocation.parse("minecraft:chest"), new GenericStack(AEItemKey.of(Items.IRON_INGOT), i + 1)));
        var inputs = List.copyOf(ports);
        measure("batch-" + portCount + "-capacity-" + capacity, 100_000, i -> {
            int room = capacity < 0 ? (i * 17) % 65 : capacity;
            int accepted;
            if (BASELINE) {
                accepted = BatchCopies.fitting(64, copies -> {
                    // Original provider path, including its eagerly scaled whole-port list.
                    for (var delivery : PendingBatchInputs.scale(inputs, copies))
                        if (delivery.stack().amount() > (delivery.pos().getX() + 1L) * room) return false;
                    return true;
                });
            } else {
                accepted = PendingBatchInputs.fittingByReceipt(inputs, 64, delivery -> Long.MAX_VALUE,
                        delivery -> Math.min(delivery.stack().amount(), (delivery.pos().getX() + 1L) * room));
            }
            if (accepted != room) throw new AssertionError("capacity mismatch");
            return accepted;
        });
    }

    private static void measure(String name, int calls, IntToLongFunction action) {
        for (int warm = 0; warm < 6; warm++) execute(action, calls);
        for (int round = 0; round < 4; round++) {
            long before = BEAN.getThreadAllocatedBytes(THREAD);
            long start = System.nanoTime();
            long checksum = execute(action, calls);
            long elapsed = System.nanoTime() - start;
            long allocated = BEAN.getThreadAllocatedBytes(THREAD) - before;
            System.out.printf("PP_COST baseline=%s case=%s round=%d calls=%d ns/call=%.3f bytes/call=%.3f checksum=%d%n",
                    BASELINE, name, round, calls, elapsed / (double) calls, allocated / (double) calls, checksum);
        }
    }

    private static long execute(IntToLongFunction action, int calls) {
        long sum = 0;
        for (int i = 0; i < calls; i++) sum += action.applyAsLong(i);
        sink = sum;
        return sum;
    }
}
