package com.moakiee.ae2lt.packaged.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class MekmmRecipeConfigMixinTest {
    @Test
    void replacesNullWithIndependentMutableEmptyMaps() throws ReflectiveOperationException {
        var first = normalize(null);
        var second = normalize(null);
        assertNotSame(first, second);
        assertTrue(first.isEmpty());
        first.put("minecraft:stone", 12);
        assertTrue(second.isEmpty());
    }

    @Test
    void keepsConfiguredRecipesAndOriginalMapIdentity() throws ReflectiveOperationException {
        var configured = new HashMap<String, Integer>();
        configured.put("minecraft:stone", 12);
        assertSame(configured, normalize(configured));
        assertEquals(12, configured.get("minecraft:stone"));
    }

    @Test
    void keepsAlreadyEmptyMaps() throws ReflectiveOperationException {
        var configured = new HashMap<String, Integer>();
        assertSame(configured, normalize(configured));
    }

    private static HashMap<String, Integer> normalize(HashMap<String, Integer> map) throws ReflectiveOperationException {
        var callback = new CallbackInfoReturnable<HashMap<String, Integer>>("getRecipeFromConfig", true, map);
        var method = MekmmRecipeConfigMixin.class.getDeclaredMethod("ae2ltpp$keepEmptyReplicationConfigUsable",
                List.class, CallbackInfoReturnable.class);
        method.setAccessible(true);
        method.invoke(null, List.of(), callback);
        return callback.getReturnValue();
    }
}
