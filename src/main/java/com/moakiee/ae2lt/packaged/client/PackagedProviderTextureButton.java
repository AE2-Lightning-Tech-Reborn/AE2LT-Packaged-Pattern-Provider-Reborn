package com.moakiee.ae2lt.packaged.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import appeng.util.Icon;
import com.moakiee.ae2lt.packaged.client.LegacyAe2IconBlitter;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;

/**
 * Local copy of the toolbar rendering used by the pre-refactor provider UI.
 * The textures remain AE2LT resources, but the addon does not depend on an
 * AE2LT implementation class to render them.
 */
final class PackagedProviderTextureButton extends Button implements ITooltip {
    private static final Identifier AUTO_RETURN_OFF = texture("auto_input_off");
    private static final Identifier AUTO_RETURN_ON = texture("auto_input_on");
    private static final Identifier FREQUENCY_BIND = texture("frequency_select");

    private final Identifier offTexture;
    private final Identifier onTexture;
    private final Runnable listener;
    private boolean state;
    private List<Component> tooltipOff = List.of();
    private List<Component> tooltipOn = List.of();

    private PackagedProviderTextureButton(
            Identifier offTexture,
            Identifier onTexture,
            Runnable listener) {
        super(0, 0, 16, 16, Component.empty(), ignored -> listener.run(), DEFAULT_NARRATION);
        this.offTexture = offTexture;
        this.onTexture = onTexture;
        this.listener = listener;
    }

    static PackagedProviderTextureButton autoReturn(Runnable listener) {
        return new PackagedProviderTextureButton(
                AUTO_RETURN_OFF, AUTO_RETURN_ON, listener);
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
    public void onPress(InputWithModifiers input) {
        listener.run();
    }

    @Override
    protected void extractContents(
            GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        var yOffset = isHovered() ? 1 : 0;
        var background = isHovered()
                ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : isFocused()
                        ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                        : Icon.TOOLBAR_BUTTON_BACKGROUND;
        LegacyAe2IconBlitter.of(background)
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .blit(guiGraphics);

        var texture = state ? onTexture : offTexture;
        var blitter = Blitter.texture(texture, 16, 16).src(0, 0, 16, 16);
        if (!active) {
            blitter.opacity(0.5f);
        }
        blitter.dest(getX(), getY() + 1 + yOffset)
                .blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return state ? tooltipOn : tooltipOff;
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), 16, 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return visible && !getTooltipMessage().isEmpty();
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(
                "ae2lt", "textures/gui/buttons/" + name + ".png");
    }
}
