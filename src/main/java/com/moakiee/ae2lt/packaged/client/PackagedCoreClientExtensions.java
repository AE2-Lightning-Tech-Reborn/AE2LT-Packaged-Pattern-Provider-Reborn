package com.moakiee.ae2lt.packaged.client;

import java.util.function.Consumer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Client extensions supplied through Forge 1.20.1 Item.initializeClient.
 * Construct the renderer lazily, after Minecraft client initialization.
 */
public final class PackagedCoreClientExtensions {

    private PackagedCoreClientExtensions() {
    }

    public static void accept(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(LazyRenderer.INSTANCE);
    }

    private static final class LazyRenderer implements IClientItemExtensions {
        static final LazyRenderer INSTANCE = new LazyRenderer();

        // Publish the lazily initialized renderer safely across threads.
        private volatile PackagedCoreItemRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            PackagedCoreItemRenderer local = renderer;
            if (local == null) {
                synchronized (this) {
                    local = renderer;
                    if (local == null) {
                        local = new PackagedCoreItemRenderer();
                        renderer = local;
                    }
                }
            }
            return local;
        }
    }
}
