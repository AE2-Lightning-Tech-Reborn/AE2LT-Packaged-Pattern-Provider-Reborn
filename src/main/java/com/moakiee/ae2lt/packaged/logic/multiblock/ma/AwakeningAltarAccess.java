package com.moakiee.ae2lt.packaged.logic.multiblock.ma;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchCommitException;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
final class AwakeningAltarAccess {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("ae2ltpp/ma-awakening-reflection");

    private static final String ALTAR_CLASS =
            "com.blakebr0.mysticalagriculture.tileentity.AwakeningAltarTileEntity";
    private static final String PEDESTAL_CLASS =
            "com.blakebr0.mysticalagriculture.tileentity.AwakeningPedestalTileEntity";
    private static final String VESSEL_CLASS =
            "com.blakebr0.mysticalagriculture.tileentity.EssenceVesselTileEntity";
    private static final String AWAKENING_RECIPE_API_CLASS =
            "com.blakebr0.mysticalagriculture.api.crafting.IAwakeningRecipe";
    private static final String ACTIVATABLE_CLASS =
            "com.blakebr0.mysticalagriculture.util.IActivatable";
    private static final String BASE_INVENTORY_CLASS =
            "com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity";

    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("ma-awakening");
    private static volatile @Nullable Class<?> altarClass;
    private static volatile @Nullable Class<?> pedestalClass;
    private static volatile @Nullable Class<?> vesselClass;
    private static volatile @Nullable Class<?> recipeApiClass;
    private static volatile @Nullable Class<?> activatableClass;
    private static volatile @Nullable Method getInventoryMethod;
    private static volatile @Nullable Method activateMethod;
    private static volatile @Nullable Method isActiveMethod;
    private static volatile @Nullable Method getEssencesMethod;
    private static volatile @Nullable Field progressField;

    static boolean isReady() {
        ensureLookup();
        return altarClass != null
                && pedestalClass != null
                && vesselClass != null
                && recipeApiClass != null
                && activatableClass != null
                && getInventoryMethod != null
                && activateMethod != null
                && isActiveMethod != null
                && getEssencesMethod != null
                && progressField != null;
    }

    static boolean isAwakeningAltar(Object o) {
        ensureLookup();
        return altarClass != null && isReady() && altarClass.isInstance(o);
    }

    static boolean isAwakeningPedestal(Object o) {
        ensureLookup();
        return pedestalClass != null && isReady() && pedestalClass.isInstance(o);
    }

    static boolean isEssenceVessel(Object o) {
        ensureLookup();
        return vesselClass != null && isReady() && vesselClass.isInstance(o);
    }

    @Nullable
    static IItemHandler getHandler(BlockEntity be) {
        ensureLookup();
        return com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess.get(be, getInventoryMethod);
    }

    static int getProgress(BlockEntity be) {
        ensureLookup();
        if (progressField == null) {
            return Integer.MAX_VALUE;
        }
        try {
            return progressField.getInt(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Integer.MAX_VALUE;
        }
    }

    static void activateOrThrow(ServerLevel level, BlockPos pos) {
        ensureLookup();
        var be = level.getBlockEntity(pos);
        if (activateMethod == null
                || isActiveMethod == null
                || activatableClass == null
                || be == null
                || !activatableClass.isInstance(be)) {
            throw new DispatchCommitException("MA awakening activation API is unavailable");
        }
        try {
            activateMethod.invoke(be);
            if (!Boolean.TRUE.equals(isActiveMethod.invoke(be))) {
                throw new DispatchCommitException("MA awakening altar remained inactive");
            }
        } catch (DispatchCommitException e) {
            throw e;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            throw new DispatchCommitException("MA awakening activation failed: " + e);
        }
    }

    static boolean isDefinitelyInactive(BlockEntity be) {
        ensureLookup();
        if (isActiveMethod == null
                || activatableClass == null
                || !activatableClass.isInstance(be)) {
            return false;
        }
        try {
            return Boolean.FALSE.equals(isActiveMethod.invoke(be));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static List<ItemStack> getEssences(Object recipe) {
        ensureLookup();
        if (getEssencesMethod == null || recipeApiClass == null
                || !recipeApiClass.isInstance(recipe)) {
            return null;
        }
        try {
            var value = getEssencesMethod.invoke(recipe);
            if (!(value instanceof List<?> list)) {
                return null;
            }
            var result = new ArrayList<ItemStack>(list.size());
            for (var item : list) {
                if (!(item instanceof ItemStack stack)) {
                    return null;
                }
                result.add(stack);
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static List<Ingredient> getIngredients(Object recipe) {
        // Direct vanilla-interface dispatch instead of reflection:
        // getIngredients() overrides a net.minecraft method, so a
        // production (reobfuscated) jar renames the implementing method
        // to its SRG name and lookups by the mojmap name fail even
        // though the method exists. Calling through the erased Recipe
        // interface is compile-time-reobfuscated on our side and
        // therefore always resolves.
        ensureLookup();
        if (!isReady() || !recipeApiClass.isInstance(recipe)
                || !(recipe instanceof net.minecraft.world.item.crafting.Recipe<?> vanillaRecipe)) {
            return null;
        }
        try {
            var value = vanillaRecipe.getIngredients();
            var result = new ArrayList<Ingredient>(value.size());
            for (Ingredient ingredient : value) {
                result.add(ingredient);
            }
            return result;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static void ensureLookup() {
        LOOKUP.run(AwakeningAltarAccess::doLookup);
    }

    private static void doLookup() {
        altarClass = tryClass(ALTAR_CLASS);
        pedestalClass = tryClass(PEDESTAL_CLASS);
        vesselClass = tryClass(VESSEL_CLASS);
        recipeApiClass = tryClass(AWAKENING_RECIPE_API_CLASS);
        activatableClass = tryClass(ACTIVATABLE_CLASS);

        var baseInventoryClass = tryClass(BASE_INVENTORY_CLASS);
        if (baseInventoryClass != null) {
            getInventoryMethod = tryMethod(baseInventoryClass, "getInventory");
        }
        if (activatableClass != null) {
            activateMethod = tryMethod(activatableClass, "activate");
            isActiveMethod = tryMethod(activatableClass, "isActive");
        }
        if (recipeApiClass != null) {
            getEssencesMethod = tryMethod(recipeApiClass, "getEssences");
        }
        // getIngredients is NOT resolved reflectively: it overrides a
        // vanilla Recipe method and is renamed to its SRG name in a
        // production jar. getIngredients(...) calls the vanilla
        // interface directly instead, which is reobf-safe.
        if (altarClass != null) {
            progressField = tryField(altarClass, "progress");
        }

        LOG.info("MA awakening reflection ready: ready={} altar={} pedestal={} vessel={} recipe={} activatable={} inventory={} activate={} isActive={} ingredients=vanilla essences={} progress={}",
                altarClass != null && pedestalClass != null && vesselClass != null
                        && recipeApiClass != null && activatableClass != null
                        && getInventoryMethod != null && activateMethod != null
                        && isActiveMethod != null && getEssencesMethod != null
                        && progressField != null,
                altarClass != null,
                pedestalClass != null,
                vesselClass != null,
                recipeApiClass != null,
                activatableClass != null,
                getInventoryMethod != null,
                activateMethod != null,
                isActiveMethod != null,
                getEssencesMethod != null,
                progressField != null);
    }

    @Nullable
    private static Class<?> tryClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
            LOG.warn("MA awakening class lookup failed: {} ({})", name, e.toString());
            return null;
        }
    }

    @Nullable
    private static Method tryMethod(Class<?> declaring, String name, Class<?>... params) {
        try {
            return declaring.getMethod(name, params);
        } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
            LOG.warn("MA awakening method lookup failed: {}#{} ({})",
                    declaring.getName(), name, e.toString());
            return null;
        }
    }

    @Nullable
    private static Field tryField(Class<?> declaring, String name) {
        try {
            var field = declaring.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException | LinkageError e) {
            LOG.warn("MA awakening field lookup failed: {}#{} ({})",
                    declaring.getName(), name, e.toString());
            return null;
        }
    }
}
