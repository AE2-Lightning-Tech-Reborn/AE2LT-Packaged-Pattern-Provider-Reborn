package com.moakiee.ae2lt.packaged.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class CoreUpgradeRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, "ae2ltpp");
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "ae2ltpp");
    public static final Supplier<RecipeType<CoreUpgradeRecipe>> TYPE = TYPES.register("core_upgrade", () -> new RecipeType<>() {
        @Override public String toString() { return "ae2ltpp:core_upgrade"; }
    });
    public static final Supplier<CoreUpgradeRecipe.Serializer> SERIALIZER = SERIALIZERS.register("core_upgrade", CoreUpgradeRecipe.Serializer::new);
    private CoreUpgradeRecipes() {}
    public static void register(IEventBus bus) { TYPES.register(bus); SERIALIZERS.register(bus); }
}
