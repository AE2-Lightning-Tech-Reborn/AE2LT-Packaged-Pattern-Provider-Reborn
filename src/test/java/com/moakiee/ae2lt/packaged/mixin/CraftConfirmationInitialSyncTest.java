package com.moakiee.ae2lt.packaged.mixin;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class CraftConfirmationInitialSyncTest {
    @Test
    void absentDestinationSnapshotPreservesNativeConfirmation() throws Exception {
        var state = new CraftConfirmMenuMixin() {};
        set(state, "ae2ltpp$coreMode", null);
        set(state, "ae2ltpp$coreDescription", null);
        assertEquals("", state.ae2ltpp$coreMode());
        assertEquals("", state.ae2ltpp$coreDescription().getString());
        assertFalse(state.ae2ltpp$coreBlocked());
    }

    @Test
    void selectedDestinationAndBlockingSurviveTheView() throws Exception {
        var state = new CraftConfirmMenuMixin() {};
        var description = Component.literal("Already selected destination");
        set(state, "ae2ltpp$coreMode", "install_core");
        set(state, "ae2ltpp$coreDescription", description);
        set(state, "ae2ltpp$coreBlocked", true);
        assertEquals("install_core", state.ae2ltpp$coreMode());
        assertSame(description, state.ae2ltpp$coreDescription());
        assertTrue(state.ae2ltpp$coreBlocked());
    }

    @Test
    void aClearedSnapshotDoesNotRetainThePreviousDestination() throws Exception {
        var state = new CraftConfirmMenuMixin() {};
        set(state, "ae2ltpp$coreMode", "install_core");
        set(state, "ae2ltpp$coreDescription", Component.literal("Old destination"));
        set(state, "ae2ltpp$coreMode", null);
        set(state, "ae2ltpp$coreDescription", null);
        assertEquals("", state.ae2ltpp$coreMode());
        assertEquals("", state.ae2ltpp$coreDescription().getString());
    }

    private static void set(Object state, String name, Object value) throws Exception {
        var field = CraftConfirmMenuMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(state, value);
    }
}
