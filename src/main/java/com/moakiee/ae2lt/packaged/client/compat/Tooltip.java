package com.moakiee.ae2lt.packaged.client.compat;
import net.minecraft.network.chat.Component;
public record Tooltip(Component text) {
    public static Tooltip create(Component text) { return new Tooltip(text); }
}
