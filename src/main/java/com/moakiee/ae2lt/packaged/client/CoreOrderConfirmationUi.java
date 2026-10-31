package com.moakiee.ae2lt.packaged.client;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import com.moakiee.ae2lt.packaged.menu.CoreOrderConfirmation;
import com.moakiee.ae2lt.packaged.mixin.client.CraftConfirmScreenAccessor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class CoreOrderConfirmationUi {
    private record ButtonState(Component label, Tooltip tooltip, int width, Component description) {}
    private static final WeakHashMap<Button, ButtonState> BUTTONS = new WeakHashMap<>();
    private CoreOrderConfirmationUi() {}

    // Run after all native/LT render updates, which also set the shared start button's active state.
    public static void update(CraftConfirmScreen screen) {
        if (!(screen.getMenu() instanceof CoreOrderConfirmation state)) return;
        var start = ((CraftConfirmScreenAccessor) screen).ae2ltpp$startButton();
        String mode = state.ae2ltpp$coreMode();
        if (mode == null || mode.isEmpty()) {
            var previous = BUTTONS.remove(start);
            if (previous != null) {
                start.setMessage(previous.label());
                start.setTooltip(previous.tooltip());
                start.setX(start.getX() + start.getWidth() - previous.width());
                start.setWidth(previous.width());
            }
            return;
        }
        var previous = BUTTONS.computeIfAbsent(start,
                button -> new ButtonState(button.getMessage(), button.getTooltip(), button.getWidth(), null));
        var label = Component.translatable("ae2ltpp.terminal." + mode);
        start.setMessage(label);
        int width = Math.max(previous.width(), Minecraft.getInstance().font.width(label) + 12);
        start.setX(start.getX() + start.getWidth() - width);
        start.setWidth(width);
        var description = state.ae2ltpp$coreDescription();
        if (description == null) description = Component.empty();
        if (!description.equals(previous.description())) {
            start.setTooltip(Tooltip.create(description));
            BUTTONS.put(start, new ButtonState(previous.label(), previous.tooltip(), previous.width(), description));
        }
        if (state.ae2ltpp$coreBlocked()) start.active = false;
    }
}
