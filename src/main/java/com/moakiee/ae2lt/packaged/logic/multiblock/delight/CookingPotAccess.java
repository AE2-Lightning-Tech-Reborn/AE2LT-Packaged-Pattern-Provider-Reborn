package com.moakiee.ae2lt.packaged.logic.multiblock.delight;

import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/** Optional access to the two verified native pot layouts; no dependency on either mod at class load. */
public final class CookingPotAccess {
    public enum Kind {
        FARMERS("farmersdelight", "cooking_pot", "cooking", 6,
                "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity", AdapterIds.FD_COOKING_POT),
        EXTREME("avaritia_delight", "extreme_cooking_pot", "extreme_cooking", 81,
                "committee.nova.avaritia_delight.common.block.entity.ExtremeCookingPotBlockEntity", AdapterIds.AD_COOKING_POT);

        public final ResourceLocation blockId;
        public final ResourceLocation recipeId;
        public final ResourceLocation coreId;
        public final int ingredients;
        private final String className;

        Kind(String mod, String block, String recipe, int ingredients, String className, ResourceLocation coreId) {
            this.blockId = ResourceLocation.fromNamespaceAndPath(mod, block);
            this.recipeId = ResourceLocation.fromNamespaceAndPath(mod, recipe);
            this.ingredients = ingredients;
            this.className = className;
            this.coreId = coreId;
        }

        public int containerSlot() { return ingredients + 1; }
        public int outputSlot() { return ingredients + 2; }
        public int size() { return ingredients + 3; }
    }

    public record Pot(Kind kind, BlockEntity entity, IItemHandler inventory) {
        public boolean heated() { return Boolean.TRUE.equals(call(entity, "isHeated")); }
        public boolean empty() {
            for (int slot = 0; slot < inventory.getSlots(); slot++)
                if (!inventory.getStackInSlot(slot).isEmpty()) return false;
            return true;
        }
    }

    private CookingPotAccess() {}

    public static Kind kind(ServerLevel level, BlockPos pos, BlockEntity entity) {
        if (entity == null || !level.isLoaded(pos)) return null;
        var block = BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock());
        for (var kind : Kind.values()) {
            if (block.equals(kind.blockId) && ReflectionSupport.findClassCached(kind.className)
                    .map(type -> type.isInstance(entity)).orElse(false)) return kind;
        }
        return null;
    }

    public static Pot pot(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        var entity = level.getBlockEntity(pos);
        var kind = kind(level, pos, entity);
        if (kind == null) return null;
        // The native public getter preserves slot identity and onContentsChanged callbacks.
        // A sided capability is not this layout: Avaritia 1.6.3 even reuses FD's 6-slot wrapper.
        var value = call(entity, "getInventory");
        return value instanceof IItemHandler inventory && inventory.getSlots() == kind.size()
                ? new Pot(kind, entity, inventory) : null;
    }

    public static ItemStack container(Recipe<?> recipe) {
        var value = call(recipe, "getOutputContainer");
        return value instanceof ItemStack stack ? stack.copy() : null;
    }

    @SuppressWarnings("unchecked")
    public static RecipeType<Recipe<RecipeWrapper>> recipeType(Kind kind) {
        return BuiltInRegistries.RECIPE_TYPE.containsKey(kind.recipeId)
                ? (RecipeType<Recipe<RecipeWrapper>>) BuiltInRegistries.RECIPE_TYPE.get(kind.recipeId) : null;
    }

    private static Object call(Object target, String method) {
        return ReflectionSupport.findMethodCached(target.getClass(), method)
                .flatMap(found -> ReflectionSupport.invoke(found, target)).orElse(null);
    }
}
