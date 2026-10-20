package com.moakiee.ae2lt.packaged.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class RavenRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, "ae2ltpp");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "ae2ltpp");
    public static final DeferredHolder<RecipeType<?>, RecipeType<RavenCoreRecipe>> TYPE = TYPES.register("raven_core", () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeSerializer<?>, RavenCoreRecipe.Serializer> SERIALIZER = SERIALIZERS.register("raven_core", RavenCoreRecipe.Serializer::new);
    private RavenRecipes() {}
    public static void register(IEventBus bus) { TYPES.register(bus); SERIALIZERS.register(bus); }
}
