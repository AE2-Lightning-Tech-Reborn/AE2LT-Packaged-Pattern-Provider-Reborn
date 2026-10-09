package com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Nature's Aura is optional. All private access is isolated and fails closed. */
final class NaturesAuraAccess {
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, List<BlockPos>> OFFSETS = new ConcurrentHashMap<>();

    static Object field(Object instance, String name) {
        try {
            String key = instance.getClass().getName() + "#" + name;
            Field field = FIELDS.get(key);
            if (field == null) {
                field = instance.getClass().getDeclaredField(name);
                field.setAccessible(true);
                FIELDS.put(key, field);
            }
            return field.get(instance);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CompatibilityDiagnostics.failure(NaturesAuraAccess.class, name, e);
            return null;
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
            var cached = OFFSETS.get(key);
            if (cached != null) return cached.stream().map(center::offset).toList();
            var result = new ArrayList<BlockPos>();
            Object structure = structure(name);
            BiFunction<BlockPos, Object, Boolean> visit = (pos, matcher) -> {
                result.add(pos.immutable());
                return true;
            };
            structure.getClass().getMethod("forEach", BlockPos.class, char.class, BiFunction.class)
                    .invoke(structure, BlockPos.ZERO, symbol, visit);
            OFFSETS.put(key, List.copyOf(result));
            return result.stream().map(center::offset).toList();
        } catch (ReflectiveOperationException | RuntimeException e) {
            CompatibilityDiagnostics.failure(NaturesAuraAccess.class, "positions", e);
            return List.of();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean altarSelects(Object be, ItemStack input, RecipeHolder<?> expected) {
        try {
            var capability = Class.forName("de.ellpeck.naturesaura.api.NaturesAuraAPI")
                    .getField("AURA_CONTAINER_ITEM_CAPABILITY").get(null);
            // The native altar charges Aura-container items instead of executing recipes on them.
            if (!(capability instanceof net.neoforged.neoforge.capabilities.ItemCapability cap)
                    || input.getCapability(cap, null) != null) return false;
            var method = be.getClass().getDeclaredMethod("getRecipeForInput", ItemStack.class);
            method.setAccessible(true);
            return method.invoke(be, input) instanceof RecipeHolder<?> selected && selected.id().equals(expected.id());
        } catch (ReflectiveOperationException | RuntimeException e) {
            CompatibilityDiagnostics.failure(NaturesAuraAccess.class, "altarRecipe", e);
            return false;
        }
    }

    private NaturesAuraAccess() {}
}
