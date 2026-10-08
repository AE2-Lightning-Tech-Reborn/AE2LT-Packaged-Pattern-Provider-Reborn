package com.moakiee.ae2lt.packaged.logic.multiblock.botania;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

/**
 * Lazy Botania reflection for the Forge 1.20.1 adapters.
 * Missing optional members return safe defaults without loading Botania at compile time.
 */
public final class BotaniaReflection {

    public static final String MOD_ID = "botania";
    private static final Logger LOG = LogUtils.getLogger();

    // Botania classes

    private static final String CLS_MANA_POOL_BE =
            "vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity";
    private static final String CLS_PETAL_APOTHECARY_BE =
            "vazkii.botania.common.block.block_entity.PetalApothecaryBlockEntity";
    private static final String CLS_ALFHEIM_PORTAL_BE =
            "vazkii.botania.common.block.block_entity.AlfheimPortalBlockEntity";
    private static final String CLS_RUNIC_ALTAR_BE =
            "vazkii.botania.common.block.block_entity.RunicAltarBlockEntity";
    private static final String CLS_RUNE_ITEM =
            "vazkii.botania.common.item.material.RuneItem";
    private static final String CLS_TERRA_PLATE_BE =
            "vazkii.botania.common.block.block_entity.TerrestrialAgglomerationPlateBlockEntity";

    private static final String CLS_SIMPLE_INVENTORY_BE =
            "vazkii.botania.common.block.block_entity.SimpleInventoryBlockEntity";

    private static final String CLS_MANA_RECEIVER = "vazkii.botania.api.mana.ManaReceiver";

    private static final String CLS_PETAL_APOTHECARY_IFACE = "vazkii.botania.api.block.PetalApothecary";
    private static final String CLS_PETAL_APOTHECARY_STATE = "vazkii.botania.api.block.PetalApothecary$State";
    private static final String CLS_ALFHEIM_PORTAL_STATE = "vazkii.botania.api.state.enums.AlfheimPortalState";

    private static final String CLS_ELVEN_TRADE_RECIPE = "vazkii.botania.api.recipe.ElvenTradeRecipe";

    private static final String CLS_RECIPE_WITH_REAGENT = "vazkii.botania.api.recipe.RecipeWithReagent";
    private static final String CLS_RECIPE_WITH_CATALYSTS = "vazkii.botania.api.recipe.RecipeWithCatalysts";

    private static final String CLS_BOTANIA_RECIPE_TYPES = "vazkii.botania.common.crafting.BotaniaRecipeTypes";

    // Lazy holders

    private static final ReflectionBootstrap ATTEMPT = new ReflectionBootstrap("botania");
    private static volatile boolean available;

    // Class handles
    @Nullable private static volatile Class<?> manaPoolBeClass;
    @Nullable private static volatile Class<?> petalApothecaryBeClass;
    @Nullable private static volatile Class<?> alfheimPortalBeClass;
    @Nullable private static volatile Class<?> runicAltarBeClass;
    @Nullable private static volatile Class<?> terraPlateBeClass;
    @Nullable private static volatile Class<?> simpleInventoryBeClass;
    @Nullable private static volatile Class<?> manaReceiverClass;
    @Nullable private static volatile Class<?> petalApothecaryIfaceClass;
    @Nullable private static volatile Class<?> petalApothecaryStateClass;
    @Nullable private static volatile Class<?> alfheimPortalStateClass;

    @Nullable private static volatile Class<?> elvenTradeRecipeClass;

    @Nullable private static volatile Class<?> recipeWithReagentClass;
    @Nullable private static volatile Class<?> recipeWithCatalystsClass;
    @Nullable private static volatile Class<?> runeItemClass;

    // Method handles
    @Nullable private static volatile Method getCurrentMana;          // ManaReceiver
    @Nullable private static volatile Method receiveMana;             // ManaReceiver
    @Nullable private static volatile Method poolGetMatchingRecipe;   // ManaPoolBlockEntity
    @Nullable private static volatile Method poolCraftingEffect;      // ManaPoolBlockEntity
    @Nullable private static volatile Method apothecaryGetFluid;
    @Nullable private static volatile Method apothecarySetFluid;
    @Nullable private static volatile Method simpleBeGetItemHandler;  // SimpleInventoryBlockEntity
    @Nullable private static volatile Method reagentGetReagent;       // RecipeWithReagent.getReagent()
    @Nullable private static volatile Method catalystsGetCatalysts;   // RecipeWithCatalysts.getCatalysts()

    // Field handles
    @Nullable private static volatile Field portalTicksOpen;          // AlfheimPortalBlockEntity.ticksOpen
    @Nullable private static volatile Field altarCurrentRecipe;       // RunicAltarBlockEntity.currentRecipe
    @Nullable private static volatile Field altarMana;                // RunicAltarBlockEntity.mana
    @Nullable private static volatile Field altarManaToGet;           // RunicAltarBlockEntity.manaToGet
    @Nullable private static volatile Field terraMana;                // TerrestrialAgglomerationPlateBlockEntity.mana
    @Nullable private static volatile Field terraManaToGet;           // TerrestrialAgglomerationPlateBlockEntity.manaToGet


    // RecipeType statics from BotaniaRecipeTypes
    @Nullable private static RecipeType<?> manaInfusionType;
    @Nullable private static RecipeType<?> elvenTradeType;
    @Nullable private static RecipeType<?> petalType;
    @Nullable private static RecipeType<?> runeType;
    @Nullable private static RecipeType<?> terraPlateType;

    // State enum values (cached for fast equals)
    @Nullable private static volatile Object stateEmpty;
    @Nullable private static volatile Object stateWater;
    @Nullable private static volatile Object portalStateOnX;
    @Nullable private static volatile Object portalStateOnZ;

    private BotaniaReflection() {
    }

    /** Whether the required Botania classes resolved during the lazy lookup. */
    public static boolean isAvailable() {
        ATTEMPT.run(BotaniaReflection::runAttempt);
        return available;
    }

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static void runAttempt() {
        if (!isModLoaded()) {
            return;
        }

        // Optional members resolve independently so a missing version-specific
        // interface does not disable all adapters.
        manaPoolBeClass = tryClass(CLS_MANA_POOL_BE);
        petalApothecaryBeClass = tryClass(CLS_PETAL_APOTHECARY_BE);
        alfheimPortalBeClass = tryClass(CLS_ALFHEIM_PORTAL_BE);
        runicAltarBeClass = tryClass(CLS_RUNIC_ALTAR_BE);
        terraPlateBeClass = tryClass(CLS_TERRA_PLATE_BE);
        simpleInventoryBeClass = tryClass(CLS_SIMPLE_INVENTORY_BE);
        manaReceiverClass = tryClass(CLS_MANA_RECEIVER);
        petalApothecaryIfaceClass = tryClass(CLS_PETAL_APOTHECARY_IFACE);
        petalApothecaryStateClass = tryClass(CLS_PETAL_APOTHECARY_STATE);
        alfheimPortalStateClass = tryClass(CLS_ALFHEIM_PORTAL_STATE);
        runeItemClass = tryClass(CLS_RUNE_ITEM);

        boolean requiredClassesPresent = allPresent(
                manaPoolBeClass,
                petalApothecaryBeClass,
                alfheimPortalBeClass,
                runicAltarBeClass,
                terraPlateBeClass,
                simpleInventoryBeClass,
                manaReceiverClass,
                petalApothecaryIfaceClass,
                petalApothecaryStateClass);

        // Optional recipe interfaces.
        elvenTradeRecipeClass = tryClass(CLS_ELVEN_TRADE_RECIPE);
        recipeWithReagentClass = tryClass(CLS_RECIPE_WITH_REAGENT);
        recipeWithCatalystsClass = tryClass(CLS_RECIPE_WITH_CATALYSTS);

        getCurrentMana = tryMethod(manaReceiverClass, "getCurrentMana");
        receiveMana = tryMethod(manaReceiverClass, "receiveMana", int.class);
        poolGetMatchingRecipe = tryMethod(manaPoolBeClass, "getMatchingRecipe",
                ItemStack.class, BlockState.class);
        poolCraftingEffect = tryMethod(manaPoolBeClass, "craftingEffect", boolean.class);

        apothecaryGetFluid = tryMethod(petalApothecaryIfaceClass, "getFluid");
        apothecarySetFluid = tryMethod(petalApothecaryIfaceClass, "setFluid", petalApothecaryStateClass);

        simpleBeGetItemHandler = tryMethod(simpleInventoryBeClass, "getItemHandler");

        reagentGetReagent = tryMethod(recipeWithReagentClass, "getReagent");
        catalystsGetCatalysts = tryMethod(recipeWithCatalystsClass, "getCatalysts");

        portalTicksOpen = tryField(alfheimPortalBeClass, "ticksOpen");
        altarCurrentRecipe = tryField(runicAltarBeClass, "currentRecipe");
        altarMana = tryField(runicAltarBeClass, "mana");
        altarManaToGet = tryField(runicAltarBeClass, "manaToGet");
        terraMana = tryField(terraPlateBeClass, "mana");
        terraManaToGet = tryField(terraPlateBeClass, "manaToGet");

        stateEmpty = enumValue(petalApothecaryStateClass, "EMPTY");
        stateWater = enumValue(petalApothecaryStateClass, "WATER");
        portalStateOnX = enumValue(alfheimPortalStateClass, "ON_X");
        portalStateOnZ = enumValue(alfheimPortalStateClass, "ON_Z");

        var typeHolder = tryClass(CLS_BOTANIA_RECIPE_TYPES);
        manaInfusionType = recipeType(typeHolder, "MANA_INFUSION_TYPE");
        elvenTradeType = recipeType(typeHolder, "ELVEN_TRADE_TYPE");
        petalType = recipeType(typeHolder, "PETAL_APOTHECARY_TYPE", "PETAL_TYPE");
        runeType = recipeType(typeHolder, "RUNIC_ALTAR_TYPE", "RUNE_TYPE");
        terraPlateType = recipeType(typeHolder, "TERRA_PLATE_TYPE");

        available = requiredClassesPresent;
        if (!available) {
            LOG.warn("Botania reflection missed critical handles; all Botania adapters are disabled");
        }
    }

    @Nullable
    private static Class<?> tryClass(String name) {
        try {
            return Class.forName(name);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static Method tryMethod(@Nullable Class<?> owner, String name, Class<?>... params) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, params);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static Field tryField(@Nullable Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            var f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean allPresent(Class<?>... classes) {
        for (var clazz : classes) {
            if (clazz == null) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static RecipeType<?> recipeType(@Nullable Class<?> owner, String... fieldNames) {
        if (owner == null) {
            return null;
        }
        for (var fieldName : fieldNames) {
            var f = tryField(owner, fieldName);
            if (f != null) {
                try {
                    return (RecipeType<?>) f.get(null);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    // try the next alias
                }
            }
        }
        return null;
    }

    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(@Nullable Class<?> enumClass, String name) {
        if (enumClass == null || !enumClass.isEnum()) {
            return null;
        }
        try {
            return Enum.valueOf((Class) enumClass, name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // Class predicates

    public static boolean isManaPool(@Nullable BlockEntity be) {
        return isAvailable() && manaPoolBeClass != null && manaPoolBeClass.isInstance(be);
    }

    public static boolean isPetalApothecary(@Nullable BlockEntity be) {
        return isAvailable() && petalApothecaryBeClass != null && petalApothecaryBeClass.isInstance(be);
    }

    public static boolean isAlfheimPortal(@Nullable BlockEntity be) {
        return isAvailable() && alfheimPortalBeClass != null && alfheimPortalBeClass.isInstance(be);
    }

    public static boolean isRunicAltar(@Nullable BlockEntity be) {
        return isAvailable() && runicAltarBeClass != null && runicAltarBeClass.isInstance(be);
    }

    public static boolean isTerraPlate(@Nullable BlockEntity be) {
        return isAvailable() && terraPlateBeClass != null && terraPlateBeClass.isInstance(be);
    }

    // Mana Pool

    public static int manaPoolCurrentMana(BlockEntity be) {
        try {
            return (int) getCurrentMana.invoke(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    public static void manaPoolReceiveMana(BlockEntity be, int delta) {
        try {
            receiveMana.invoke(be, delta);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    public static void manaPoolCraftingEffect(BlockEntity be) {
        try {
            poolCraftingEffect.invoke(be, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    /** Lets Botania select a recipe from the live input and catalyst block. */
    @Nullable
    public static Recipe<?> manaPoolMatchingRecipe(BlockEntity be, ItemStack stack, BlockState below) {
        try {
            var result = poolGetMatchingRecipe.invoke(be, stack, below);
            return result instanceof Recipe<?> holder ? holder : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    // Petal Apothecary

    @Nullable
    public static Object apothecaryFluid(BlockEntity be) {
        try {
            return apothecaryGetFluid.invoke(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean apothecaryHasWater(BlockEntity be) {
        var fluid = apothecaryFluid(be);
        return fluid != null && fluid == stateWater;
    }

    public static void apothecarySetEmpty(BlockEntity be) {
        try {
            apothecarySetFluid.invoke(be, stateEmpty);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    // Alfheim Portal

    public static int portalTicksOpen(BlockEntity be) {
        try {
            return portalTicksOpen.getInt(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    public static boolean portalIsOpen(BlockEntity be, BlockState state) {
        // Either the BE ticks-open counter says "open" or the controller's
        // block state is in one of the ON_X/ON_Z values.
        if (portalTicksOpen(be) > 0) {
            return true;
        }
        for (var prop : state.getProperties()) {
            if (alfheimPortalStateClass != null && alfheimPortalStateClass.isInstance(state.getValue(prop))) {
                var value = state.getValue(prop);
                return value == portalStateOnX || value == portalStateOnZ;
            }
        }
        return false;
    }

    // Runic Altar

    @Nullable
    public static Recipe<?> altarCurrentRecipe(BlockEntity be) {
        try {
            var v = altarCurrentRecipe.get(be);
            return v instanceof Recipe<?> holder ? holder : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static int altarMana(BlockEntity be) {
        try {
            return altarMana.getInt(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    public static int altarManaToGet(BlockEntity be) {
        try {
            return altarManaToGet.getInt(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    public static void altarResetState(BlockEntity be) {
        // Reset each field independently so one missing member cannot latch the recipe.
        setFieldQuietly(altarMana, be, 0);
        setFieldQuietly(altarManaToGet, be, 0);
        if (altarCurrentRecipe != null) {
            try {
                altarCurrentRecipe.set(be, null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
    }

    private static void setFieldQuietly(@Nullable Field field, BlockEntity be, @Nullable Object value) {
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            if (field.getType() == int.class) {
                field.setInt(be, value instanceof Number number ? number.intValue() : 0);
            } else {
                field.set(be, value);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    // Terra Plate

    public static int terraMana(BlockEntity be) {
        try {
            return terraMana.getInt(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    public static int terraManaToGet(BlockEntity be) {
        try {
            return terraManaToGet.getInt(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    public static boolean terraIsIdle(BlockEntity be) {
        return terraMana(be) == 0 && terraManaToGet(be) == 0;
    }

    // SimpleInventory / Recipe helpers

    @Nullable
    public static Container simpleInventoryContainer(BlockEntity be) {
        try {
            return (Container) simpleBeGetItemHandler.invoke(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    // Recipe types

    @Nullable public static RecipeType<?> manaInfusionType() { return manaInfusionType; }
    @Nullable public static RecipeType<?> elvenTradeType() { return elvenTradeType; }
    @Nullable public static RecipeType<?> petalType() { return petalType; }
    @Nullable public static RecipeType<?> runeType() { return runeType; }
    @Nullable public static RecipeType<?> terraPlateType() { return terraPlateType; }

    public static boolean isElvenTradeRecipe(Object recipe) {
        return elvenTradeRecipeClass != null && elvenTradeRecipeClass.isInstance(recipe);
    }

    public static int invokeRecipeIntMethod(Object recipe, String methodName, int fallback) {
        try {
            var m = ReflectionSupport.findMethodCached(recipe.getClass(), methodName).orElse(null);
            if (m == null) {
                return fallback;
            }
            var v = m.invoke(recipe);
            return v instanceof Integer i ? i : fallback;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return fallback;
        }
    }

    /** Reads the separate reagent ingredient when the recipe exposes it. */
    @Nullable
    public static net.minecraft.world.item.crafting.Ingredient recipeReagent(Object recipe) {
        try {
            if (recipeWithReagentClass == null || !recipeWithReagentClass.isInstance(recipe)) {
                return null;
            }
            var v = reagentGetReagent.invoke(recipe);
            return v instanceof net.minecraft.world.item.crafting.Ingredient ing ? ing : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * Returns catalyst ingredients, or an empty list when the interface is unavailable.
     * Dispatch catalysts alongside ingredients and return them after crafting.
     */
    public static java.util.List<net.minecraft.world.item.crafting.Ingredient>
            recipeCatalysts(Object recipe) {
        try {
            if (recipeWithCatalystsClass == null || !recipeWithCatalystsClass.isInstance(recipe)) {
                return java.util.List.of();
            }
            var v = catalystsGetCatalysts.invoke(recipe);
            if (v instanceof java.util.List<?> list) {
                var out = new java.util.ArrayList<net.minecraft.world.item.crafting.Ingredient>(list.size());
                for (var entry : list) {
                    if (entry instanceof net.minecraft.world.item.crafting.Ingredient ing) {
                        out.add(ing);
                    }
                }
                return out;
            }
            return java.util.List.of();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return java.util.List.of();
        }
    }

    /** Identifies RuneItem stacks that Botania 1.20.1 refunds after altar crafting. */
    public static boolean isRuneItem(net.minecraft.world.item.Item item) {
        var c = runeItemClass;
        return c != null && c.isInstance(item);
    }
}
