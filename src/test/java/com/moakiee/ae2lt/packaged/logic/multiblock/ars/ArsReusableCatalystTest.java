package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import static org.junit.jupiter.api.Assertions.*;
import static com.moakiee.ae2lt.packaged.testsupport.TestPatterns.*;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class ArsReusableCatalystTest {
    @BeforeAll static void bootstrap() {
        MinecraftTestBootstrap.initialize();
        PRODUCT = new ItemStack(Items.DIAMOND, 2);
    }
    private static ItemStack PRODUCT;
    private static GenericStack product(long n) { return new GenericStack(AEItemKey.of(PRODUCT), n); }
    private static GenericStack catalyst(long n) { return new GenericStack(AEItemKey.of(Items.STONE), n); }

    @Test void legacyProductOnlyPatternStillWorks() {
        assertTrue(ArsNouveauImbuementChamberAdapter.outputsMatch(pattern(new GenericStack[]{product(2)}),
                PRODUCT, List.of(AEItemKey.of(Items.STONE))));
    }
    @Test void requiresExactReturnedCatalystCounts() {
        var catalysts = List.of(AEItemKey.of(Items.STONE), AEItemKey.of(Items.STONE));
        assertTrue(ArsNouveauImbuementChamberAdapter.outputsMatch(pattern(new GenericStack[]{product(2), catalyst(2)}), PRODUCT, catalysts));
        for (long n : new long[]{0, 1, 3}) {
            assertFalse(ArsNouveauImbuementChamberAdapter.outputsMatch(pattern(new GenericStack[]{product(2), catalyst(n)}), PRODUCT, catalysts));
        }
    }
    @Test void returnedCatalystNbtCannotBeInvented() {
        var tagged = new ItemStack(Items.STONE);
        tagged.getOrCreateTag().putString("variant", "custom");
        var key = AEItemKey.of(tagged);
        assertFalse(ArsNouveauImbuementChamberAdapter.outputsMatch(pattern(new GenericStack[]{product(2), catalyst(1)}), PRODUCT, List.of(key)));
        assertTrue(ArsNouveauImbuementChamberAdapter.outputsMatch(pattern(new GenericStack[]{product(2), new GenericStack(key, 1)}), PRODUCT, List.of(key)));
    }
    @Test void overlappingProductAndCatalystAmountsAreMerged() {
        assertTrue(ArsNouveauImbuementChamberAdapter.outputsMatch(pattern(new GenericStack[]{product(3)}),
                PRODUCT, List.of(AEItemKey.of(PRODUCT))));
        assertFalse(ArsNouveauImbuementChamberAdapter.outputsMatch(pattern(new GenericStack[]{product(4)}),
                PRODUCT, List.of(AEItemKey.of(PRODUCT))));
    }
    @Test void triesAnotherReagentWhenReturnedCatalystsRejectTheFirstMatch() {
        var match = ArsImbuementInputMatcher.match(List.of("a", "b"), value -> true,
                List.of(value -> true), candidate -> candidate.pedestals().equals(List.of("a")));
        assertNotNull(match);
        assertEquals("b", match.reagent());
        assertEquals(List.of("a"), match.pedestals());
    }
}
