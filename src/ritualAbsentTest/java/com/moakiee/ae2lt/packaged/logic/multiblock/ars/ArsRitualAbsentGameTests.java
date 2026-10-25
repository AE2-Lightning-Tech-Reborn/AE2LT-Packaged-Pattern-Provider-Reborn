package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ArsRitualAbsentGameTests {
    @GameTest(template = "chisel_test")
    public static void absentArsLoadsWithoutRitualHooksOrRecipe(GameTestHelper h) {
        h.assertTrue(!net.neoforged.fml.ModList.get().isLoaded("ars_nouveau"), "Ars is absent");
        h.assertTrue(!ArsRitualAccess.isTile(null), "optional reflection does not load missing mod");
        h.assertTrue(PackagedCoreDefinition.visibleWhen(net.neoforged.fml.ModList.get()::isLoaded).stream()
                .noneMatch(c -> c.itemId().equals("ars_ritual_brazier_packaged_core")), "ritual core hidden without Ars");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                "ae2ltpp:packaged_core/ars_ritual_brazier_packaged_core")).isEmpty(), "recipe conditional");
        h.succeed();
    }
}
