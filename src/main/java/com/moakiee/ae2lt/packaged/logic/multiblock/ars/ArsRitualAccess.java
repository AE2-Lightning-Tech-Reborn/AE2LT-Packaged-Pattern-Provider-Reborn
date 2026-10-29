package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional native API. Mutating calls propagate failures to dispatch compensation. */
final class ArsRitualAccess {
    private static final String ROOT = "com.hollingsworth.arsnouveau.";

    static boolean isTile(Object value) { return instance(value, "common.block.tile.RitualBrazierTile"); }
    static boolean isTablet(ItemStack stack) { return instance(stack.getItem(), "common.items.RitualTablet"); }
    private static boolean instance(Object value, String type) {
        return value != null && ReflectionSupport.findClassCached(ROOT + type).map(c -> c.isInstance(value)).orElse(false);
    }

    static Object field(Object value, String name) {
        try { return ReflectionSupport.findFieldCached(value.getClass(), name).orElseThrow().get(value); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Ars ritual field " + name, e); }
    }

    static Object call(Object value, String name, Class<?>[] signature, Object... args) {
        try {
            Class<?> type = value instanceof Class<?> c ? c : value.getClass();
            return ReflectionSupport.findMethodCached(type, name, signature).orElseThrow()
                    .invoke(value instanceof Class<?> ? null : value, args);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Ars ritual call " + name, e); }
    }

    static Object call(Object value, String name) { return call(value, name, new Class<?>[0]); }
    static ResourceLocation tabletId(ItemStack stack) {
        return isTablet(stack) ? (ResourceLocation) call(field(stack.getItem(), "ritual"), "getRegistryName") : null;
    }

    static Object preview(BlockEntity tile, ResourceLocation id, List<ItemStack> augments) {
        var registry = ReflectionSupport.findClassCached(ROOT + "api.registry.RitualRegistry").orElseThrow();
        var ritual = call(registry, "getRitual", new Class<?>[]{ResourceLocation.class}, id);
        if (ritual == null) return null;
        try { ReflectionSupport.findFieldCached(ritual.getClass(), "tile").orElseThrow().set(ritual, tile); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        // Dry-run only: callbacks can mutate the world. Native acceptance is evaluated
        // successively against a private consumed-items list, including stack components.
        @SuppressWarnings("unchecked")
        var consumed = (List<ItemStack>) call(ritual, "getConsumedItems");
        for (var stack : augments) {
            if (!Boolean.TRUE.equals(call(ritual, "canConsumeItem", new Class<?>[]{ItemStack.class}, stack.copy()))) return null;
            consumed.add(stack.copy());
        }
        return Boolean.TRUE.equals(call(ritual, "canStart", new Class<?>[]{net.minecraft.world.entity.player.Player.class}, (Object) null))
                ? ritual : null;
    }

    static boolean empty(BlockEntity tile) {
        return isTile(tile) && field(tile, "ritual") == null && !Boolean.TRUE.equals(field(tile, "isOff"));
    }

    private ArsRitualAccess() {}
}
