package com.moakiee.ae2lt.packaged.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class FARecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES=DeferredRegister.create(Registries.RECIPE_TYPE,"ae2ltpp");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,"ae2ltpp");
    public static final DeferredHolder<RecipeType<?>,RecipeType<FABlackHoleRecipe>> BLACK_HOLE=TYPES.register("fa_black_hole",()->new RecipeType<>(){});
    public static final DeferredHolder<RecipeSerializer<?>,FABlackHoleRecipe.Serializer> BLACK_HOLE_SERIALIZER=SERIALIZERS.register("fa_black_hole",FABlackHoleRecipe.Serializer::new);
    private FARecipes(){}
    public static void register(IEventBus bus){TYPES.register(bus);SERIALIZERS.register(bus);}
}
