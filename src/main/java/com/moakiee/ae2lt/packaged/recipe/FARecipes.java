package com.moakiee.ae2lt.packaged.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class FARecipes {
    private static final DeferredRegister<RecipeType<?>> BLACK_HOLES = DeferredRegister.create(Registries.RECIPE_TYPE, "ae2ltpp");
    private static final DeferredRegister<RecipeSerializer<?>> BLACK_HOLE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "ae2ltpp");
    public static final Supplier<RecipeType<FABlackHoleRecipe>> BLACK_HOLE = BLACK_HOLES.register("fa_black_hole", () -> new RecipeType<>() {
        @Override public String toString() { return "ae2ltpp:fa_black_hole"; }
    });
    public static final Supplier<FABlackHoleRecipe.Serializer> BLACK_HOLE_SERIALIZER = BLACK_HOLE_SERIALIZERS.register("fa_black_hole", FABlackHoleRecipe.Serializer::new);
    private FARecipes() {}
    public static void register(IEventBus bus) { BLACK_HOLES.register(bus); BLACK_HOLE_SERIALIZERS.register(bus); }
}
