package com.moakiee.ae2lt.packaged.logic.multiblock.astral;

import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class AstralAbsentGameTests {
    @GameTest(template = "astral_test")
    public static void optionalIntegrationIsAbsentCleanly(GameTestHelper h) {
        h.assertTrue(!ModList.get().isLoaded("astralsorcery"), "Astral absent from actual mod list");
        h.assertTrue(MultiblockAdapterRegistry.activeAdapters().stream().noneMatch(a -> a instanceof AstralSorceryAdapter), "no active Astral integration");
        h.assertTrue(PackagedCoreDefinition.visibleWhen(id -> ModList.get().isLoaded(id)).stream()
                .noneMatch(c -> c.targetModId().equals("astralsorcery")), "no visible Astral cores");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("ae2ltpp", "packaged_core/astral_illumination_packaged_core")).isEmpty(),
                "native Astral recipe skipped when target mod absent");
        h.succeed();
    }
}
