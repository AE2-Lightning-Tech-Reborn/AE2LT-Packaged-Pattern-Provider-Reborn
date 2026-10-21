package com.moakiee.ae2lt.packaged.client;

import java.util.List;

import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Tooltip;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/** Compact status beside the crafting-lock row; full machine names remain in the tooltip. */
final class MachineRecognitionWidget implements ICompositeWidget {
    private final PackagedPatternProviderMenu menu;
    private Rect2i bounds = new Rect2i(0, 0, 33, 10);

    MachineRecognitionWidget(PackagedPatternProviderMenu menu) {
        this.menu = menu;
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
        boolean recognized = menu.recognizedMachineCount > 0;
        int background = recognized ? 0xFFD4EBD5 : 0xFFD2D2D2;
        int foreground = recognized ? 0xFF216C2A : 0xFF666666;
        graphics.fill(bounds.getX(), bounds.getY(), bounds.getX() + bounds.getWidth(),
                bounds.getY() + bounds.getHeight(), background);
        var text = Component.translatable(recognized
                ? "ae2ltpp.gui.machine_recognition.found" : "ae2ltpp.gui.machine_recognition.none");
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, text, bounds.getX() + (bounds.getWidth() - font.width(text)) / 2,
                bounds.getY() + 1, foreground, false);
    }

    @Override
    public Tooltip getTooltip(int mouseX, int mouseY) {
        if (menu.recognizedMachineCount == 0) {
            return new Tooltip(List.of(
                    Component.translatable("ae2ltpp.gui.machine_recognition.missing"),
                    Component.translatable(menu.isWirelessMode()
                            ? "ae2ltpp.gui.machine_recognition.wireless_hint"
                            : "ae2ltpp.gui.machine_recognition.normal_hint")));
        }
        return new Tooltip(List.of(
                Component.translatable("ae2ltpp.gui.machine_recognition.count", menu.recognizedMachineCount)
                        .withStyle(ChatFormatting.GREEN),
                menu.recognizedMachineNames,
                Component.translatable("ae2ltpp.gui.machine_recognition.recipe_hint")
                        .withStyle(ChatFormatting.GRAY)));
    }
}
