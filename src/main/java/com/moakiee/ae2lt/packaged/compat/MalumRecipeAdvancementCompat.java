package com.moakiee.ae2lt.packaged.compat;

import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

public final class MalumRecipeAdvancementCompat {
    private static final Pattern RECIPE_PATH = Pattern.compile(
            "recipes/(?:(impetus_creation_|node_focusing_)(aluminum|cobalt|lead|nickel|osmium|silver|tin|uranium|zinc)"
                    + "|(aluminum|cobalt|lead|nickel|osmium|silver|tin|uranium|zinc)_from_node_(smelting|blasting))");
    private static final Set<String> EMPTY_ADVANCEMENT_FIELDS = Set.of("criteria", "requirements", "sends_telemetry_event");

    private MalumRecipeAdvancementCompat() {
    }

    public static JsonElement repair(ResourceLocation id, JsonElement resource) {
        if (!"malum".equals(id.getNamespace()) || !resource.isJsonObject()) {
            return resource;
        }
        var recipePath = RECIPE_PATH.matcher(id.getPath());
        if (!recipePath.matches()) {
            return resource;
        }
        var original = resource.getAsJsonObject();
        var alternatives = original.get("advancements");
        if (alternatives == null || !alternatives.isJsonArray()) {
            return resource;
        }
        JsonObject repaired = null;
        for (int index = 0; index < alternatives.getAsJsonArray().size(); index++) {
            var alternative = alternatives.getAsJsonArray().get(index);
            if (!alternative.isJsonObject()) {
                continue;
            }
            var branch = alternative.getAsJsonObject();
            if (!branch.has("conditions") || !branch.get("conditions").isJsonArray()) {
                continue;
            }
            var advancement = branch.get("advancement");
            if (advancement == null) {
                if (!branch.keySet().equals(Set.of("conditions"))) {
                    continue;
                }
            } else if (!isEmptyGeneratedAdvancement(advancement)) {
                continue;
            }
            if (repaired == null) {
                repaired = original.deepCopy();
            }
            var fixed = advancement == null ? new JsonObject() : advancement.getAsJsonObject().deepCopy();
            String inputItem = "impetus_creation_".equals(recipePath.group(1)) ? "malum:alchemical_impetus"
                    : "malum:" + (recipePath.group(2) == null ? recipePath.group(3) + "_node"
                    : recipePath.group(2) + "_impetus");
            var predicate = new JsonObject();
            var itemIds = new JsonArray();
            itemIds.add(inputItem);
            predicate.add("items", itemIds);
            var predicates = new JsonArray();
            predicates.add(predicate);
            var inventoryConditions = new JsonObject();
            inventoryConditions.add("items", predicates);
            var criterion = new JsonObject();
            criterion.addProperty("trigger", "minecraft:inventory_changed");
            criterion.add("conditions", inventoryConditions);
            var criteria = new JsonObject();
            criteria.add("has_input", criterion);
            var requirement = new JsonArray();
            requirement.add("has_input");
            var requirements = new JsonArray();
            requirements.add(requirement);
            var recipes = new JsonArray();
            recipes.add("malum:" + id.getPath().substring("recipes/".length()));
            var rewards = new JsonObject();
            rewards.add("recipes", recipes);
            fixed.add("criteria", criteria);
            fixed.add("requirements", requirements);
            fixed.add("rewards", rewards);
            repaired.getAsJsonArray("advancements").get(index).getAsJsonObject().add("advancement", fixed);
        }
        return repaired == null ? resource : repaired;
    }

    private static boolean isEmptyGeneratedAdvancement(JsonElement advancement) {
        if (!advancement.isJsonObject()) {
            return false;
        }
        var object = advancement.getAsJsonObject();
        var criteria = object.get("criteria");
        var requirements = object.get("requirements");
        return EMPTY_ADVANCEMENT_FIELDS.containsAll(object.keySet())
                && criteria != null && criteria.isJsonObject() && criteria.getAsJsonObject().size() == 0
                && requirements != null && requirements.isJsonArray() && requirements.getAsJsonArray().isEmpty();
    }
}
