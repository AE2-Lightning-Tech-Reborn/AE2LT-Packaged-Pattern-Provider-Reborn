package com.moakiee.ae2lt.packaged.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Datapack-controlled fluid amount; FA's native black-hole product stays fixed. */
public record FABlackHoleRecipe(ResourceLocation id, ResourceLocation fluidTag, int fluidAmount) implements Recipe<Container> {
    public FABlackHoleRecipe { if (fluidAmount <= 0) throw new IllegalArgumentException("Positive fluid amount required"); }
    @Override public ResourceLocation getId() { return id; }
    @Override public boolean matches(Container input, Level level) { return false; }
    @Override public ItemStack assemble(Container input, RegistryAccess registries) { return getResultItem(registries); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("forbidden_arcanus:xpetrified_orb"))); }
    @Override public RecipeSerializer<?> getSerializer() { return FARecipes.BLACK_HOLE_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return FARecipes.BLACK_HOLE.get(); }
    @Override public boolean isSpecial() { return true; }
    public static final class Serializer implements RecipeSerializer<FABlackHoleRecipe> {
        @Override public FABlackHoleRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new FABlackHoleRecipe(id, new ResourceLocation(GsonHelper.getAsString(json,"fluid_tag")), GsonHelper.getAsInt(json,"fluid_amount"));
        }
        @Override public FABlackHoleRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf in) { return new FABlackHoleRecipe(id,in.readResourceLocation(),in.readVarInt()); }
        @Override public void toNetwork(FriendlyByteBuf out, FABlackHoleRecipe recipe) { out.writeResourceLocation(recipe.fluidTag); out.writeVarInt(recipe.fluidAmount); }
    }
}
