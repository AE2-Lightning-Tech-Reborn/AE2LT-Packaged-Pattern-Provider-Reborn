package com.moakiee.ae2lt.packaged.logic.multiblock.de;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import appeng.api.config.Actionable;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchCommitException;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

import com.moakiee.ae2lt.packaged.logic.multiblock.de.DraconicFusionCraftingAdapter.InjectorInfo;
import com.moakiee.ae2lt.packaged.logic.multiblock.de.DraconicFusionCraftingAdapter.IngredientInfo;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
final class DraconicFusionCraftingAccess {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DraconicFusionCraftingAccess.class);
    private static final String CORE_CLASS =
            "com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingCore";
    private static final String INJECTOR_CLASS =
            "com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingInjector";
    private static final String FUSION_RECIPE_CLASS =
            "com.brandon3055.draconicevolution.api.crafting.IFusionRecipe";
    private static final String FUSION_INGREDIENT_CLASS =
            "com.brandon3055.draconicevolution.api.crafting.IFusionRecipe$IFusionIngredient";
    private static final String STACK_INGREDIENT_CLASS =
            "com.brandon3055.draconicevolution.api.crafting.IngredientStack";
    private static final String DRACONIC_API_CLASS =
            "com.brandon3055.draconicevolution.api.DraconicAPI";
    private static final String TECH_LEVEL_CLASS =
            "com.brandon3055.brandonscore.api.TechLevel";

    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("draconic-fusion");
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
    private static volatile @Nullable Object fusionRecipeType;

    static boolean isCrafting(BlockEntity be) {
        ensureLookup();
        if (isCraftingMethod == null) return true;
        try {
            return Boolean.TRUE.equals(isCraftingMethod.invoke(be));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return true;
        }
    }

    static boolean canSetCatalystStack(BlockEntity be) {
        ensureLookup();
        return supportsMutation(setCatalystStackMethod, be);
    }

    static boolean canSetInjectorStack(BlockEntity core, BlockEntity injector) {
        ensureLookup();
        return supportsMutation(setInjectorStackMethod, injector);
    }

    static boolean canStartCraft(BlockEntity be) {
        ensureLookup();
        return supportsMutation(startCraftMethod, be);
    }

    private static boolean supportsMutation(@Nullable Method method, Object target) {
        return method != null && method.getDeclaringClass().isInstance(target);
    }

    static void startCraft(BlockEntity be) {
        ensureLookup();
        ReflectionSupport.invokeMutation(startCraftMethod, be);
    }

    static void startCraftIfIdle(BlockEntity be) {
        if (isCrafting(be)) {
            throw new DispatchCommitException("Draconic fusion core was already crafting at commit time");
        }
        startCraft(be);
        if (!isCrafting(be)) {
            throw new DispatchCommitException("Draconic fusion core did not enter crafting after startCraft");
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
            return null;
        }
    }

    static void setCatalystStack(BlockEntity be, ItemStack stack) {
        ensureLookup();
        ReflectionSupport.invokeMutation(setCatalystStackMethod, be, stack);
    }

    static boolean clearCatalystIfExact(BlockEntity be, ItemStack expected,
                                         Actionable mode) {
        ensureLookup();
        if (getCatalystStackMethod == null || setCatalystStackMethod == null) return false;
        var current = getCatalystStack(be);
        if (current == null || !ItemStack.isSameItemSameTags(current, expected)
                || current.getCount() != expected.getCount()) return false;
        if (mode == Actionable.SIMULATE) return true;
        setCatalystStack(be, ItemStack.EMPTY);
        var remaining = getCatalystStack(be);
        return remaining != null && remaining.isEmpty();
    }

    @Nullable
    static ItemStack getOutputStack(BlockEntity be) {
        ensureLookup();
        if (getOutputStackMethod == null) return null;
        try {
            var result = getOutputStackMethod.invoke(be);
            return result instanceof ItemStack s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    static ItemStack extractOutput(BlockEntity be) {
        var current = getOutputStack(be);
        return current == null ? ItemStack.EMPTY : extractOutput(be, current.getCount());
    }

    static ItemStack extractOutput(BlockEntity be, int amount) {
        ensureLookup();
        if (amount <= 0 || getOutputStackMethod == null || setOutputStackMethod == null) {
            return ItemStack.EMPTY;
        }
        try {
            var result = getOutputStackMethod.invoke(be);
            if (!(result instanceof ItemStack stack) || stack.isEmpty()
                    || stack.getCount() < amount) {
                return ItemStack.EMPTY;
            }
            var original = stack.copy();
            var extracted = stack.copyWithCount(amount);
            var remaining = stack.copyWithCount(stack.getCount() - amount);
            setOutputStackMethod.invoke(be, remaining);
            var after = getOutputStackMethod.invoke(be);
            if (!(after instanceof ItemStack afterStack)
                    || afterStack.getCount() != remaining.getCount()
                    || !ItemStack.isSameItemSameTags(afterStack, remaining)) {
                try {
                    setOutputStackMethod.invoke(be, original);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                }
                return ItemStack.EMPTY;
            }
            return extracted;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
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
            return null;
        }
    }

    static void setInjectorStack(BlockEntity be, ItemStack stack) {
        ensureLookup();
        ReflectionSupport.invokeMutation(setInjectorStackMethod, be, stack);
    }

    static boolean clearInjectorIfExact(BlockEntity be, ItemStack expected,
                                         Actionable mode) {
        ensureLookup();
        if (getInjectorStackMethod == null || setInjectorStackMethod == null) return false;
        var current = getInjectorStack(be);
        if (current == null || !ItemStack.isSameItemSameTags(current, expected)
                || current.getCount() != expected.getCount()) return false;
        if (mode == Actionable.SIMULATE) return true;
        setInjectorStack(be, ItemStack.EMPTY);
        var remaining = getInjectorStack(be);
        return remaining != null && remaining.isEmpty();
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
            return -1;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static List<Recipe<?>> getFusionRecipes(ServerLevel level) {
        ensureLookup();
        if (fusionRecipeType == null) return null;
        try {
            var allRecipes = level.getRecipeManager()
                    .getAllRecipesFor((RecipeType) fusionRecipeType);
            return (List<Recipe<?>>) (List<?>) allRecipes;
        } catch (RuntimeException | LinkageError e) {
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
            return -1;
        }
    }

    @Nullable
    static ItemStack getResultItem(Object recipe, ServerLevel level) {
        // Direct vanilla-interface dispatch instead of reflection:
        // FusionRecipe.getResultItem(RegistryAccess) overrides a
        // net.minecraft method, so a production (reobfuscated) jar renames
        // the implementation to its SRG name and getMethod lookups by the
        // mojmap name fail even though the method exists. Calling through
        // the erased Recipe interface is compile-time-reobfuscated on our
        // side and dispatches to DE's override correctly.
        if (!(recipe instanceof Recipe<?> vanillaRecipe)) {
            return null;
        }
        try {
            var result = vanillaRecipe.getResultItem(level.registryAccess());
            return result != null && !result.isEmpty() ? result.copy() : null;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    static int getCatalystCount(Object recipe) {
        ensureLookup();
        try {
            var catalyst = getCatalystMethod.invoke(recipe);
            if (!(catalyst instanceof Ingredient ing)) return 1;
            // DE 1.20.x represents counted catalysts as IngredientStack (an
            // Ingredient subclass) exposing the count via getCount().
            var stackIngClass = ReflectionSupport.findClassCached(STACK_INGREDIENT_CLASS).orElse(null);
            if (stackIngClass != null && stackIngClass.isInstance(ing)) {
                var count = ReflectionSupport.findMethodCached(stackIngClass, "getCount")
                        .orElse(null);
                if (count != null) {
                    var value = count.invoke(ing);
                    if (value instanceof Number n && n.intValue() > 0) {
                        return n.intValue();
                    }
                }
            }
            return 1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return 1;
        }
    }

    private static void ensureLookup() {
        LOOKUP.run(DraconicFusionCraftingAccess::doLookup);
    }

    @SuppressWarnings("unchecked")
    private static void doLookup() {
        // Core + injector BE members. Each group resolves independently so a
        // single missing name cannot silently disable the whole adapter.
        try {
            var coreClass = Class.forName(CORE_CLASS);
            isCraftingMethod = coreClass.getMethod("isCrafting");
            startCraftMethod = coreClass.getMethod("startCraft");
            getCatalystStackMethod = coreClass.getMethod("getCatalystStack");
            setCatalystStackMethod = coreClass.getMethod("setCatalystStack", ItemStack.class);
            getOutputStackMethod = coreClass.getMethod("getOutputStack");
            setOutputStackMethod = coreClass.getMethod("setOutputStack", ItemStack.class);
            getInjectorsMethod = coreClass.getMethod("getInjectors");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[ae2ltpp] DE fusion: failed to resolve TileFusionCraftingCore members: {}", e.getMessage());
        }

        try {
            var injectorClass = Class.forName(INJECTOR_CLASS);
            getInjectorStackMethod = injectorClass.getMethod("getInjectorStack");
            setInjectorStackMethod = injectorClass.getMethod("setInjectorStack", ItemStack.class);
            getInjectorTierMethod = injectorClass.getMethod("getInjectorTier");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[ae2ltpp] DE fusion: failed to resolve TileFusionCraftingInjector members: {}", e.getMessage());
        }

        try {
            var techLevelClass = Class.forName(TECH_LEVEL_CLASS);
            techLevelIndexField = techLevelClass.getField("index");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[ae2ltpp] DE fusion: failed to resolve TechLevel.index: {}", e.getMessage());
        }

        try {
            var recipeClass = Class.forName(FUSION_RECIPE_CLASS);
            fusionIngredientsMethod = recipeClass.getMethod("fusionIngredients");
            getCatalystMethod = recipeClass.getMethod("getCatalyst");
            getRecipeTierMethod = recipeClass.getMethod("getRecipeTier");
            // getResultItem is NOT resolved reflectively: it overrides a
            // vanilla Recipe method and is renamed to its SRG name in a
            // production jar, so getMethod("getResultItem", ...) fails.
            // DraconicFusionCraftingAccess.getResultItem calls the vanilla interface
            // directly instead, which is reobf-safe.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[ae2ltpp] DE fusion: failed to resolve IFusionRecipe members: {}", e.getMessage());
        }

        try {
            var ingredientClass = Class.forName(FUSION_INGREDIENT_CLASS);
            ingredientGetMethod = ingredientClass.getMethod("get");
            ingredientConsumeMethod = ingredientClass.getMethod("consume");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[ae2ltpp] DE fusion: failed to resolve IFusionIngredient members: {}", e.getMessage());
        }

        try {
            // Resolve fusion recipe type from DraconicAPI's RegistryObject.
            var apiClass = Class.forName(DRACONIC_API_CLASS);
            var typeField = apiClass.getField("FUSION_RECIPE_TYPE");
            var holder = typeField.get(null);
            if (holder != null) {
                var getMethod = holder.getClass().getMethod("get");
                fusionRecipeType = getMethod.invoke(holder);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[ae2ltpp] DE fusion: failed to resolve DraconicAPI.FUSION_RECIPE_TYPE: {}", e.getMessage());
        }
    }
}
