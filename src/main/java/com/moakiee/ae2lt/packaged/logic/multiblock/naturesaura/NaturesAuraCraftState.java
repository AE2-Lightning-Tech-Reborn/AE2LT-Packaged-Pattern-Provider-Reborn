package com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Stores only results emitted by a paid native craft, including native bonus drops. */
@EventBusSubscriber(modid = "ae2ltpp")
public final class NaturesAuraCraftState {
    public static final String KEY = "ae2ltpp:natures_aura_craft";
    static final String TREE_LINK = "ae2ltpp:natures_aura_tree_link";

    public static boolean active(BlockEntity be) {
        return be.getPersistentData().contains(KEY);
    }

    public static boolean owned(BlockEntity be, BlockPos owner) {
        return active(be) && be.getPersistentData().getCompound(KEY).getLong("owner") == owner.asLong();
    }

    /** Native cancellation releases the lane without recreating consumed materials. */
    public static void cancelWithoutResult(BlockEntity be) {
        if (!active(be)) return;
        var state = be.getPersistentData().getCompound(KEY);
        if (!state.getList("results", 10).isEmpty()) return;
        var owner = BlockPos.of(state.getLong("owner"));
        if (be.getLevel() != null && be.getLevel().isLoaded(owner)) {
            var anchor = be.getLevel().getBlockEntity(owner);
            if (anchor != null) {
                anchor.getPersistentData().remove(TREE_LINK);
                anchor.setChanged();
            }
        }
        be.getPersistentData().remove(KEY);
        be.setChanged();
    }

    static void begin(BlockEntity be, BlockPos owner) {
        var state = new CompoundTag();
        state.putLong("owner", owner.asLong());
        be.getPersistentData().put(KEY, state);
        be.setChanged();
    }

    static void storeOfferingStart(BlockEntity be, ItemStack start) {
        be.getPersistentData().getCompound(KEY).put("start", start.save(be.getLevel().registryAccess()));
        be.setChanged();
    }

    public static ItemStack offeringStart(BlockEntity be) {
        if (!active(be) || be.getLevel() == null) return ItemStack.EMPTY;
        return ItemStack.parseOptional(be.getLevel().registryAccess(), be.getPersistentData().getCompound(KEY).getCompound("start"));
    }

    public static void consumeOfferingStart(BlockEntity be) {
        if (active(be)) {
            be.getPersistentData().getCompound(KEY).remove("start");
            be.setChanged();
        }
    }

    public static boolean capture(BlockEntity be, ItemStack output) {
        if (!active(be) || output.isEmpty() || be.getLevel() == null) return false;
        var state = be.getPersistentData().getCompound(KEY);
        ListTag results = state.getList("results", 10);
        results.add(output.save(be.getLevel().registryAccess()));
        state.put("results", results);
        be.setChanged();
        return true;
    }

    public static boolean capture(BlockEntity be, Entity entity) {
        return entity instanceof ItemEntity item && capture(be, item.getItem());
    }

    static List<GenericStack> drain(BlockEntity be, boolean finished) {
        if (!active(be) || be.getLevel() == null) return List.of();
        var state = be.getPersistentData().getCompound(KEY);
        var results = new ArrayList<GenericStack>();
        for (var entry : state.getList("results", 10)) {
            ItemStack stack = ItemStack.parseOptional(be.getLevel().registryAccess(), (CompoundTag) entry);
            if (!stack.isEmpty()) results.add(new GenericStack(AEItemKey.of(stack), stack.getCount()));
        }
        // An empty native machine is not proof of completion (a tree may still be growing).
        if (results.isEmpty()) return List.of();
        state.remove("results");
        if (finished) be.getPersistentData().remove(KEY);
        be.setChanged();
        return List.copyOf(results);
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        var be = event.getBlockEntity();
        if (!(be instanceof NaturesAuraManagedMachine) || !active(be)) return;
        var pos = event.getPos();
        var start = offeringStart(be);
        if (!start.isEmpty()) {
            consumeOfferingStart(be);
            event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, start));
        }
        // Offerings finish before the native table emits its queue, one stack every three ticks.
        // Preserve those already-created results if the table is broken in that short interval.
        if (be instanceof NaturesAuraOfferingMachine
                && NaturesAuraAccess.field(be, "itemsToSpawn") instanceof java.util.Queue<?> queue) {
            while (!queue.isEmpty()) {
                if (queue.remove() instanceof ItemStack stack && !stack.isEmpty()) {
                    event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + .5,
                            pos.getY() + .5, pos.getZ() + .5, stack));
                }
            }
            be.setChanged();
        }
        for (var stack : drain(be, true)) {
            if (stack.what() instanceof AEItemKey key) {
                event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + .5, pos.getY() + .5,
                        pos.getZ() + .5, key.toStack((int) stack.amount())));
            }
        }
    }

    private NaturesAuraCraftState() {}
}
