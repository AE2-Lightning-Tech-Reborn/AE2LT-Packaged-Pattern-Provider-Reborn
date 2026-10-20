package com.moakiee.ae2lt.packaged.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** A datapack-controlled fluid amount; the native black hole's product stays fixed. */
public record FABlackHoleRecipe(ResourceLocation fluidTag, int fluidAmount) implements Recipe<CraftingInput> {
    public static final MapCodec<FABlackHoleRecipe> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
            ResourceLocation.CODEC.fieldOf("fluid_tag").forGetter(FABlackHoleRecipe::fluidTag),
            Codec.intRange(1,Integer.MAX_VALUE).fieldOf("fluid_amount").forGetter(FABlackHoleRecipe::fluidAmount)
    ).apply(instance,FABlackHoleRecipe::new));
    @Override public boolean matches(CraftingInput input,Level level){return false;}
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){return getResultItem(registries);}
    @Override public boolean canCraftInDimensions(int width,int height){return false;}
    @Override public ItemStack getResultItem(HolderLookup.Provider registries){return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("forbidden_arcanus:xpetrified_orb")));}
    @Override public RecipeSerializer<?> getSerializer(){return FARecipes.BLACK_HOLE_SERIALIZER.get();}
    @Override public RecipeType<?> getType(){return FARecipes.BLACK_HOLE.get();}
    @Override public boolean isSpecial(){return true;}
    public static final class Serializer implements RecipeSerializer<FABlackHoleRecipe>{
        @Override public MapCodec<FABlackHoleRecipe> codec(){return CODEC;}
        @Override public StreamCodec<RegistryFriendlyByteBuf,FABlackHoleRecipe> streamCodec(){return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());}
    }
}
