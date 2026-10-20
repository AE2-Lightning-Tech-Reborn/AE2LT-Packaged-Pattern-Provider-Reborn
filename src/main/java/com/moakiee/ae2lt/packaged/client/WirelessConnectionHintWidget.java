package com.moakiee.ae2lt.packaged.client;

import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Tooltip;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/** Always-visible connection instructions beneath a wireless provider's dialog. */
final class WirelessConnectionHintWidget implements ICompositeWidget {
    private static final int PINK = 0xFFFF85C1;
    private final PackagedPatternProviderMenu menu;
    private Rect2i bounds = new Rect2i(0, 0, 176, 10);

    WirelessConnectionHintWidget(PackagedPatternProviderMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean isVisible() {
        return menu.isWirelessMode();
    }

    @Override
    public void setPosition(Point position) {
        bounds = new Rect2i(position.getX(), position.getY(), bounds.getWidth(), bounds.getHeight());
    }

    @Override
    public void setSize(int width, int height) {
        bounds = new Rect2i(bounds.getX(), bounds.getY(), width, height);
    }

    @Override
    public Rect2i getBounds() {
        return bounds;
    }

    @Override
    public void drawForegroundLayer(GuiGraphics graphics, Rect2i dialogBounds, Point mouse) {
        var font = Minecraft.getInstance().font;
        var text = message();
        int textWidth = font.width(text);
        float scale = Math.min(1.0f, (bounds.getWidth() - 8.0f) / Math.max(1, textWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.getX() + bounds.getWidth() / 2.0f, bounds.getY() + 1, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, -textWidth / 2, 0, PINK, true);
        graphics.pose().popPose();
    }

    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        return new Tooltip(message().withStyle(style -> style.withColor(PINK & 0xFFFFFF)));
    }

    private static net.minecraft.network.chat.MutableComponent message() {
        return Component.translatable("ae2ltpp.gui.wireless_connection_hint");
    }
}
