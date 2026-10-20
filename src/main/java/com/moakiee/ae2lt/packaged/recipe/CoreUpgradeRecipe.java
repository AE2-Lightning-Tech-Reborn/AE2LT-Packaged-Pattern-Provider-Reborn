package com.moakiee.ae2lt.packaged.recipe;

import java.util.List;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.IngredientAssignment;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** The installed core is a completion requirement, never a transported pattern input. */
public record CoreUpgradeRecipe(List<Ingredient> materials, ItemStack requiredCore, ItemStack result)
        implements Recipe<CraftingInput> {
    public static final MapCodec<CoreUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.listOf(1, 81).fieldOf("ingredients").forGetter(CoreUpgradeRecipe::materials),
            ItemStack.SINGLE_ITEM_CODEC.fieldOf("required_core").forGetter(CoreUpgradeRecipe::requiredCore),
            ItemStack.SINGLE_ITEM_CODEC.fieldOf("result").forGetter(CoreUpgradeRecipe::result)
    ).apply(instance, CoreUpgradeRecipe::new));

    public int completionMode(ItemStack installed, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!(requiredCore.getItem() instanceof MultiblockAdapterItem previous)
                || !(result.getItem() instanceof MultiblockAdapterItem next)
                || previous == next || !next.coveredAdapterIds().containsAll(previous.coveredAdapterIds())
                || pattern.getOutputs().size() != 1 || pattern.getPrimaryOutput().amount() != 1
                || !AEItemKey.of(result).equals(pattern.getPrimaryOutput().what())) return -1;
        var supplied = new java.util.ArrayList<IngredientAssignment.Input<AEItemKey>>();
        for (var counter : inputs) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0) return -1;
            supplied.add(new IngredientAssignment.Input<>(key, entry.getLongValue()));
        }
        boolean suppliedCore = supplied.stream().anyMatch(input -> input.key().equals(AEItemKey.of(requiredCore)));
        var needed = new java.util.ArrayList<>(materials);
        if (suppliedCore) needed.add(Ingredient.of(requiredCore));
        else if (installed.getCount() != 1 || !ItemStack.isSameItemSameComponents(installed, requiredCore)) return -1;
        return IngredientAssignment.assign(needed, supplied, 1, (ingredient, key) -> ingredient.test(key.toStack())) != null
                ? (suppliedCore ? 1 : 0) : -1;
    }
    @Override public boolean matches(CraftingInput input, Level level) { return false; }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result.copy(); }
    @Override public RecipeSerializer<?> getSerializer() { return CoreUpgradeRecipes.SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return CoreUpgradeRecipes.TYPE.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<CoreUpgradeRecipe> {
        @Override public MapCodec<CoreUpgradeRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, CoreUpgradeRecipe> streamCodec() {
            return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
        }
    }
}
