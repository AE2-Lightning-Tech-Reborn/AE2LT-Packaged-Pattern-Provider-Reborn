package com.moakiee.ae2lt.packaged.logic.multiblock.botania;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;

import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PetalVariantCapacityTest {
    private static Method consume;

    @BeforeAll
    static void bootstrap() throws Exception {
        MinecraftTestBootstrap.initialize();
        consume = PetalApothecaryAdapter.class.getDeclaredMethod(
                "consumeIngredientsStrict", List.class, long.class, LinkedHashMap.class);
        consume.setAccessible(true);
    }

    @Test
    void genericBatchCanUseDifferentNbtVariantsWithoutPoolingStrictCounts() throws Exception {
        var named = named();
        var available = inputs(named, 1, 1);
        assertFalse(consume(List.of(StrictNBTIngredient.of(named)), 2, available));
        assertEquals(inputs(named, 1, 1), available, "failed matching must not debit borrowed inputs");
        assertTrue(consume(List.of(Ingredient.of(Items.IRON_INGOT)), 2, available));
        assertTrue(available.isEmpty());
    }

    @Test
    void broadIngredientCanMoveAsideForStrictIngredientInEitherOrder() throws Exception {
        var named = named();
        var broad = Ingredient.of(Items.IRON_INGOT);
        var strict = StrictNBTIngredient.of(named);
        for (var requirements : List.of(List.of(broad, strict), List.of(strict, broad))) {
            var available = inputs(named, 3, 1);
            assertTrue(consume(requirements, 2, available));
            assertTrue(available.isEmpty());
        }
    }

    @Test
    void hugeBatchesKeepLongCountsAndExactLeftovers() throws Exception {
        var named = named();
        var available = inputs(named, Long.MAX_VALUE, 1);
        assertTrue(consume(List.of(Ingredient.of(Items.IRON_INGOT)), Integer.MAX_VALUE, available));
        assertEquals(Long.MAX_VALUE - Integer.MAX_VALUE, available.get(AEItemKey.of(named)));
        assertEquals(1L, available.get(AEItemKey.of(Items.IRON_INGOT)));
    }

    @Test
    void competingIngredientsCannotReuseOneVariantOrLosePartialAssignments() throws Exception {
        var named = named();
        var available = inputs(named, 3, 1);
        assertFalse(consume(List.of(Ingredient.of(Items.IRON_INGOT), StrictNBTIngredient.of(named)), 3, available));
        assertEquals(inputs(named, 3, 1), available);
    }

    private static ItemStack named() {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.getOrCreateTag().putString("variant", "special");
        return stack;
    }

    private static LinkedHashMap<AEItemKey, Long> inputs(ItemStack named, long special, long plain) {
        var available = new LinkedHashMap<AEItemKey, Long>();
        available.put(AEItemKey.of(named), special);
        available.put(AEItemKey.of(Items.IRON_INGOT), plain);
        return available;
    }

    private static boolean consume(List<Ingredient> ingredients, long copies,
                                   LinkedHashMap<AEItemKey, Long> available) throws Exception {
        return (boolean) consume.invoke(null, ingredients, copies, available);
    }
}
