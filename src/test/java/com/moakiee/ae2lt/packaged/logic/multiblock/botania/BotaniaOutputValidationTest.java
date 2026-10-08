package com.moakiee.ae2lt.packaged.logic.multiblock.botania;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import com.moakiee.ae2lt.packaged.testsupport.TestPatterns;

class BotaniaOutputValidationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void multipleOutputsMayBeReordered() {
        var result = new ItemStack(Items.DIAMOND, 2);
        var remainder = new ItemStack(Items.BUCKET, 1);
        assertTrue(BotaniaRecipeLookup.validatePatternMultiOutput(
                pattern(output(remainder), output(result)), List.of(result, remainder)));
    }

    @Test
    void duplicateOutputsRequireSeparateMatchingStacks() {
        var result = new ItemStack(Items.DIAMOND, 2);
        var remainder = new ItemStack(Items.BUCKET, 1);
        var duplicates = pattern(output(result), output(result));
        assertTrue(BotaniaRecipeLookup.validatePatternMultiOutput(duplicates, List.of(result, result)));
        assertFalse(BotaniaRecipeLookup.validatePatternMultiOutput(duplicates, List.of(result, remainder)));
    }

    @Test
    void outputCountsCannotBeCombinedAcrossEntries() {
        var result = new ItemStack(Items.DIAMOND, 2);
        assertFalse(BotaniaRecipeLookup.validatePatternMultiOutput(
                pattern(output(new ItemStack(Items.DIAMOND, 1)), output(new ItemStack(Items.DIAMOND, 3))),
                List.of(result, result)));
    }

    @Test
    void strictOutputsPreserveNbt() {
        var result = new ItemStack(Items.DIAMOND, 1);
        result.getOrCreateTag().putString("variant", "recipe");
        var other = result.copy();
        other.getOrCreateTag().putString("variant", "pattern");
        var matching = pattern(output(result));
        var mismatching = pattern(output(other));
        assertTrue(BotaniaRecipeLookup.validatePatternBatchOutput(matching, result, 1));
        assertFalse(BotaniaRecipeLookup.validatePatternBatchOutput(mismatching, result, 1));
        assertTrue(BotaniaRecipeLookup.validatePatternMultiOutput(matching, List.of(result)));
        assertFalse(BotaniaRecipeLookup.validatePatternMultiOutput(mismatching, List.of(result)));
    }

    @Test
    void idOnlyOutputsStillRequireTheCorrectItemAndCount() {
        var result = new ItemStack(Items.DIAMOND, 1);
        result.getOrCreateTag().putString("variant", "recipe");
        var fuzzy = TestPatterns.pattern(new GenericStack[] {output(new ItemStack(Items.DIAMOND, 1))},
                new IPatternDetails.IInput[0], Set.of(), true);
        assertTrue(BotaniaRecipeLookup.validatePatternBatchOutput(fuzzy, result, 1));
        assertTrue(BotaniaRecipeLookup.validatePatternMultiOutput(fuzzy, List.of(result)));
        assertFalse(BotaniaRecipeLookup.validatePatternBatchOutput(fuzzy, result, 2));
        assertFalse(BotaniaRecipeLookup.validatePatternMultiOutput(
                fuzzy, List.of(new ItemStack(Items.BUCKET, 1))));
    }

    @Test
    void batchValidationRequiresOneOutputWithTheExactTotal() {
        var result = new ItemStack(Items.DIAMOND, 2);
        var batch = pattern(new GenericStack(AEItemKey.of(result), 6));
        assertTrue(BotaniaRecipeLookup.validatePatternBatchOutput(batch, result, 6));
        assertFalse(BotaniaRecipeLookup.validatePatternBatchOutput(batch, result, 2));
        assertFalse(BotaniaRecipeLookup.validatePatternBatchOutput(pattern(), result, 6));
        assertFalse(BotaniaRecipeLookup.validatePatternBatchOutput(
                pattern(output(result), output(result)), result, 4));
    }

    @Test
    void invalidOutputShapesAreRejected() {
        var result = new ItemStack(Items.DIAMOND, 1);
        var itemPattern = pattern(output(result));
        var fluidPattern = pattern(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000));
        assertFalse(BotaniaRecipeLookup.validatePatternMultiOutput(itemPattern, List.of()));
        assertFalse(BotaniaRecipeLookup.validatePatternMultiOutput(pattern(), List.of(result)));
        assertFalse(BotaniaRecipeLookup.validatePatternMultiOutput(itemPattern, List.of(ItemStack.EMPTY)));
        assertFalse(BotaniaRecipeLookup.validatePatternMultiOutput(fluidPattern, List.of(result)));
        assertFalse(BotaniaRecipeLookup.validatePatternBatchOutput(fluidPattern, result, 1000));
    }

    private static GenericStack output(ItemStack stack) {
        return new GenericStack(AEItemKey.of(stack), stack.getCount());
    }

    private static IPatternDetails pattern(GenericStack... outputs) {
        return TestPatterns.pattern(outputs);
    }
}
