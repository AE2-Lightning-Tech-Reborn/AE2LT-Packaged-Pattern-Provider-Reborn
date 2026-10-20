package com.moakiee.ae2lt.packaged.item;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PackagedCoreRecipeCostTest {
    private static final Path DATA = Path.of("src/main/resources/data/ae2ltpp");
    private static final Map<String, String> PREVIOUS = Map.of(
            "ec_advanced_table", "ec_basic_table",
            "ec_elite_table", "ec_advanced_table",
            "ec_ultimate_table", "ec_elite_table",
            "avaritia_nether_table", "avaritia_sculk_table",
            "avaritia_end_table", "avaritia_nether_table",
            "avaritia_extreme_table", "avaritia_end_table",
            "astral_resonance", "astral_illumination",
            "astral_luminance", "astral_resonance",
            "astral_radiance", "astral_luminance");

    @Test
    void nativeAndProviderUpgradeRecipesBothRequireImmediatePreviousCore() throws IOException {
        for (var entry : PREVIOUS.entrySet()) {
            var filename = entry.getKey() + "_packaged_core.json";
            var required = "ae2ltpp:" + entry.getValue() + "_packaged_core";
            assertEquals(Set.of(required), coreInputs(read(DATA.resolve("recipe/packaged_core/" + filename))), filename);
            var upgrade = read(DATA.resolve("recipe/core_upgrade/" + filename));
            assertEquals(required, upgrade.getAsJsonObject().getAsJsonObject("required_core").get("id").getAsString(), filename);
        }
    }

    @Test
    void everyDataDefinedFirstMachineCoreConsumesBasicCore() throws IOException {
        try (var files = Files.list(DATA.resolve("recipe/packaged_core"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var name = file.getFileName().toString().replace("_packaged_core.json", "");
                if (PREVIOUS.containsKey(name)) continue;
                // Raven's fixed-cost recipe is checked against native runtime inputs in NativeGameTests.
                if (name.equals("apothic_infusion")) continue;
                assertEquals(Set.of("ae2ltpp:basic_packaged_core"), coreInputs(read(file)), name);
            }
        }
        assertEquals(Set.of("ae2ltpp:basic_packaged_core"), coreInputs(read(DATA.resolve(
                "forbidden_arcanus/hephaestus_forge/ritual/fa_hephaestus_forge_packaged_core.json"))));
    }

    private static JsonElement read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file));
    }

    private static Set<String> coreInputs(JsonElement json) {
        var cores = new HashSet<String>();
        collectInputs(json, cores);
        return cores;
    }

    private static void collectInputs(JsonElement json, Set<String> cores) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(value -> collectInputs(value, cores));
        } else if (json.isJsonObject()) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                if (Set.of("result", "output", "outputs").contains(entry.getKey())) continue;
                var value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && (entry.getKey().equals("item") || entry.getKey().equals("id"))
                        && value.getAsString().startsWith("ae2ltpp:") && value.getAsString().endsWith("_packaged_core")) {
                    cores.add(value.getAsString());
                } else collectInputs(value, cores);
            }
        }
    }
}
