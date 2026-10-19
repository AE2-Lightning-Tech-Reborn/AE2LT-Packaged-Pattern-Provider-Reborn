package com.moakiee.ae2lt.packaged.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;

/**
 * Local copy of the toolbar rendering used by the pre-refactor provider UI.
 * The textures remain AE2LT resources, but the addon does not depend on an
 * AE2LT implementation class to render them.
 */
final class PackagedProviderTextureButton extends Button implements ITooltip {
    private static final ResourceLocation AUTO_RETURN_OFF = texture("auto_input_off");
    private static final ResourceLocation AUTO_RETURN_ON = texture("auto_input_on");
    private static final ResourceLocation FREQUENCY_BIND = texture("frequency_select");

    private final ResourceLocation offTexture;
    private final ResourceLocation onTexture;
    private final Runnable listener;
    private boolean state;
    private List<Component> tooltipOff = List.of();
    private List<Component> tooltipOn = List.of();

    private PackagedProviderTextureButton(
            ResourceLocation offTexture,
            ResourceLocation onTexture,
            Runnable listener) {
        super(0, 0, 16, 16, Component.empty(), ignored -> listener.run());
        this.offTexture = offTexture;
        this.onTexture = onTexture;
        this.listener = listener;
    }

    static PackagedProviderTextureButton autoReturn(Runnable listener) {
        return new PackagedProviderTextureButton(
                AUTO_RETURN_OFF, AUTO_RETURN_ON, listener);
    }

    static PackagedProviderTextureButton providerMode(Runnable listener) {
        return new PackagedProviderTextureButton(texture("wired_mode"), texture("wireless_mode"), listener);
    }

    static PackagedProviderTextureButton frequencyBinding(Runnable listener) {
        return new PackagedProviderTextureButton(
                FREQUENCY_BIND, FREQUENCY_BIND, listener);
    }

    void setState(boolean state) {
        this.state = state;
    }

    void setTooltipOff(List<Component> tooltip) {
        this.tooltipOff = List.copyOf(tooltip);
    }

    void setTooltipOn(List<Component> tooltip) {
        this.tooltipOn = List.copyOf(tooltip);
    }

    @Override
    public void onPress() {
        listener.run();
    }

    @Override
    public void renderButton(
            PoseStack guiGraphics, int mouseX, int mouseY, float partialTick) {
        var yOffset = isHoveredOrFocused() ? 1 : 0;
        // 1.20.1's Icon sheet has a single toolbar background (no hover/focus
        // variants) and Blitter carries no z-offset control, so the press
        // feedback is conveyed by the vertical nudge alone.
        Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter()
                .dest(x - 1, y + yOffset, 18, 20)
                .blit(guiGraphics, 0);

        var texture = state ? onTexture : offTexture;
        var blitter = Blitter.texture(texture, 16, 16).src(0, 0, 16, 16);
        if (!active) {
            blitter.opacity(0.5f);
        }
        blitter.dest(x, y + 1 + yOffset)
                .blit(guiGraphics, 0);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return state ? tooltipOn : tooltipOff;
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(x, y, 16, 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible && !getTooltipMessage().isEmpty();
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(
                "ae2lt", "textures/gui/buttons/" + name + ".png");
    }
}
