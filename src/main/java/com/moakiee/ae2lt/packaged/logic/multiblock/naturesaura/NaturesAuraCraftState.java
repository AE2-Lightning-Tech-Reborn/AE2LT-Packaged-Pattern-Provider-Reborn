package com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.items.ItemStackHandler;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;

/** Stores results emitted by a paid native craft until the provider harvests them. */
public final class NaturesAuraCraftState {
    public static final String KEY = "ae2ltpp:natures_aura_craft";
    public static final String TREE_LINK = "ae2ltpp:natures_aura_tree_link";
    private static final String PLACED_POWDER = "placed_powder";
    private static final String PLACED_SAPLING = "placed_sapling";
    private static final String EXPECTED_OUTPUT = "expected_output";

    public static boolean active(BlockEntity be) {
        return be != null && be.getPersistentData().contains(KEY);
    }

    public static boolean owned(BlockEntity be, BlockPos owner) {
        return active(be) && be.getPersistentData().getCompound(KEY).getLong("owner") == owner.asLong();
    }

    public static void cancelWithoutResult(BlockEntity be) {
        if (!active(be)) return;
        CompoundTag state = be.getPersistentData().getCompound(KEY);
        if (!state.getList("results", 10).isEmpty()) return;
        if (be.getLevel() instanceof ServerLevel level) {
            dropPending(level, be.getBlockPos());
            be.getPersistentData().remove(KEY);
            be.setChanged();
            return;
        }
        clearTreeLink(be, state);
        be.getPersistentData().remove(KEY);
        be.setChanged();
    }

    static void begin(BlockEntity be, BlockPos owner) {
        CompoundTag state = new CompoundTag();
        state.putLong("owner", owner.asLong());
        be.getPersistentData().put(KEY, state);
        be.setChanged();
    }

    static void storeExpectedOutput(BlockEntity be, ItemStack output) {
        if (!active(be) || output.isEmpty()) return;
        be.getPersistentData().getCompound(KEY).put(EXPECTED_OUTPUT, output.save(new CompoundTag()));
        be.setChanged();
    }

    static void storeOfferingStart(BlockEntity be, ItemStack start) {
        CompoundTag tag = start.save(new CompoundTag());
        be.getPersistentData().getCompound(KEY).put("start", tag);
        be.setChanged();
    }

    static void recordPlacedPowder(BlockEntity be, BlockPos pos) {
        if (!active(be)) return;
        ListTag positions = be.getPersistentData().getCompound(KEY).getList(PLACED_POWDER, 4);
        positions.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        be.getPersistentData().getCompound(KEY).put(PLACED_POWDER, positions);
        be.setChanged();
    }

    static void recordPlacedSapling(BlockEntity be, BlockPos pos) {
        if (!active(be)) return;
        be.getPersistentData().getCompound(KEY).putLong(PLACED_SAPLING, pos.asLong());
        be.setChanged();
    }

    public static ItemStack offeringStart(BlockEntity be) {
        if (!active(be)) return ItemStack.EMPTY;
        CompoundTag tag = be.getPersistentData().getCompound(KEY).getCompound("start");
        return tag.isEmpty() ? ItemStack.EMPTY : ItemStack.of(tag);
    }

    public static void consumeOfferingStart(BlockEntity be) {
        if (active(be)) {
            be.getPersistentData().getCompound(KEY).remove("start");
            be.setChanged();
        }
    }

    public static boolean capture(BlockEntity be, ItemStack output) {
        if (!active(be) || output.isEmpty()) return false;
        CompoundTag state = be.getPersistentData().getCompound(KEY);
        CompoundTag expected = state.getCompound(EXPECTED_OUTPUT);
        if (!expected.isEmpty() && !ItemStack.isSameItemSameTags(ItemStack.of(expected), output)) {
            return false;
        }
        ListTag results = state.getList("results", 10);
        results.add(output.save(new CompoundTag()));
        state.put("results", results);
        state.putBoolean("completed", true);
        be.setChanged();
        return true;
    }

    public static boolean capture(BlockEntity be, Entity entity) {
        return entity instanceof ItemEntity item && capture(be, item.getItem());
    }

    static List<GenericStack> drain(BlockEntity be, boolean finished, AllowedOutputFilter filter) {
        if (!active(be)) return List.of();
        CompoundTag state = be.getPersistentData().getCompound(KEY);
        List<GenericStack> results = new ArrayList<>();
        ListTag stored = state.getList("results", 10);
        ListTag retained = new ListTag();
        for (int i = 0; i < stored.size(); i++) {
            ItemStack stack = ItemStack.of(stored.getCompound(i));
            if (stack.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(stack);
            if (filter != null && (filter.isEmpty() || !filter.matches(key))) {
                retained.add(stack.save(new CompoundTag()));
            } else {
                results.add(new GenericStack(key, stack.getCount()));
            }
        }
        if (retained.isEmpty()) {
            state.remove("results");
            if (finished && (state.getBoolean("completed") || !stored.isEmpty())) {
                clearTreeLink(be, state);
                be.getPersistentData().remove(KEY);
            }
        } else {
            state.put("results", retained);
        }
        be.setChanged();
        return List.copyOf(results);
    }

    /** Release paid contents only when the block entity is actually being removed. */
    public static void beforeBlockEntityRemoval(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof NaturesAuraManagedMachine)) return;
        if (active(be)) {
            dropPending(level, pos);
            return;
        }
        if (isTreeMachine(be)) {
            BlockPos leader = findTreeLeader(level, pos);
            if (leader != null) dropPending(level, leader);
        }
    }

    public static void dropPending(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof NaturesAuraManagedMachine) || !active(be)) return;
        CompoundTag state = be.getPersistentData().getCompound(KEY).copy();
        ItemStack start = offeringStart(be);
        if (!start.isEmpty()) {
            consumeOfferingStart(be);
            drop(level, pos, start);
        }
        if (be instanceof NaturesAuraOfferingMachine
                && NaturesAuraAccess.field(be, "itemsToSpawn") instanceof Queue<?> queue) {
            while (!queue.isEmpty()) {
                Object value = queue.remove();
                if (value instanceof ItemStack stack && !stack.isEmpty()) {
                    drop(level, pos, stack);
                }
            }
            be.setChanged();
        }
        // Refund only items still held by the machine, never inventory snapshots.
        dropUnconsumedInputs(level, pos, be);
        if (isTreeMachine(be)) {
            BlockPos center = findTreeCenter(level, state);
            if (center != null) dropPlacedTreeBlocks(level, pos, state, center);
        }
        for (GenericStack stack : drain(be, true, null)) {
            if (stack.what() instanceof AEItemKey key) drop(level, pos, key.toStack((int) stack.amount()));
        }
        clearTreeLink(be, state);
        be.getPersistentData().remove(KEY);
        if (isTreeMachine(be)) {
            NaturesAuraAccess.setRitual(be, null, null);
            NaturesAuraAccess.setTimer(be, 0);
        }
        be.setChanged();
    }

    private static void dropUnconsumedInputs(ServerLevel level, BlockPos pos, BlockEntity be) {
        if (isTreeMachine(be)) {
            CompoundTag state = be.getPersistentData().getCompound(KEY);
            BlockPos center = findTreeCenter(level, state);
            if (center != null) {
                for (BlockPos stand : NaturesAuraAccess.positions("TREE_RITUAL", center, 'W')) {
                    BlockEntity candidate = level.getBlockEntity(stand);
                    if (candidate instanceof NaturesAuraManagedMachine) dropInventory(level, stand, candidate);
                }
            }
        } else {
            dropInventory(level, pos, be);
        }
    }

    private static void dropInventory(ServerLevel level, BlockPos pos, BlockEntity be) {
        ItemStackHandler inventory = NaturesAuraAccess.items(be);
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
            drop(level, pos, stack);
        }
        be.setChanged();
    }

    private static void dropPlacedTreeBlocks(ServerLevel level, BlockPos dropPos, CompoundTag state, BlockPos center) {
        Block powder = BuiltInRegistries.BLOCK.get(new ResourceLocation("naturesaura", "gold_powder"));
        ListTag placed = state.getList(PLACED_POWDER, 4);
        for (int i = 0; i < placed.size(); i++) {
            BlockPos powderPos = BlockPos.of(((LongTag) placed.get(i)).getAsLong());
            if (!level.getBlockState(powderPos).is(powder)) continue;
            level.setBlockAndUpdate(powderPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            drop(level, dropPos, new ItemStack(powder));
        }
        if (state.contains(PLACED_SAPLING)) {
            BlockPos saplingPos = BlockPos.of(state.getLong(PLACED_SAPLING));
            BlockState current = level.getBlockState(saplingPos);
            if (saplingPos.equals(center) && current.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock) {
                level.setBlockAndUpdate(saplingPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                drop(level, dropPos, new ItemStack(current.getBlock()));
            }
        }
    }

    private static boolean isTreeMachine(BlockEntity be) {
        return be != null && !(be instanceof NaturesAuraOfferingMachine)
                && BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock())
                        .equals(new ResourceLocation("naturesaura", "wood_stand"));
    }

    private static BlockPos findTreeCenter(ServerLevel level, CompoundTag state) {
        if (!state.contains("owner")) return null;
        BlockEntity anchor = level.getBlockEntity(BlockPos.of(state.getLong("owner")));
        if (anchor == null || !anchor.getPersistentData().contains(TREE_LINK)) return null;
        return BlockPos.of(anchor.getPersistentData().getLong(TREE_LINK));
    }

    private static BlockPos findTreeLeader(ServerLevel level, BlockPos broken) {
        for (BlockPos offset : NaturesAuraAccess.positions("TREE_RITUAL", BlockPos.ZERO, 'W')) {
            BlockPos center = broken.subtract(offset);
            for (BlockPos stand : NaturesAuraAccess.positions("TREE_RITUAL", center, 'W')) {
                if (!level.isLoaded(stand)) continue;
                BlockEntity candidate = level.getBlockEntity(stand);
                if (!(candidate instanceof NaturesAuraManagedMachine) || !isTreeMachine(candidate)
                        || !active(candidate)) continue;
                CompoundTag state = candidate.getPersistentData().getCompound(KEY);
                if (center.equals(findTreeCenter(level, state))) return stand;
            }
        }
        return null;
    }

    private static void drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        level.addFreshEntity(new ItemEntity(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack));
    }

    private static void clearTreeLink(BlockEntity be, CompoundTag state) {
        if (be.getLevel() == null) return;
        BlockPos owner = BlockPos.of(state.getLong("owner"));
        BlockEntity anchor = be.getLevel().getBlockEntity(owner);
        if (anchor != null) {
            anchor.getPersistentData().remove(TREE_LINK);
            anchor.setChanged();
        }
    }

    private NaturesAuraCraftState() {
    }
}
