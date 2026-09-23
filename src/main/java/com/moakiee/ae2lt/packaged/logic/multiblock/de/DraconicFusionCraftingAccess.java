package com.moakiee.ae2lt.packaged.logic.multiblock.de;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class DraconicFusionCraftingAccess {
    private static final String CORE_CLASS =
            "com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingCore";
    private static final String INJECTOR_CLASS =
            "com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingInjector";
    private static final String FUSION_RECIPE_CLASS =
            "com.brandon3055.draconicevolution.api.crafting.IFusionRecipe";
    private static final String FUSION_INGREDIENT_CLASS =
            "com.brandon3055.draconicevolution.api.crafting.IFusionRecipe$IFusionIngredient";
    private static final String STACK_INGREDIENT_CLASS =
            "com.brandon3055.draconicevolution.api.crafting.StackIngredient";
    private static final String DRACONIC_API_CLASS =
            "com.brandon3055.draconicevolution.api.DraconicAPI";
    private static final String TECH_LEVEL_CLASS =
            "com.brandon3055.brandonscore.api.TechLevel";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Method isCraftingMethod;
    private static volatile @Nullable Method startCraftMethod;
    private static volatile @Nullable Method getInjectorsMethod;
    private static volatile @Nullable Method getInjectorStackMethod;
    private static volatile @Nullable Method setInjectorStackMethod;
    private static volatile @Nullable Method getInjectorTierMethod;
    private static volatile @Nullable Method getCatalystStackMethod;
    private static volatile @Nullable Method setCatalystStackMethod;
    private static volatile @Nullable Method getOutputStackMethod;
    private static volatile @Nullable Method setOutputStackMethod;
    private static volatile @Nullable Method fusionIngredientsMethod;
    private static volatile @Nullable Method getCatalystMethod;
    private static volatile @Nullable Method getRecipeTierMethod;
    private static volatile @Nullable Method ingredientGetMethod;
    private static volatile @Nullable Method ingredientConsumeMethod;
    private static volatile @Nullable Field techLevelIndexField;
    private static volatile @Nullable Method getCustomIngredientMethod;
    private static volatile @Nullable Method stackIngredientCountMethod;
    private static volatile @Nullable Object fusionRecipeType;

    static boolean isCrafting(BlockEntity be) {
        ensureLookup();
        if (isCraftingMethod == null) return true;
        try {
            return Boolean.TRUE.equals(isCraftingMethod.invoke(be));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "isCrafting", e);
            return true;
        }
    }

    static void startCraft(BlockEntity be) {
        ensureLookup();
        if (startCraftMethod == null) return;
        try {
            startCraftMethod.invoke(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "startCraft", ignored);}
    }

    static void startCraftIfIdle(BlockEntity be) {
        if (!isCrafting(be)) {
            startCraft(be);
        }
    }

    @Nullable
    static ItemStack getCatalystStack(BlockEntity be) {
        ensureLookup();
        if (getCatalystStackMethod == null) return null;
        try {
            var result = getCatalystStackMethod.invoke(be);
            return result instanceof ItemStack s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "getCatalystStack", e);
            return null;
        }
    }

    static void setCatalystStack(BlockEntity be, ItemStack stack) {
        ensureLookup();
        if (setCatalystStackMethod == null) return;
        try {
            setCatalystStackMethod.invoke(be, stack);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "setCatalystStack", ignored);}
    }

    @Nullable
    static ItemStack getOutputStack(BlockEntity be) {
        ensureLookup();
        if (getOutputStackMethod == null) return null;
        try {
            var result = getOutputStackMethod.invoke(be);
            return result instanceof ItemStack s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "getOutputStack", e);
            return null;
        }
    }

    static ItemStack extractOutput(BlockEntity be) {
        ensureLookup();
        if (getOutputStackMethod == null || setOutputStackMethod == null) return ItemStack.EMPTY;
        try {
            var result = getOutputStackMethod.invoke(be);
            if (!(result instanceof ItemStack stack) || stack.isEmpty()) return ItemStack.EMPTY;
            var copy = stack.copy();
            setOutputStackMethod.invoke(be, ItemStack.EMPTY);
            return copy;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "extractOutput", e);
            return ItemStack.EMPTY;
        }
    }

    @Nullable
    static ItemStack getInjectorStack(BlockEntity be) {
        ensureLookup();
        if (getInjectorStackMethod == null) return null;
        try {
            var result = getInjectorStackMethod.invoke(be);
            return result instanceof ItemStack s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "getInjectorStack", e);
            return null;
        }
    }

    static void setInjectorStack(BlockEntity be, ItemStack stack) {
        ensureLookup();
        if (setInjectorStackMethod == null) return;
        try {
            setInjectorStackMethod.invoke(be, stack);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "setInjectorStack", ignored);}
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static List<InjectorInfo> getInjectors(BlockEntity core, ServerLevel level) {
        ensureLookup();
        if (getInjectorsMethod == null) return null;
        try {
            var raw = getInjectorsMethod.invoke(core);
            if (!(raw instanceof List<?> list)) return null;
            var result = new ArrayList<InjectorInfo>(list.size());
            for (var obj : list) {
                if (obj instanceof BlockEntity injBe) {
                    var stack = getInjectorStack(injBe);
                    int tier = getInjectorTierIndex(injBe);
                    if (stack != null && tier >= 0) {
                        result.add(new InjectorInfo(injBe.getBlockPos(), tier, stack.isEmpty()));
                    }
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "getInjectors", e);
            return null;
        }
    }

    private static int getInjectorTierIndex(BlockEntity injBe) {
        ensureLookup();
        if (getInjectorTierMethod == null || techLevelIndexField == null) return -1;
        try {
            var tier = getInjectorTierMethod.invoke(injBe);
            if (tier == null) return -1;
            return techLevelIndexField.getInt(tier);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "getInjectorTierIndex", e);
            return -1;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static List<RecipeHolder<?>> getFusionRecipes(ServerLevel level) {
        ensureLookup();
        if (fusionRecipeType == null) return null;
        try {
            var allRecipes = com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26.byType(
                    level, (RecipeType<?>) fusionRecipeType);
            return (List<RecipeHolder<?>>) (List<?>) allRecipes;
        } catch (RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "getFusionRecipes", e);
            return null;
        }
    }

    @Nullable
    static Ingredient getCatalyst(Object recipe) {
        ensureLookup();
        if (getCatalystMethod == null) return null;
        try {
            var result = getCatalystMethod.invoke(recipe);
            return result instanceof Ingredient ing ? ing : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "getCatalyst", e);
            return null;
        }
    }

    @Nullable
    static List<IngredientInfo> getFusionIngredients(Object recipe) {
        ensureLookup();
        if (fusionIngredientsMethod == null || ingredientGetMethod == null
                || ingredientConsumeMethod == null) return null;
        try {
            var raw = fusionIngredientsMethod.invoke(recipe);
            if (!(raw instanceof List<?> list)) return null;
            var result = new ArrayList<IngredientInfo>(list.size());
            for (var obj : list) {
                var ing = ingredientGetMethod.invoke(obj);
                var consume = ingredientConsumeMethod.invoke(obj);
                if (ing instanceof Ingredient ingredient && consume instanceof Boolean b) {
                    result.add(new IngredientInfo(ingredient, b));
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "getFusionIngredients", e);
            return null;
        }
    }

    static int getRecipeTierIndex(Object recipe) {
        ensureLookup();
        if (getRecipeTierMethod == null || techLevelIndexField == null) return -1;
        try {
            var tier = getRecipeTierMethod.invoke(recipe);
            if (tier == null) return -1;
            return techLevelIndexField.getInt(tier);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "getRecipeTierIndex", e);
            return -1;
        }
    }

    @Nullable
    static ItemStack getResultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof net.minecraft.world.item.crafting.Recipe<?> typed)) return null;
        try {
            return com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26.resultItem(typed, level).copy();
        } catch (RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "getResultItem", e);
            return null;
        }
    }

    static int getCatalystCount(Object recipe) {
        ensureLookup();
        if (getCatalystMethod == null) return 1;
        try {
            var catalyst = getCatalystMethod.invoke(recipe);
            if (!(catalyst instanceof Ingredient ing)) return 1;
            if (getCustomIngredientMethod == null) return 1;
            var custom = getCustomIngredientMethod.invoke(ing);
            if (custom == null) return 1;
            var stackIngClass = ReflectionSupport.findClassCached(STACK_INGREDIENT_CLASS).orElse(null);
            if (stackIngClass == null) return 1;
            if (!stackIngClass.isInstance(custom)) return 1;
            if (stackIngredientCountMethod == null) {
                stackIngredientCountMethod = ReflectionSupport.findMethodCached(stackIngClass, "getCount")
                        .orElse(null);
            }
            if (stackIngredientCountMethod == null) {
                return 1;
            }
            var count = stackIngredientCountMethod.invoke(custom);
            return count instanceof Number n ? n.intValue() : 1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "getCatalystCount", e);
            return 1;
        }
    }

    private static void ensureLookup() {
        if (lookupDone) return;
        synchronized (DraconicFusionCraftingAccess.class) {
            if (lookupDone) return;
            try {
                doLookup();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                if (!(ignored instanceof ClassNotFoundException) || net.neoforged.fml.ModList.get().isLoaded("draconicevolution")) {
                    CompatibilityDiagnostics.failure(DraconicFusionCraftingAccess.class, "ensureLookup", ignored);
                }
            } finally {
                lookupDone = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void doLookup() throws ReflectiveOperationException {
        var coreClass = Class.forName(CORE_CLASS);
        isCraftingMethod = coreClass.getMethod("isCrafting");
        startCraftMethod = coreClass.getMethod("startCraft");
        getCatalystStackMethod = coreClass.getMethod("getCatalystStack");
        setCatalystStackMethod = coreClass.getMethod("setCatalystStack", ItemStack.class);
        getOutputStackMethod = coreClass.getMethod("getOutputStack");
        setOutputStackMethod = coreClass.getMethod("setOutputStack", ItemStack.class);
        getInjectorsMethod = coreClass.getMethod("getInjectors");

        var injectorClass = Class.forName(INJECTOR_CLASS);
        getInjectorStackMethod = injectorClass.getMethod("getInjectorStack");
        setInjectorStackMethod = injectorClass.getMethod("setInjectorStack", ItemStack.class);
        getInjectorTierMethod = injectorClass.getMethod("getInjectorTier");

        var techLevelClass = Class.forName(TECH_LEVEL_CLASS);
        techLevelIndexField = techLevelClass.getField("index");

        var recipeClass = Class.forName(FUSION_RECIPE_CLASS);
        fusionIngredientsMethod = recipeClass.getMethod("fusionIngredients");
        getCatalystMethod = recipeClass.getMethod("getCatalyst");
        getRecipeTierMethod = recipeClass.getMethod("getRecipeTier");

        var ingredientClass = Class.forName(FUSION_INGREDIENT_CLASS);
        ingredientGetMethod = ingredientClass.getMethod("get");
        ingredientConsumeMethod = ingredientClass.getMethod("consume");

        getCustomIngredientMethod = Ingredient.class.getMethod("getCustomIngredient");

        // Resolve fusion recipe type from DraconicAPI
        var apiClass = Class.forName(DRACONIC_API_CLASS);
        var typeField = apiClass.getField("FUSION_RECIPE_TYPE");
        var holder = typeField.get(null);
        if (holder != null) {
            var getMethod = holder.getClass().getMethod("get");
            fusionRecipeType = getMethod.invoke(holder);
        }
    }
    record InjectorInfo(BlockPos pos, int tierIndex, boolean empty) {}
    record IngredientInfo(Ingredient ingredient, boolean consume) {}
}
