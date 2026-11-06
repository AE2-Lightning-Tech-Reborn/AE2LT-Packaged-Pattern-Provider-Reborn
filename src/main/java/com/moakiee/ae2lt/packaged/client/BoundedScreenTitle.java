package com.moakiee.ae2lt.packaged.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.TextOverride;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.Text;
import com.moakiee.ae2lt.packaged.menu.CoreOrderConfirmation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Keeps our single-line titles clear of panel edges and title-bar buttons. */
public final class BoundedScreenTitle {
    private Component tooltip;
    private Rect2i tooltipArea;

    public void reset() {
        tooltip = null;
        tooltipArea = null;
    }

    public boolean draw(AEBaseScreen<?> screen, GuiGraphics graphics, Text text, TextOverride override) {
        boolean provider = screen instanceof PackagedPatternProviderScreen;
        boolean coreConfirmation = screen instanceof CraftConfirmScreen
                && screen.getMenu() instanceof CoreOrderConfirmation state
                && !state.ae2ltpp$coreMode().isEmpty();
        if ((!provider && !coreConfirmation)
                || text != screen.getStyle().getText().get(AEBaseScreen.TEXT_ID_DIALOG_TITLE)
                || (override != null && override.isHidden())) {
            return false;
        }

        var bounds = new Rect2i(0, 0, screen.getXSize(), screen.getYSize());
        var position = text.getPosition().resolve(bounds);
        int right = screen.getXSize() - 8;
        if (provider) {
            right = Math.min(right, screen.getStyle().getWidget("openPriority").resolve(bounds).getX() - 4);
        }
        float scale = text.getScale();
        int available = Math.max(0, (int) ((right - position.getX()) / scale));
        var content = override != null && override.getContent() != null
                ? override.getContent().copy().withStyle(text.getText().getStyle()) : text.getText();
        var font = screen.getMinecraft().font;
        if (font.width(content) <= available) {
            return false;
        }

        // Use formatted text so translated and styled components retain their formatting.
        String ellipsis = "...";
        var clipped = available >= font.width(ellipsis)
                ? FormattedText.composite(font.substrByWidth(content, available - font.width(ellipsis)),
                        FormattedText.of(ellipsis))
                : font.substrByWidth(content, available);
        graphics.pose().pushPose();
        graphics.pose().translate(position.getX(), position.getY(), 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, Language.getInstance().getVisualOrder(clipped), 0, 0,
                screen.getStyle().getColor(text.getColor()).toARGB(), false);
        graphics.pose().popPose();
        tooltip = content;
        tooltipArea = new Rect2i(screen.getGuiLeft() + position.getX(), screen.getGuiTop() + position.getY(),
                Math.max(0, right - position.getX()), (int) Math.ceil(font.lineHeight * scale));
        return true;
    }

    public void renderTooltip(AEBaseScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY) {
        if (tooltip != null && tooltipArea.contains(mouseX, mouseY)) {
            graphics.renderTooltip(screen.getMinecraft().font, tooltip, mouseX, mouseY);
        }
    }
}
