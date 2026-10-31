package com.moakiee.ae2lt.packaged.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moakiee.ae2lt.packaged.mixin.CraftConfirmMenuMixin;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class CoreOrderConfirmationStateTest {
    @Test
    void missingClientStateCanBeReadBeforeTheFirstSync() throws ReflectiveOperationException {
        var state = new CraftConfirmMenuMixin() {};
        set(state, "ae2ltpp$coreMode", null);
        set(state, "ae2ltpp$coreDescription", null);

        assertEquals("", state.ae2ltpp$coreMode());
        assertEquals(Component.empty(), state.ae2ltpp$coreDescription());
        assertFalse(state.ae2ltpp$coreBlocked());
    }

    @Test
    void modeArrivingBeforeDescriptionStillHasASafeTooltip() throws ReflectiveOperationException {
        var state = new CraftConfirmMenuMixin() {};
        set(state, "ae2ltpp$coreMode", "install");
        set(state, "ae2ltpp$coreDescription", null);

        assertEquals("install", state.ae2ltpp$coreMode());
        assertEquals(Component.empty(), state.ae2ltpp$coreDescription());
    }

    @Test
    void synchronizedValuesRemainIntact() throws ReflectiveOperationException {
        var state = new CraftConfirmMenuMixin() {};
        var description = Component.literal("Core destination");
        set(state, "ae2ltpp$coreMode", "upgrade");
        set(state, "ae2ltpp$coreDescription", description);
        set(state, "ae2ltpp$coreBlocked", true);

        assertEquals("upgrade", state.ae2ltpp$coreMode());
        assertSame(description, state.ae2ltpp$coreDescription());
        assertTrue(state.ae2ltpp$coreBlocked());
    }

    private static void set(CraftConfirmMenuMixin state, String name, Object value)
            throws ReflectiveOperationException {
        var field = CraftConfirmMenuMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(state, value);
    }
}
