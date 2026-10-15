package com.moakiee.ae2lt.packaged.patternprovider;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.Items;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

class UnlimitedPatternProviderReturnInventoryTest {
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.initialize(); }
    @Test void nearLimitReturnsExactReceiptsWithoutSaturatingAcceptedInputs() {
        var inventory = UnlimitedPatternProviderReturnInventory.create(() -> {}, null);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        assertEquals(Long.MAX_VALUE - 3, inventory.insert(0, iron, Long.MAX_VALUE - 3, Actionable.MODULATE));
        assertEquals(3, inventory.insert(0, iron, 9, Actionable.SIMULATE));
        assertEquals(Long.MAX_VALUE - 3, inventory.getAmount(0));
        assertEquals(3, inventory.insert(0, iron, 9, Actionable.MODULATE));
        assertEquals(Long.MAX_VALUE, inventory.getAmount(0));
        assertEquals(0, inventory.insert(0, iron, 1, Actionable.MODULATE));
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        assertEquals(7, inventory.insert(0, gold, 7, Actionable.MODULATE));
    }
}
