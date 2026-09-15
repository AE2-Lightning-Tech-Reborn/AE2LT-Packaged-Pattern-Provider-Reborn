package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class OccultismReflectionFieldLookupTest {

    @Test
    void resolvesPrivateFieldFromSuperclass() throws ReflectiveOperationException {
        var reflection = Class.forName(
                "com.moakiee.ae2lt.packaged.logic.multiblock.occultism."
                        + "OccultismRitualAdapter$OccultismReflection");
        Method tryField = reflection.getDeclaredMethod("tryField", Class.class, String.class);
        tryField.setAccessible(true);

        Field field = (Field) tryField.invoke(null, ChildState.class, "provided");

        assertNotNull(field);
        var state = new ChildState();
        field.setBoolean(state, true);
        assertEquals(true, field.getBoolean(state));
    }

    private static class ParentState {
        @SuppressWarnings("unused")
        private boolean provided;
    }

    private static final class ChildState extends ParentState {
    }
}
