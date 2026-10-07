package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

class ReflectionSupportTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void restoresAbsentTagAfterSuccessfulHook() {
        var original = ItemStack.EMPTY.getTag();
        try {
            ItemStack.EMPTY.setTag(null);
            var result = ReflectionSupport.withEmptyStackTagPreserved(() -> {
                ItemStack.EMPTY.getOrCreateTag().putString("mutation", "changed");
                return "result";
            });
            assertEquals("result", result);
            assertNull(ItemStack.EMPTY.getTag());
        } finally {
            ItemStack.EMPTY.setTag(original);
        }
    }

    @Test
    void restoresNestedTagAfterInPlaceMutation() {
        var original = ItemStack.EMPTY.getTag();
        try {
            var tag = new CompoundTag();
            var nested = new CompoundTag();
            nested.putString("value", "original");
            tag.put("nested", nested);
            var expected = tag.copy();
            ItemStack.EMPTY.setTag(tag);
            ReflectionSupport.withEmptyStackTagPreserved(() -> {
                ItemStack.EMPTY.getOrCreateTag().getCompound("nested").putString("value", "changed");
                return null;
            });
            assertEquals(expected, ItemStack.EMPTY.getTag());
        } finally {
            ItemStack.EMPTY.setTag(original);
        }
    }

    @Test
    void restoresTagAndPropagatesHookException() {
        var original = ItemStack.EMPTY.getTag();
        try {
            var tag = new CompoundTag();
            tag.putString("value", "original");
            var expected = tag.copy();
            ItemStack.EMPTY.setTag(tag);
            var failure = new IllegalStateException("hook failed");
            var thrown = assertThrows(IllegalStateException.class,
                    () -> ReflectionSupport.withEmptyStackTagPreserved(() -> {
                        ItemStack.EMPTY.getOrCreateTag().putString("value", "changed");
                        throw failure;
                    }));
            assertEquals(failure, thrown);
            assertEquals(expected, ItemStack.EMPTY.getTag());
        } finally {
            ItemStack.EMPTY.setTag(original);
        }
    }
}
