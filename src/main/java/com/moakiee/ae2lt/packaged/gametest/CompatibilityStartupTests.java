package com.moakiee.ae2lt.packaged.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class CompatibilityStartupTests {
    private CompatibilityStartupTests() {
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 40)
    public static void emptyMekmmReplicationConfigIsUsable(GameTestHelper helper) {
        try {
            var validator = Class.forName("com.jerry.mekmm.common.util.ValidatorUtils");
            var parser = validator.getMethod("getRecipeFromConfig", List.class);
            var empty = parser.invoke(null, List.of());
            helper.assertTrue(empty instanceof Map<?, ?> && ((Map<?, ?>) empty).isEmpty(),
                    "MekMM empty replication config still returns null");
            var configured = parser.invoke(null, List.of("minecraft:stone#12"));
            helper.assertTrue(configured instanceof Map<?, ?> && Integer.valueOf(12).equals(
                    ((Map<?, ?>) configured).get("minecraft:stone")), "MekMM configured replication cost changed");
            for (var machine : new String[]{"machine.TileEntityReplicator", "machine.TileEntityFluidReplicator",
                    "factory.TileEntityReplicatingFactory"}) {
                var map = Class.forName("com.jerry.mekmm.common.tile." + machine).getField("customRecipeMap").get(null);
                helper.assertTrue(map instanceof Map<?, ?>, "MekMM replication cache is null: " + machine);
            }
        } catch (ReflectiveOperationException failed) {
            throw new IllegalStateException("MekMM replication regression failed", failed);
        }
        helper.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 40)
    public static void malumConditionalRecipeAdvancementsLoad(GameTestHelper helper) {
        int loaded = 0;
        for (var metal : new String[]{"aluminum", "cobalt", "lead", "nickel", "osmium", "silver", "tin", "uranium", "zinc"}) {
            for (var family : new String[]{"impetus_creation_", "node_focusing_", "smelting", "blasting"}) {
                String tagName = "forge:" + ("impetus_creation_".equals(family) ? "ingots/" : "nuggets/") + metal;
                var tag = TagKey.create(Registries.ITEM, new ResourceLocation(tagName));
                boolean enabled = !ForgeRegistries.ITEMS.tags().getTag(tag).isEmpty();
                String name = family.endsWith("_") ? family + metal : metal + "_from_node_" + family;
                var id = new ResourceLocation("malum", "recipes/" + name);
                var advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(id);
                helper.assertTrue(enabled == (advancement != null), "Malum conditional advancement loading mismatch: " + id);
                if (enabled) {
                    helper.assertTrue(advancement.getCriteria().containsKey("has_input"), "Malum criterion was not restored: " + id);
                    helper.assertTrue(helper.getLevel().getRecipeManager().byKey(new ResourceLocation("malum", name)).isPresent(),
                            "Malum repaired advancement refers to a missing recipe: " + id);
                    loaded++;
                }
            }
        }
        helper.assertTrue(loaded > 0, "Malum advancement regression did not exercise any enabled tag condition");
        helper.succeed();
    }
}
