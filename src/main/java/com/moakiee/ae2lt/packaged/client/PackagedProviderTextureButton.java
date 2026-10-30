package com.moakiee.ae2lt.packaged.client;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import com.moakiee.ae2lt.api.client.TextureToolbarButton;

/** Packaged-provider styles; rendering and tooltip behavior come from the shared API. */
final class PackagedProviderTextureButton extends TextureToolbarButton {
    private PackagedProviderTextureButton(String off, String on, Runnable listener) {
        super(List.of(texture(off), texture(on)), ignored -> listener.run());
    }

    static PackagedProviderTextureButton autoReturn(Runnable listener) {
        return new PackagedProviderTextureButton("auto_input_off", "auto_input_on", listener);
    }

    static PackagedProviderTextureButton providerMode(Runnable listener) {
        return new PackagedProviderTextureButton("wired_mode", "wireless_mode", listener);
    }

    static PackagedProviderTextureButton frequencyBinding(Runnable listener) {
        return new PackagedProviderTextureButton("frequency_select", "frequency_select", listener);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("ae2lt", "textures/gui/buttons/" + name + ".png");
    }
}
