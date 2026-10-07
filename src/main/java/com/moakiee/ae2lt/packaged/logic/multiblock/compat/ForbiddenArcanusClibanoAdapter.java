package com.moakiee.ae2lt.packaged.logic.multiblock.compat;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterBlocks;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterRecipeTypes;
import com.moakiee.ae2lt.packaged.logic.multiblock.BlockCapabilities;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.SlotHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;

public final class ForbiddenArcanusClibanoAdapter implements MultiblockAdapter {
    private static final ResourceLocation BLOCK = new ResourceLocation("forbidden_arcanus", "clibano_main_part");
    private static final ResourceLocation TYPE = new ResourceLocation("forbidden_arcanus", "clibano_combustion");
    private static final String TILE_CLASS = "com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoMainBlockEntity";
    private static final String HARVEST_STATE = "forbidden_arcanus_clibano:harvest";
    private static final int[] INPUT_SLOTS = {3, 4};
    private static final int[] OUTPUT_SLOTS = {5, 6};

    @Override public int priority() { return 100; }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
        return be != null && ModList.get().isLoaded("forbidden_arcanus")
                && BLOCK.equals(AdapterBlocks.idOf(be.getBlockState()))
                && TILE_CLASS.equals(be.getClass().getName());
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.FORBIDDEN_ARCANUS_CLIBANO;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        if (!recognizesMain(level, pos, level.getBlockEntity(pos)) || pattern.getOutputs().length != 1
                || !(pattern.getOutputs()[0].what() instanceof AEItemKey)) return null;
        var recipes = new ArrayList<Object>();
        for (var recipe : recipes(level)) {
            var output = recipeOutput(recipe, level);
            if (output != null && !output.isEmpty() && outputMatches(pattern, output)) recipes.add(recipe);
        }
        return recipes.isEmpty() ? null : new BindingResult(new Handle(List.copyOf(recipes)), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        return canDispatch(level, pos, handle, AdapterPersistentScope.NOOP);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle, AdapterPersistentScope scope) {
        if (!(handle instanceof Handle) || !recognizesMain(level, pos, level.getBlockEntity(pos))
                || !isIdle(level, pos)) return false;
        var input = inputHandler(level, pos);
        var output = outputHandler(level, pos);
        return input != null && output != null && empty(input, INPUT_SLOTS) && empty(output, OUTPUT_SLOTS)
                && !SlotHarvestOwnership.blocksDispatch(level, pos, scope, HARVEST_STATE, true);
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
                                        KeyCounter[] inputs, Object handle, IActionSource source) {
        return planWithBinding(level, pos, pattern, inputs, handle, source, AdapterPersistentScope.NOOP);
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
                                        KeyCounter[] inputs, Object handle, IActionSource source,
                                        AdapterPersistentScope scope) {
        if (!(handle instanceof Handle bound) || !canDispatch(level, pos, handle, scope)) return null;
        var input = singleInput(inputs);
        if (input == null || input.amount != 1) return null;
        for (var recipe : bound.recipes) {
            var ingredient = recipeIngredient(recipe);
            var output = recipeOutput(recipe, level);
            if (!(ingredient instanceof Ingredient inputIngredient) || output == null || !inputIngredient.test(input.stack)
                    || !outputMatches(pattern, output)
                    || !supportsFireType(level.getBlockEntity(pos), recipe)) continue;
            var target = new TargetSlot(level, pos, Direction.UP, List.of(new GenericStack(input.key, 1)),
                    InsertionStrategy.CUSTOM, (stack, mode) -> insertInput(level, pos, stack, input.key, mode));
            return new DispatchPlan(List.of(target), () -> SlotHarvestOwnership.store(
                    level, pos, scope, HARVEST_STATE, output, recipeId(recipe)));
        }
        return null;
    }

    @Override public boolean supportsPatternIndependentHarvest() { return true; }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos,
                                              AllowedOutputFilter filter, IActionSource source) {
        return List.of();
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos,
                                              AllowedOutputFilter filter, IActionSource source,
                                              AdapterPersistentScope scope) {
        if (!recognizesMain(level, pos, level.getBlockEntity(pos))) return List.of();
        if (!isIdle(level, pos)) return List.of();
        var input = inputHandler(level, pos);
        if (input == null || !empty(input, INPUT_SLOTS)) return List.of();
        var ownership = SlotHarvestOwnership.load(level, pos, scope, HARVEST_STATE);
        if (ownership == null) return List.of();
        var output = outputHandler(level, pos);
        if (output == null) return List.of();
        var expected = ownership.expectedOutput();
        for (int slot : OUTPUT_SLOTS) {
            var current = output.getStackInSlot(slot);
            if (ownership.attributableCount(current) != expected.getCount()
                    || !filter.matches(AEItemKey.of(current))) continue;
            var extracted = output.extractItem(slot, expected.getCount(), false);
            if (extracted.isEmpty()) return List.of();
            SlotHarvestOwnership.clear(level, pos, scope, HARVEST_STATE);
            return List.of(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
        }
        return List.of();
    }

    private static long insertInput(ServerLevel level, BlockPos pos, GenericStack stack,
                                    AEItemKey expected, Actionable mode) {
        if (!(stack.what() instanceof AEItemKey key) || !expected.equals(key) || stack.amount() != 1) return 0L;
        var handler = inputHandler(level, pos);
        if (handler == null) return 0L;
        for (int slot : INPUT_SLOTS) {
            if (!handler.getStackInSlot(slot).isEmpty()) continue;
            var remainder = handler.insertItem(slot, expected.toStack(1), mode == Actionable.SIMULATE);
            if (remainder.isEmpty()) return 1L;
        }
        return 0L;
    }

    private static boolean empty(IItemHandler handler, int[] slots) {
        for (int slot : slots) if (slot >= handler.getSlots() || !handler.getStackInSlot(slot).isEmpty()) return false;
        return true;
    }

    private static boolean isIdle(ServerLevel level, BlockPos pos) {
        return isIdle(level.getBlockEntity(pos));
    }

    static boolean isIdle(@Nullable Object entity) {
        var firstProgress = intField(entity, "cookingProgressFirst");
        var secondProgress = intField(entity, "cookingProgressSecond");
        return firstProgress == 0 && secondProgress == 0;
    }

    static boolean supportsFireType(@Nullable Object entity, @Nullable Object recipe) {
        if (entity == null || recipe == null) return false;
        var currentField = ReflectionSupport.findDeclaredFieldCached(entity.getClass(), "fireType").orElse(null);
        var inputMethod = ReflectionSupport.findDeclaredMethodCached(entity.getClass(), "getFireTypeFromInput")
                .orElse(null);
        var requiredMethod = ReflectionSupport.findMethodCached(recipe.getClass(), "getRequiredFireType")
                .orElse(null);
        if (currentField == null || inputMethod == null || requiredMethod == null) return false;
        try {
            var current = currentField.get(entity);
            var queued = ReflectionSupport.invoke(inputMethod, entity).orElse(null);
            var required = ReflectionSupport.invoke(requiredMethod, recipe).orElse(null);
            return current instanceof Enum<?> currentType && queued instanceof Enum<?> queuedType
                    && required instanceof Enum<?> requiredType
                    && currentType.getDeclaringClass() == requiredType.getDeclaringClass()
                    && queuedType.getDeclaringClass() == requiredType.getDeclaringClass()
                    && Math.max(currentType.ordinal(), queuedType.ordinal()) >= requiredType.ordinal();
        } catch (IllegalAccessException | RuntimeException ignored) {
            return false;
        }
    }

    private static int intField(@Nullable Object target, String name) {
        if (target == null) return -1;
        try {
            var field = ReflectionSupport.findDeclaredFieldCached(target.getClass(), name).orElse(null);
            if (field == null) return -1;
            return field.getInt(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    @Nullable private static IItemHandler inputHandler(ServerLevel level, BlockPos pos) {
        return BlockCapabilities.find(level, pos, ForgeCapabilities.ITEM_HANDLER, Direction.UP);
    }

    @Nullable private static IItemHandler outputHandler(ServerLevel level, BlockPos pos) {
        return BlockCapabilities.find(level, pos, ForgeCapabilities.ITEM_HANDLER, Direction.DOWN);
    }

    @SuppressWarnings({"rawtypes", "unchecked"}) private static List<Recipe<?>> recipes(ServerLevel level) {
        var type = AdapterRecipeTypes.find(TYPE);
        return type == null ? List.of() : (List<Recipe<?>>)(List<?>)level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }

    @Nullable private static Object recipeIngredient(Object recipe) {
        return recipe instanceof Recipe<?> vanilla && !vanilla.getIngredients().isEmpty() ? vanilla.getIngredients().get(0) : null;
    }

    @Nullable private static ItemStack recipeOutput(Object recipe, ServerLevel level) {
        if (recipe instanceof Recipe<?> vanilla) try { return vanilla.getResultItem(level.registryAccess()).copy(); }
        catch (RuntimeException ignored) { }
        return null;
    }

    @Nullable private static ResourceLocation recipeId(Object recipe) {
        return recipe instanceof Recipe<?> vanilla ? vanilla.getId() : null;
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack output) {
        var expected = pattern.getOutputs()[0];
        if (!(expected.what() instanceof AEItemKey key) || expected.amount() != output.getCount()) return false;
        var actual = AEItemKey.of(output);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? key.dropSecondary().equals(actual.dropSecondary()) : key.equals(actual);
    }

    @Nullable private static Input singleInput(KeyCounter[] inputs) {
        AEItemKey key = null; long amount = 0;
        for (var counter : inputs) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey itemKey) || (key != null && !key.equals(itemKey))) return null;
            key = itemKey; amount += entry.getLongValue();
        }
        return key == null || amount <= 0 ? null : new Input(key, key.toStack((int)Math.min(amount, Integer.MAX_VALUE)), amount);
    }

    private record Handle(List<Object> recipes) { }
    private record Input(AEItemKey key, ItemStack stack, long amount) { }
}
