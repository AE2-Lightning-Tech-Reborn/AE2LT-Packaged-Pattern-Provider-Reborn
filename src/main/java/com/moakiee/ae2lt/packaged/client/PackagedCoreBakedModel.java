package com.moakiee.ae2lt.packaged.client;

import com.moakiee.ae2lt.packaged.mixin.client.ItemLayerRenderStateAccessor;
import com.moakiee.ae2lt.packaged.mixin.client.ItemStackRenderStateAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Preserves the packaged-core base model and overlays its target item icon. */
final class PackagedCoreBakedModel implements ItemModel {
    private static final float TARGET_CENTER_X = 0.78f;
    private static final float TARGET_CENTER_Y = 0.22f;
    private static final float TARGET_CENTER_Z = 0.57f;
    private static final float TARGET_SCALE = 0.45f;
    private static final float TARGET_DEPTH_SCALE = 0.02f;

    private final ItemModel delegate;

    PackagedCoreBakedModel(ItemModel delegate) { this.delegate = delegate; }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext context, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        delegate.update(state, stack, resolver, context, level, owner, seed);
        var target = PackagedCoreItemRenderer.targetStack(stack);
        if (target.isEmpty()) return;
        var access = (ItemStackRenderStateAccessor) state;
        int first = access.ae2ltpp$activeLayerCount();
        resolver.appendItemLayers(state, target.get(), ItemDisplayContext.GUI, level, owner, seed);
        int end = access.ae2ltpp$activeLayerCount();
        for (int i = first; i < end; i++) {
            var layer = access.ae2ltpp$layers()[i];
            var old = ((ItemLayerRenderStateAccessor) layer).ae2ltpp$localTransform();
            var transform = new Matrix4f(old).translate(TARGET_CENTER_X, TARGET_CENTER_Y, TARGET_CENTER_Z)
                    .scale(TARGET_SCALE, TARGET_SCALE, TARGET_SCALE * TARGET_DEPTH_SCALE);
            layer.setLocalTransform(transform);
        }
    }
}
