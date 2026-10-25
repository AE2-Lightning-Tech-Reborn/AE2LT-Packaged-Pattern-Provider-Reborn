package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Only the native tick's completed result belongs to the provider. */
@EventBusSubscriber(modid = "ae2ltpp")
public final class ArsScribesState {
    private static final String ACTIVE = "ae2ltpp:scribe_paid";
    private static final String OUTPUT = "ae2ltpp:scribe_output";
    public static boolean active(BlockEntity tile) { return tile.getPersistentData().getBoolean(ACTIVE); }
    public static boolean hasOutput(BlockEntity tile) { return tile.getPersistentData().contains(OUTPUT); }
    public static void begin(BlockEntity tile) {
        tile.getPersistentData().putBoolean(ACTIVE, true);
        tile.setChanged();
    }
    public static boolean capture(BlockEntity tile, Entity entity) {
        if (!active(tile) || !(entity instanceof ItemEntity item) || item.getItem().isEmpty()) return false;
        if (hasOutput(tile)) throw new IllegalStateException("Previous scribe output was not returned");
        tile.getPersistentData().put(OUTPUT, item.getItem().save(tile.getLevel().registryAccess()));
        tile.getPersistentData().remove(ACTIVE);
        tile.setChanged();
        return true;
    }
    public static List<GenericStack> drain(BlockEntity tile) {
        if (!hasOutput(tile) || tile.getLevel() == null) return List.of();
        var result = ItemStack.parseOptional(tile.getLevel().registryAccess(), tile.getPersistentData().getCompound(OUTPUT));
        if (result.isEmpty()) return List.of();
        tile.getPersistentData().remove(OUTPUT);
        tile.setChanged();
        return List.of(new GenericStack(AEItemKey.of(result), result.getCount()));
    }
    /** A manual cancellation/replacement and either table half's teardown use native refunds. */
    public static void beforeNativeRefund(BlockEntity tile) {
        if (!active(tile) && !hasOutput(tile)) return;
        tile.getPersistentData().remove(ACTIVE);
        for (var result : drain(tile)) {
            var pos = tile.getBlockPos().getCenter();
            tile.getLevel().addFreshEntity(new ItemEntity(tile.getLevel(), pos.x, pos.y, pos.z,
                    ((AEItemKey) result.what()).toStack((int) result.amount())));
        }
        tile.setChanged();
    }
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        var tile = event.getBlockEntity();
        if (!(tile instanceof ArsScribesMachine)) return;
        // Unfinished recipe inputs and experience keep the mod's own teardown
        // behavior. Only an already-produced, captured result needs our handling.
        var pos = event.getPos().getCenter();
        for (var result : drain(tile)) event.getDrops().add(new ItemEntity(event.getLevel(), pos.x, pos.y, pos.z,
                ((AEItemKey) result.what()).toStack((int) result.amount())));
    }
    private ArsScribesState() {}
}
