package com.moakiee.ae2lt.packaged.recipe;

import com.mojang.serialization.MapCodec;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** An explicit raven-only bootstrap recipe; never registered as ordinary infusion. */
public final class RavenCoreRecipe implements Recipe<CraftingInput> {
    public static final int EXPERIENCE = 30970;
    public static final int FLUID_AMOUNT = EXPERIENCE * 20;
    public static final RavenCoreRecipe INSTANCE = new RavenCoreRecipe();
    @Override public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, Ingredient.of(PPItems.BASIC_PACKAGED_CORE.get()),
                Ingredient.of(Items.LAPIS_LAZULI), Ingredient.of(Items.LAPIS_LAZULI), Ingredient.of(Items.LAPIS_LAZULI));
    }
    @Override public boolean matches(CraftingInput input, Level level) { return false; }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return getResultItem(registries); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return PPItems.APOTHIC_INFUSION_ADAPTER.toStack(); }
    @Override public RecipeSerializer<?> getSerializer() { return RavenRecipes.SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return RavenRecipes.TYPE.get(); }
    @Override public boolean isSpecial() { return true; }
    public static final class Serializer implements RecipeSerializer<RavenCoreRecipe> {
        @Override public MapCodec<RavenCoreRecipe> codec() { return MapCodec.unit(INSTANCE); }
        @Override public StreamCodec<RegistryFriendlyByteBuf, RavenCoreRecipe> streamCodec() { return StreamCodec.unit(INSTANCE); }
    }
}
