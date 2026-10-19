package com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NaturesAuraInputMatchingTest {
    @BeforeAll
    static void bootstrap() { MinecraftTestBootstrap.initialize(); }

    @Test
    void impossibleWideOfferingInputFailsWithinTheSearchBudget() {
        var items = List.of(Items.STONE, Items.DIRT, Items.COBBLESTONE, Items.SAND,
                Items.GRAVEL, Items.OAK_LOG, Items.OAK_PLANKS, Items.OAK_SAPLING,
                Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.IRON_INGOT, Items.GOLD_INGOT,
                Items.COPPER_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL);
        var input = Ingredient.of(items.stream().map(item -> new ItemStack(item, 16)));
        var recipe = new Offering(input, Ingredient.of(Items.DIAMOND));
        var supplied = new ArrayList<AEItemKey>(items.stream().map(AEItemKey::of).toList());
        supplied.add(AEItemKey.of(Items.EMERALD));
        assertTimeout(Duration.ofSeconds(1), () -> assertNull(
                new NaturesAuraAdapter(NaturesAuraAdapter.Kind.OFFERING).assign(recipe, supplied)));
    }

    @Test
    void offeringRequiresHomogeneousMaterialsAndOnePaidStart() {
        var recipe = new Offering(Ingredient.of(new ItemStack(Items.IRON_INGOT, 16)), Ingredient.of(Items.DIAMOND));
        var supplied = new ArrayList<>(Collections.nCopies(16, AEItemKey.of(Items.IRON_INGOT)));
        supplied.add(AEItemKey.of(Items.DIAMOND));
        var adapter = new NaturesAuraAdapter(NaturesAuraAdapter.Kind.OFFERING);
        assertEquals(supplied, adapter.assign(recipe, supplied));
        supplied.set(0, AEItemKey.of(Items.GOLD_INGOT));
        assertNull(adapter.assign(recipe, supplied));
    }

    private static final class Offering extends ShapelessRecipe {
        public final Ingredient input;
        public final Ingredient startItem;

        Offering(Ingredient input, Ingredient startItem) {
            super(new ResourceLocation("ae2ltpp", "test_offering"), "",
                    new ItemStack(Items.EMERALD), NonNullList.create());
            this.input = input;
            this.startItem = startItem;
        }
    }
}
