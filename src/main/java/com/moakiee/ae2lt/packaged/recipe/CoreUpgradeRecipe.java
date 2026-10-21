package com.moakiee.ae2lt.packaged.recipe;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.IngredientAssignment;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** The installed core is checked at completion; it never enters CPU inputs. */
public record CoreUpgradeRecipe(ResourceLocation id, List<Ingredient> materials,
                                ItemStack requiredCore, ItemStack result) implements Recipe<Container> {
    public CoreUpgradeRecipe {
        materials = List.copyOf(materials);
        if (materials.isEmpty() || materials.size() > 81
                || requiredCore.getCount() != 1 || result.getCount() != 1
                || !(requiredCore.getItem() instanceof MultiblockAdapterItem)
                || !(result.getItem() instanceof MultiblockAdapterItem)) {
            throw new IllegalArgumentException("Core upgrades need 1..81 materials and two single cores");
        }
        requiredCore = requiredCore.copy();
        result = result.copy();
    }

    /** -1: rejected; 0: consume installed core; 1: old core supplied by the CPU. */
    public int completionMode(ItemStack installed, IPatternDetails pattern, KeyCounter[] inputs) {
        var previous = (MultiblockAdapterItem) requiredCore.getItem();
        var next = (MultiblockAdapterItem) result.getItem();
        if (previous == next || !next.coveredAdapterIds().containsAll(previous.coveredAdapterIds())
                || pattern.getOutputs().length != 1 || pattern.getPrimaryOutput().amount() != 1
                || !AEItemKey.of(result).equals(pattern.getPrimaryOutput().what()) || inputs == null) return -1;
        var supplied = new ArrayList<IngredientAssignment.Input<AEItemKey>>();
        for (var counter : inputs) {
            if (counter == null) return -1;
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0) return -1;
                supplied.add(new IngredientAssignment.Input<>(key, entry.getLongValue()));
            }
        }
        boolean suppliedCore = supplied.stream().anyMatch(input -> input.key().equals(AEItemKey.of(requiredCore)));
        var needed = new ArrayList<>(materials);
        if (suppliedCore) needed.add(Ingredient.of(requiredCore));
        else if (installed.getCount() != 1 || !ItemStack.isSameItemSameTags(installed, requiredCore)) return -1;
        return IngredientAssignment.assign(needed, supplied, 1,
                (ingredient, key) -> ingredient.test(key.toStack())) != null ? (suppliedCore ? 1 : 0) : -1;
    }

    @Override public boolean matches(Container input, net.minecraft.world.level.Level level) { return false; }
    @Override public ItemStack assemble(Container input, RegistryAccess registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return result.copy(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return CoreUpgradeRecipes.SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return CoreUpgradeRecipes.TYPE.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<CoreUpgradeRecipe> {
        @Override public CoreUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            var values = GsonHelper.getAsJsonArray(json, "ingredients");
            var ingredients = new ArrayList<Ingredient>();
            for (var value : values) ingredients.add(Ingredient.fromJson(value));
            try {
                return new CoreUpgradeRecipe(id, ingredients,
                        ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "required_core")),
                        ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result")));
            } catch (IllegalArgumentException invalid) {
                throw new JsonSyntaxException("Invalid core upgrade " + id, invalid);
            }
        }

        @Override public CoreUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 1 || size > 81) throw new IllegalArgumentException("Invalid core material count " + size);
            var ingredients = new ArrayList<Ingredient>(size);
            for (int i = 0; i < size; i++) ingredients.add(Ingredient.fromNetwork(buffer));
            return new CoreUpgradeRecipe(id, ingredients, buffer.readItem(), buffer.readItem());
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, CoreUpgradeRecipe recipe) {
            buffer.writeVarInt(recipe.materials.size());
            for (var ingredient : recipe.materials) ingredient.toNetwork(buffer);
            buffer.writeItem(recipe.requiredCore);
            buffer.writeItem(recipe.result);
        }
    }
}
