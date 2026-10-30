package com.moakiee.ae2lt.packaged.menu;

import net.minecraft.network.chat.Component;

/** Server-selected core destination shown by any AE2 confirmation screen. */
public interface CoreOrderConfirmation {
    String ae2ltpp$coreMode();
    Component ae2ltpp$coreDescription();
    boolean ae2ltpp$coreBlocked();
}
