package com.moakiee.ae2lt.packaged.logic.resonant;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.RandomAccess;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Portable layout. The source anchor is immutable when the core is moved or copied. */
public record ResonantLayout(GlobalPos source, ResourceLocation anchorBlock, Direction forward, Direction anchorFace,
                             int selected, List<Binding> bindings) {
    public static final int INPUTS = 144;
    public static final int OUTPUTS = 36;
    public static final int SLOTS = INPUTS + OUTPUTS;

    public record Target(BlockPos offset, Direction face, Optional<ResourceLocation> block) {
        public static final Codec<Target> CODEC = RecordCodecBuilder.create(b -> b.group(
                BlockPos.CODEC.fieldOf("offset").forGetter(Target::offset),
                Direction.CODEC.fieldOf("face").forGetter(Target::face),
                ResourceLocation.CODEC.optionalFieldOf("block").forGetter(Target::block)
        ).apply(b, Target::new));

        public Target { offset = offset.immutable(); }

        public Target rotated(Direction from, Direction to) {
            if (from == to) return this;
            var pos = offset;
            var side = face;
            for (int turns = turns(from, to); turns > 0; turns--) {
                pos = new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
                if (side.getAxis().isHorizontal()) side = side.getClockWise();
            }
            return new Target(pos, side, block);
        }
    }

    public record Binding(int slot, Target target) {
        public static final Codec<Binding> CODEC = RecordCodecBuilder.create(b -> b.group(
                Codec.intRange(0, SLOTS - 1).fieldOf("slot").forGetter(Binding::slot),
                Target.CODEC.fieldOf("target").forGetter(Binding::target)
        ).apply(b, Binding::new));
    }

    public static final Codec<ResonantLayout> CODEC = RecordCodecBuilder.create(b -> b.group(
            GlobalPos.CODEC.fieldOf("source").forGetter(ResonantLayout::source),
            ResourceLocation.CODEC.fieldOf("anchor_block").forGetter(ResonantLayout::anchorBlock),
            Direction.CODEC.fieldOf("forward").forGetter(ResonantLayout::forward),
            Direction.CODEC.fieldOf("anchor_face").forGetter(ResonantLayout::anchorFace),
            Codec.intRange(0, SLOTS - 1).fieldOf("selected").forGetter(ResonantLayout::selected),
            Binding.CODEC.listOf(0, SLOTS).fieldOf("bindings").forGetter(ResonantLayout::bindings)
    ).apply(b, ResonantLayout::new));

    public ResonantLayout {
        if (!forward.getAxis().isHorizontal() || selected < 0 || selected >= SLOTS) {
            throw new IllegalArgumentException("Invalid resonant layout orientation or selection");
        }
        // Retain the record/codec's list representation and equality while indexing immutable slots once.
        // Selection-only copies can share the same immutable index.
        if (!(bindings instanceof IndexedBindings)) bindings = new IndexedBindings(bindings);
    }

    public Optional<Target> target(int slot) {
        return ((IndexedBindings) bindings).target(slot);
    }

    private static final class IndexedBindings extends AbstractList<Binding> implements RandomAccess {
        private final List<Binding> entries;
        @SuppressWarnings("unchecked")
        private final Optional<Target>[] targets = (Optional<Target>[]) new Optional<?>[SLOTS];

        private IndexedBindings(List<Binding> entries) {
            this.entries = List.copyOf(entries);
            for (var binding : this.entries) {
                int slot = binding.slot();
                if (slot < 0 || slot >= SLOTS || targets[slot] != null) {
                    throw new IllegalArgumentException("Invalid or duplicate resonant layout slot");
                }
                targets[slot] = Optional.of(binding.target());
            }
        }

        private Optional<Target> target(int slot) {
            return slot >= 0 && slot < SLOTS && targets[slot] != null ? targets[slot] : Optional.empty();
        }
        @Override public Binding get(int index) { return entries.get(index); }
        @Override public int size() { return entries.size(); }
    }

    public ResonantLayout withTarget(int slot, Target target) {
        var updated = new ArrayList<>(bindings);
        updated.removeIf(b -> b.slot() == slot);
        if (target != null) updated.add(new Binding(slot, target));
        updated.sort(java.util.Comparator.comparingInt(Binding::slot));
        return new ResonantLayout(source, anchorBlock, forward, anchorFace, selected, updated);
    }

    public ResonantLayout select(int slot) {
        return new ResonantLayout(source, anchorBlock, forward, anchorFace, slot, bindings);
    }

    public ResonantLayout cycle(boolean next) {
        int input = selected < INPUTS ? selected : 0;
        return select(Math.floorMod(input + (next ? 1 : -1), INPUTS));
    }

    public boolean matchesAnchor(BlockState state) {
        return !state.isAir() && anchorBlock.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /** Use the machine's horizontal facing when available, otherwise the bound/clicked face. */
    public static Direction forward(BlockState state, Direction fallback) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            var facing = state.getValue(BlockStateProperties.FACING);
            if (facing.getAxis().isHorizontal()) return facing;
        }
        return fallback.getAxis().isHorizontal() ? fallback : Direction.NORTH;
    }

    private static int turns(Direction from, Direction to) {
        for (int turns = 0; turns < 4; turns++, from = from.getClockWise()) {
            if (from == to) return turns;
        }
        throw new IllegalArgumentException("Horizontal orientation required");
    }
}
