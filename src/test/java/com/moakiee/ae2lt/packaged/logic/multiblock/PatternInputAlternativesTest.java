package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

class PatternInputAlternativesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void retainsEveryConsistentAlternativeCombination() {
        var pattern = pattern(
                input(1, stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 1)),
                input(2, stack(Items.REDSTONE, 1), stack(Items.LAPIS_LAZULI, 1)));

        var variants = PatternInputAlternatives.expandItemAlternatives(pattern);

        assertEquals(4, variants.size());
        assertEquals(1L, variants.get(0).get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(2L, variants.get(0).get(AEItemKey.of(Items.REDSTONE)));
        assertEquals(1L, variants.get(3).get(AEItemKey.of(Items.GOLD_INGOT)));
        assertEquals(2L, variants.get(3).get(AEItemKey.of(Items.LAPIS_LAZULI)));
    }

    @Test
    void mergesMatchingKeysWithinAnAlternativeChoice() {
        var pattern = pattern(
                input(1, stack(Items.IRON_INGOT, 1)),
                input(3, stack(Items.IRON_INGOT, 2)));

        var variants = PatternInputAlternatives.expandItemAlternatives(pattern);

        assertEquals(1, variants.size());
        assertEquals(7L, variants.get(0).get(AEItemKey.of(Items.IRON_INGOT)));
    }

    @Test
    void rejectsAlternativeExpansionBeyondFixedBudget() {
        var choices = new GenericStack[] {
                stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 1),
                stack(Items.REDSTONE, 1), stack(Items.LAPIS_LAZULI, 1)
        };
        var inputs = new IPatternDetails.IInput[7];
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = input(1, choices);
        }

        assertNull(PatternInputAlternatives.expandItemAlternatives(pattern(inputs)));
    }

    @Test
    void expandsAggregateChoicesIntoSlotUnits() {
        var pattern = pattern(
                input(2, stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 1)),
                input(1, stack(Items.REDSTONE, 1)));

        var variants = PatternInputAlternatives.expandItemUnitAlternatives(pattern, 3);

        assertEquals(List.of(AEItemKey.of(Items.IRON_INGOT), AEItemKey.of(Items.IRON_INGOT),
                AEItemKey.of(Items.REDSTONE)), variants.get(0));
        assertEquals(List.of(AEItemKey.of(Items.GOLD_INGOT), AEItemKey.of(Items.GOLD_INGOT),
                AEItemKey.of(Items.REDSTONE)), variants.get(1));
    }

    private static IPatternDetails pattern(IPatternDetails.IInput... inputs) {
        return (IPatternDetails) Proxy.newProxyInstance(IPatternDetails.class.getClassLoader(),
                new Class<?>[] {IPatternDetails.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getInputs" -> inputs;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static IPatternDetails.IInput input(long multiplier, GenericStack... possible) {
        return (IPatternDetails.IInput) Proxy.newProxyInstance(IPatternDetails.IInput.class.getClassLoader(),
                new Class<?>[] {IPatternDetails.IInput.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPossibleInputs" -> possible;
                    case "getMultiplier" -> multiplier;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static GenericStack stack(Item item, long amount) {
        return new GenericStack(AEItemKey.of(item), amount);
    }
}
