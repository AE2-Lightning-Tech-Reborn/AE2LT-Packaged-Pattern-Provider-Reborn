package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Capture only item entities emitted synchronously by our paid native ritual. */
@EventBusSubscriber(modid = "ae2ltpp")
public final class ArsRitualCraftState {
    private static final String ACTIVE = "ae2ltpp:ritual_active";
    private static final String RESULTS = "ae2ltpp:ritual_results";
    private static final ThreadLocal<BlockEntity> EMITTER = new ThreadLocal<>();
    private static final int MAX_BUFFER_STACKS = 256;

    static void begin(BlockEntity tile) {
        tile.getPersistentData().putBoolean(ACTIVE, true);
        tile.setChanged();
    }

    public static void release(BlockEntity tile) {
        if (tile.getPersistentData().contains(ACTIVE)) {
            tile.getPersistentData().remove(ACTIVE);
            tile.setChanged();
        }
    }

    public static void duringNativeCall(BlockEntity tile, Runnable nativeCall) {
        if (tile.getLevel() == null || tile.getLevel().isClientSide || !tile.getPersistentData().getBoolean(ACTIVE)) {
            nativeCall.run();
            return;
        }
        var previous = EMITTER.get();
        EMITTER.set(tile);
        try { nativeCall.run(); }
        finally {
            if (previous == null) EMITTER.remove(); else EMITTER.set(previous);
            if (ArsRitualAccess.field(tile, "ritual") == null) release(tile);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        var tile = EMITTER.get();
        if (tile == null || event.isCanceled() || event.loadedFromDisk() || tile.getLevel() != event.getLevel()
                || tile.isRemoved() || !(event.getEntity() instanceof ItemEntity item) || item.getItem().isEmpty()) return;
        var results = tile.getPersistentData().getList(RESULTS, 10);
        // Continuous rituals must not grow an unbounded save when return is disabled.
        // Overflow retains native world-drop behavior instead of destroying a result.
        if (results.size() >= MAX_BUFFER_STACKS) return;
        results.add(item.getItem().save(event.getLevel().registryAccess()));
        tile.getPersistentData().put(RESULTS, results);
        tile.setChanged();
        event.setCanceled(true);
    }

    static List<GenericStack> drain(BlockEntity tile) {
        if (tile.getLevel() == null) return List.of();
        var result = new ArrayList<GenericStack>();
        for (var entry : tile.getPersistentData().getList(RESULTS, 10)) {
            var stack = ItemStack.parseOptional(tile.getLevel().registryAccess(), (CompoundTag) entry);
            if (!stack.isEmpty()) result.add(new GenericStack(AEItemKey.of(stack), stack.getCount()));
        }
        if (!result.isEmpty()) {
            tile.getPersistentData().remove(RESULTS);
            tile.setChanged();
        }
        return List.copyOf(result);
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        var tile = event.getBlockEntity();
        if (!ArsRitualAccess.isTile(tile)) return;
        var pos = event.getPos().getCenter();
        for (var result : drain(tile)) {
            if (result.what() instanceof AEItemKey key) event.getDrops().add(new ItemEntity(event.getLevel(),
                    pos.x, pos.y, pos.z, key.toStack((int) result.amount())));
        }
    }

    private ArsRitualCraftState() {}
}
