package com.moakiee.ae2lt.packaged.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

class MalumRecipeAdvancementCompatTest {
    private static final String MISSING = """
            {"advancements":[{"conditions":[{"type":"forge:not","value":{
            "type":"forge:tag_empty","tag":"forge:ingots/tin"}}]}]}
            """;
    private static final String EMPTY = """
            {"advancements":[{"conditions":[],"advancement":{
            "criteria":{},"requirements":[],"sends_telemetry_event":false}}]}
            """;

    @Test
    void restoresMissingInfusionCriteriaAndRecipeRewardWithoutChangingConditionsOrSource() {
        var original = JsonParser.parseString(MISSING);
        var snapshot = original.deepCopy();
        var repaired = repair("malum:recipes/impetus_creation_tin", original);
        assertNotSame(original, repaired);
        assertEquals(snapshot, original);
        assertEquals(original.getAsJsonObject().getAsJsonArray("advancements").get(0).getAsJsonObject().get("conditions"),
                repaired.getAsJsonObject().getAsJsonArray("advancements").get(0).getAsJsonObject().get("conditions"));
        assertInputAndReward(repaired, "malum:alchemical_impetus", "malum:impetus_creation_tin");
    }

    @Test
    void focusingUnlocksFromItsOwnMetalImpetus() {
        assertInputAndReward(repair("malum:recipes/node_focusing_osmium", JsonParser.parseString(MISSING)),
                "malum:osmium_impetus", "malum:node_focusing_osmium");
    }

    @Test
    void smeltingAndBlastingUnlockFromTheMatchingNodeAndRetainTelemetrySetting() {
        for (var process : new String[]{"smelting", "blasting"}) {
            var repaired = repair("malum:recipes/tin_from_node_" + process, JsonParser.parseString(EMPTY));
            assertInputAndReward(repaired, "malum:tin_node", "malum:tin_from_node_" + process);
            assertFalse(advancement(repaired).get("sends_telemetry_event").getAsBoolean());
        }
    }

    @Test
    void repairedResourcesAreIdempotent() {
        var repaired = repair("malum:recipes/impetus_creation_tin", JsonParser.parseString(MISSING));
        assertSame(repaired, repair("malum:recipes/impetus_creation_tin", repaired));
    }

    @Test
    void leavesHealthyAndCustomAdvancementsUntouched() {
        for (var json : new String[]{
                "{\"advancements\":[{\"conditions\":[],\"advancement\":{\"criteria\":{\"custom\":{}},\"requirements\":[[\"custom\"]]}}]}",
                "{\"advancements\":[{\"conditions\":[],\"advancement\":{\"criteria\":{},\"requirements\":[],\"display\":{}}}]}",
                "{\"advancements\":[{\"conditions\":[],\"custom\":true}]}",
                "{\"criteria\":{},\"requirements\":[]}"}) {
            var original = JsonParser.parseString(json);
            assertSame(original, repair("malum:recipes/impetus_creation_tin", original));
        }
    }

    @Test
    void restrictsRepairToKnownMalumGeneratedRecipeIds() {
        var original = JsonParser.parseString(MISSING);
        for (var id : new String[]{"other:recipes/impetus_creation_tin", "malum:custom/impetus_creation_tin",
                "malum:recipes/impetus_creation_unknown", "malum:recipes/impetus_creation_iron",
                "malum:recipes/tin_from_node_custom"}) {
            assertSame(original, repair(id, original));
        }
    }

    @Test
    void toleratesUnexpectedJsonShapesWithoutHidingUpstreamErrors() {
        for (var json : new String[]{"null", "[]", "{\"advancements\":{}}",
                "{\"advancements\":[null,[],{\"conditions\":{}},{\"conditions\":[],\"advancement\":null}]}",
                "{\"advancements\":[{\"conditions\":[],\"advancement\":{\"criteria\":[],\"requirements\":[]}}]}"}) {
            var original = JsonParser.parseString(json);
            assertSame(original, repair("malum:recipes/impetus_creation_tin", original));
        }
    }

    @Test
    void repairsOnlyBrokenBranchesAndPreservesAlternativeOrder() {
        var original = JsonParser.parseString(MISSING).getAsJsonObject();
        var valid = JsonParser.parseString("{\"conditions\":[],\"advancement\":{\"criteria\":{\"custom\":{}}}}");
        original.getAsJsonArray("advancements").add(valid);
        var repaired = repair("malum:recipes/impetus_creation_tin", original).getAsJsonObject();
        assertEquals(2, repaired.getAsJsonArray("advancements").size());
        assertEquals(valid, repaired.getAsJsonArray("advancements").get(1));
    }

    private static JsonElement repair(String id, JsonElement resource) {
        return MalumRecipeAdvancementCompat.repair(new ResourceLocation(id), resource);
    }

    private static com.google.gson.JsonObject advancement(JsonElement resource) {
        return resource.getAsJsonObject().getAsJsonArray("advancements").get(0).getAsJsonObject()
                .getAsJsonObject("advancement");
    }

    private static void assertInputAndReward(JsonElement resource, String input, String recipe) {
        var advancement = advancement(resource);
        var criterion = advancement.getAsJsonObject("criteria").getAsJsonObject("has_input");
        assertEquals("minecraft:inventory_changed", criterion.get("trigger").getAsString());
        assertEquals(input, criterion.getAsJsonObject("conditions").getAsJsonArray("items").get(0)
                .getAsJsonObject().getAsJsonArray("items").get(0).getAsString());
        assertEquals("has_input", advancement.getAsJsonArray("requirements").get(0).getAsJsonArray().get(0).getAsString());
        assertEquals(recipe, advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
    }
}
