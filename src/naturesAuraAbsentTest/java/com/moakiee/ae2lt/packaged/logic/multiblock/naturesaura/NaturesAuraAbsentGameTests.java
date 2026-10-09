package com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura;

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
public final class NaturesAuraAbsentGameTests {
    @GameTest(template = "natures_aura_test")
    public static void noTargetModNoActiveIntegration(GameTestHelper h) {
        h.assertTrue(!ModList.get().isLoaded("naturesaura"), "Nature's Aura absent");
        h.assertTrue(MultiblockAdapterRegistry.activeAdapters().stream().noneMatch(NaturesAuraAdapter.class::isInstance), "no active integration");
        h.assertTrue(PackagedCoreDefinition.visibleWhen(id -> ModList.get().isLoaded(id)).stream().noneMatch(c -> c.targetModId().equals("naturesaura")), "cores hidden");
        for (var type : java.util.List.of("altar", "tree_ritual", "offering")) {
            h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("ae2ltpp",
                    "packaged_core/naturesaura_" + type + "_packaged_core")).isEmpty(), "conditional recipe skipped: " + type);
        }
        h.succeed();
    }
}
