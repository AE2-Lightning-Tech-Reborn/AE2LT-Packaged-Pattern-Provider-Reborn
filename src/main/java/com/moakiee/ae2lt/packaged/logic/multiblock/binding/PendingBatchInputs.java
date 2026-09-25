package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.ToLongFunction;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InputBatchCraftingAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Paid physical inputs, retained until each port acknowledges its own transfer. */
public final class PendingBatchInputs {
    public record Delivery(ResourceLocation adapter, ResourceLocation dimension, BlockPos pos,
                           Direction face, ResourceLocation block, GenericStack stack) {
        public Delivery { pos = pos.immutable(); }
        public Delivery withAmount(long n) { return new Delivery(adapter, dimension, pos, face, block, new GenericStack(stack.what(), n)); }
    }
    public record Ownership(ResourceLocation adapter, ResourceLocation dimension, BlockPos pos,
                            ResourceLocation block, List<GenericStack> outputs) {
        public Ownership { pos = pos.immutable(); outputs = List.copyOf(outputs); }
    }
    private final List<Delivery> pending = new ArrayList<>();
    private final List<Delivery> uncertain = new ArrayList<>();
    private Ownership ownership;
    private boolean initialized;

    public static List<Delivery> prepare(DispatchPlan plan, InputBatchCraftingAdapter adapter) {
        if (plan == null || plan.targets().isEmpty()) return List.of();
        var result = new ArrayList<Delivery>(); var ports = new HashSet<List<Object>>();
        for (var target : plan.targets()) {
            if (target.level() == null || target.stacks().size() != 1 || target.face() == null
                    || target.stacks().get(0).amount() <= 0) return List.of();
            var dimension = target.level().dimension().location();
            // Two faces can expose one tank. Do not sum simulations against aliased ports.
            if (!ports.add(List.of(dimension, target.pos()))) return List.of();
            result.add(new Delivery(adapter.batchAdapterId(), dimension, target.pos(), target.face(),
                    BuiltInRegistries.BLOCK.getKey(target.level().getBlockState(target.pos()).getBlock()), target.stacks().get(0)));
        }
        return List.copyOf(result);
    }
    public static boolean matchesInputs(List<Delivery> deliveries, appeng.api.stacks.KeyCounter[] inputs) {
        var expected = new java.util.HashMap<appeng.api.stacks.AEKey, Long>();
        var actual = new java.util.HashMap<appeng.api.stacks.AEKey, Long>();
        try {
            for (var counter : inputs) for (var entry : counter) {
                if (entry.getLongValue() > 0) expected.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
            for (var d : deliveries) actual.merge(d.stack().what(), d.stack().amount(), Math::addExact);
            return actual.equals(expected);
        } catch (ArithmeticException overflow) { return false; }
    }

    public static List<Delivery> scale(List<Delivery> source, int copies) {
        if (copies <= 0) throw new IllegalArgumentException("copies");
        return source.stream().map(d -> d.withAmount(Math.multiplyExact(d.stack().amount(), copies))).toList();
    }
    public void adopt(List<Delivery> source, Ownership owner) {
        if (hasPending() || source.isEmpty() || owner == null) throw new IllegalStateException("batch ownership conflict");
        pending.addAll(source); ownership = owner; initialized = false;
    }
    public Ownership ownership() { return ownership; }
    public boolean initialized() { return initialized; }
    public void markInitialized() { initialized = true; }

    public void deliver(ToLongFunction<Delivery> sink) {
        if (!initialized) throw new IllegalStateException("output ownership must precede delivery");
        var it = pending.listIterator();
        while (it.hasNext()) {
            var d = it.next(); long accepted;
            try {
                accepted = sink.applyAsLong(d);
                if (accepted < 0 || accepted > d.stack().amount()) throw new IllegalStateException("invalid input receipt: " + accepted);
            } catch (RuntimeException | LinkageError failure) {
                // The throwing call may already have inserted items. Neither retry nor refund it.
                it.remove(); uncertain.add(d);
                throw failure;
            }
            if (accepted == d.stack().amount()) it.remove();
            else if (accepted > 0) it.set(d.withAmount(d.stack().amount() - accepted));
        }
        if (!hasPending()) { ownership = null; initialized = false; }
    }
    public List<Delivery> snapshot() { return List.copyOf(pending); }
    public List<Delivery> uncertainSnapshot() { return List.copyOf(uncertain); }
    public boolean hasPending() { return !pending.isEmpty() || !uncertain.isEmpty(); }
    public List<GenericStack> drainKnownInputs() {
        var stacks = pending.stream().map(Delivery::stack).toList(); clear(); return stacks;
    }
    public void clear() { pending.clear(); uncertain.clear(); ownership = null; initialized = false; }

    public CompoundTag writeToNBT() {
        var tag = new CompoundTag(); tag.put("ports", writeDeliveries(pending)); tag.put("uncertain", writeDeliveries(uncertain));
        tag.putBoolean("initialized", initialized);
        if (ownership != null) {
            var owner = location(ownership.adapter(), ownership.dimension(), ownership.pos(), ownership.block());
            var outputs = new ListTag(); for (var stack : ownership.outputs()) outputs.add(GenericStack.writeTag(stack));
            owner.put("outputs", outputs); tag.put("owner", owner);
        }
        return tag;
    }
    public void readFromNBT(CompoundTag tag) {
        clear(); readDeliveries(tag.getList("ports", Tag.TAG_COMPOUND), pending);
        readDeliveries(tag.getList("uncertain", Tag.TAG_COMPOUND), uncertain);
        initialized = tag.getBoolean("initialized");
        if (tag.contains("owner", Tag.TAG_COMPOUND)) {
            var owner = tag.getCompound("owner"); var outputs = new ArrayList<GenericStack>();
            for (var entry : owner.getList("outputs", Tag.TAG_COMPOUND)) {
                var stack = GenericStack.readTag((CompoundTag) entry); if (stack != null && stack.amount() > 0) outputs.add(stack);
            }
            ownership = new Ownership(id(owner,"adapter"), id(owner,"dimension"), BlockPos.of(owner.getLong("pos")), id(owner,"block"), outputs);
        }
    }
    private static CompoundTag location(ResourceLocation adapter, ResourceLocation dimension, BlockPos pos, ResourceLocation block) {
        var t = new CompoundTag(); t.putString("adapter", adapter.toString()); t.putString("dimension", dimension.toString());
        t.putLong("pos", pos.asLong()); t.putString("block", block.toString()); return t;
    }
    private static ListTag writeDeliveries(List<Delivery> deliveries) {
        var tags = new ListTag();
        for (var d : deliveries) {
            var t = location(d.adapter(),d.dimension(),d.pos(),d.block()); t.putInt("face",d.face().get3DDataValue());
            t.put("stack",GenericStack.writeTag(d.stack())); tags.add(t);
        }
        return tags;
    }
    private static void readDeliveries(ListTag tags, List<Delivery> result) {
        for (var entry : tags) {
            var t = (CompoundTag) entry; var stack = GenericStack.readTag(t.getCompound("stack"));
            if (stack != null && stack.amount() > 0) result.add(new Delivery(id(t,"adapter"),id(t,"dimension"),
                    BlockPos.of(t.getLong("pos")),Direction.from3DDataValue(t.getInt("face")),id(t,"block"),stack));
        }
    }
    private static ResourceLocation id(CompoundTag tag,String key) { return new ResourceLocation(tag.getString(key)); }
}
