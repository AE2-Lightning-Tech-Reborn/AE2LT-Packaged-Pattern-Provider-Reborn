package com.moakiee.ae2lt.packaged.logic.multiblock.astral;

import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Optional integration boundary: no Astral classes are linked from the addon. */
final class AstralAccess {
    static final String ROOT = "hellfirepvp.astralsorcery.common.";

    private AstralAccess() {}

    static Class<?> type(String suffix) {
        return ReflectionSupport.findClassCached(ROOT + suffix)
                .orElseThrow(() -> new IllegalStateException("Astral API unavailable: " + suffix));
    }

    static Object call(Object target, String method, Class<?>[] types, Object... args) {
        Class<?> owner = target instanceof Class<?> c ? c : target.getClass();
        var found = ReflectionSupport.findMethodCached(owner, method, types)
                .orElseThrow(() -> new IllegalStateException("Astral method unavailable: " + method));
        return ReflectionSupport.invoke(found, target instanceof Class<?> ? null : target, args).orElse(null);
    }

    static Object get(Object target, String method) {
        return call(target, method, new Class<?>[0]);
    }

    static List<?> list(Object target, String method) {
        Object value = get(target, method);
        if (!(value instanceof List<?> list)) throw new IllegalStateException("Astral list unavailable: " + method);
        return list;
    }

    static Object data(BlockEntity tile) { return get(tile, "getTileData"); }

    static Optional<?> active(BlockEntity tile) {
        Object value = get(data(tile), "getActiveRecipe");
        if (!(value instanceof Optional<?> optional)) throw new IllegalStateException("Astral active recipe unavailable");
        return optional;
    }

    static IItemHandlerModifiable inventory(BlockEntity tile, boolean altar) {
        Object value = get(data(tile), altar ? "getAltarInventory" : "getInventory");
        if (!(value instanceof IItemHandlerModifiable inventory)) throw new IllegalStateException("Astral inventory unavailable");
        return inventory;
    }

    static boolean is(BlockEntity tile, String name) {
        return tile != null && type("tile." + name).isInstance(tile);
    }

    @SuppressWarnings("unchecked")
    static Map<Integer, BlockPos> relayOffsets() {
        return (Map<Integer, BlockPos>) get(type("tile.TileAltar"), "getRelayGridOffsets");
    }

    @SuppressWarnings("unchecked")
    static Set<BlockPos> liquidOffsets() {
        return (Set<BlockPos>) get(type("tile.TileInfuser"), "getLiquidOffsets");
    }
}
