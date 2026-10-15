package com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;

/** Reflection boundary for optional Nature's Aura internals. */
public final class NaturesAuraAccess {
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, List<BlockPos>> OFFSETS = new ConcurrentHashMap<>();

    public static Object field(Object instance, String name) {
        if (instance == null) return null;
        try {
            String key = instance.getClass().getName() + "#" + name;
            Field cached = FIELDS.get(key);
            if (cached != null) return cached.get(instance);
            Class<?> type = instance.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    FIELDS.put(key, field);
                    return field.get(instance);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CompatibilityDiagnostics.failure(NaturesAuraAccess.class, name, e);
            return null;
        }
    }

    public static Object recipe(Object be) {
        return field(be, "recipe");
    }

    public static void setRitual(Object be, BlockPos ritualPos, Recipe<?> recipe) {
        setField(be, "ritualPos", ritualPos);
        setField(be, "recipe", recipe);
    }

    public static void setTimer(Object be, int timer) {
        setField(be, "timer", timer);
    }

    private static void setField(Object instance, String name, Object value) {
        if (instance == null) return;
        try {
            String key = instance.getClass().getName() + "#" + name;
            Field cached = FIELDS.get(key);
            if (cached == null) {
                Class<?> type = instance.getClass();
                while (type != null) {
                    try {
                        cached = type.getDeclaredField(name);
                        cached.setAccessible(true);
                        FIELDS.put(key, cached);
                        break;
                    } catch (NoSuchFieldException ignored) {
                        type = type.getSuperclass();
                    }
                }
            }
            if (cached == null) throw new NoSuchFieldException(name);
            cached.set(instance, value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CompatibilityDiagnostics.failure(NaturesAuraAccess.class, name, e);
        }
    }

    static ItemStackHandler items(Object be) {
        return field(be, "items") instanceof ItemStackHandler handler ? handler : null;
    }

    static Ingredient ingredient(Object recipe, String name) {
        return field(recipe, name) instanceof Ingredient ingredient ? ingredient : null;
    }

    static int amount(Ingredient ingredient) {
        int count = 0;
        for (ItemStack stack : ingredient.getItems()) count = Math.max(count, stack.getCount());
        return count;
    }

    static Object structure(String name) throws ReflectiveOperationException {
        return Class.forName("de.ellpeck.naturesaura.blocks.multi.Multiblocks").getField(name).get(null);
    }

    static boolean complete(String name, ServerLevel level, BlockPos pos) {
        try {
            Object structure = structure(name);
            return (boolean) structure.getClass().getMethod("isComplete", Level.class, BlockPos.class)
                    .invoke(structure, level, pos);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CompatibilityDiagnostics.failure(NaturesAuraAccess.class, "structure", e);
            return false;
        }
    }

    static List<BlockPos> positions(String name, BlockPos center, char symbol) {
        try {
            String key = name + ":" + symbol;
            List<BlockPos> cached = OFFSETS.get(key);
            if (cached == null) {
                List<BlockPos> result = new ArrayList<>();
                Object structure = structure(name);
                BiFunction<BlockPos, Object, Boolean> visit = (pos, matcher) -> {
                    result.add(pos.immutable());
                    return true;
                };
                structure.getClass().getMethod("forEach", BlockPos.class, char.class, BiFunction.class)
                        .invoke(structure, BlockPos.ZERO, symbol, visit);
                cached = List.copyOf(result);
                OFFSETS.put(key, cached);
            }
            return cached.stream().map(center::offset).toList();
        } catch (ReflectiveOperationException | RuntimeException e) {
            CompatibilityDiagnostics.failure(NaturesAuraAccess.class, "positions", e);
            return List.of();
        }
    }

    static boolean altarSelects(Object be, ItemStack input, Recipe<?> expected) {
        try {
            Object auraCapability = auraContainerCapability();
            if (auraCapability != null && hasCapability(input, auraCapability)) return false;
            Method method = be.getClass().getDeclaredMethod("getRecipeForInput", ItemStack.class);
            method.setAccessible(true);
            Object selected = method.invoke(be, input);
            return selected instanceof Recipe<?> recipe && recipe.getId().equals(expected.getId());
        } catch (ReflectiveOperationException | RuntimeException e) {
            CompatibilityDiagnostics.failure(NaturesAuraAccess.class, "altarRecipe", e);
            return false;
        }
    }

    private static Object auraContainerCapability() {
        try {
            Class<?> api = Class.forName("de.ellpeck.naturesaura.api.NaturesAuraAPI");
            for (String name : new String[] {"CAP_AURA_CONTAINER", "AURA_CONTAINER_ITEM_CAPABILITY"}) {
                try {
                    return api.getField(name).get(null);
                } catch (NoSuchFieldException ignored) {
                    // Older Nature's Aura builds used the longer field name.
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            CompatibilityDiagnostics.failure(NaturesAuraAccess.class, "auraCapability", e);
        }
        return null;
    }

    private static boolean hasCapability(ItemStack stack, Object capability) {
        try {
            for (Method method : ItemStack.class.getMethods()) {
                if (!method.getName().equals("getCapability") || method.getParameterCount() != 2) continue;
                Object optional = method.invoke(stack, capability, null);
                if (optional == null) return false;
                Method present = optional.getClass().getMethod("isPresent");
                return (boolean) present.invoke(optional);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            CompatibilityDiagnostics.failure(NaturesAuraAccess.class, "auraCapability", e);
        }
        return false;
    }

    private NaturesAuraAccess() {
    }
}
