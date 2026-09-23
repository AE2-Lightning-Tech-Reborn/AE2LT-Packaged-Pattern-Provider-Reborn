package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.LinearVirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Virtual adapter for Re-Avaritia's Extreme Smithing Table.
 */
public final class AvaritiaExtremeSmithingAdapter implements LinearVirtualCraftingAdapter {

    private static final String MOD_ID = "avaritia";
    private static final Identifier EXTREME_SMITHING_BLOCK = avaritiaId("extreme_smithing_table");
    private static final Identifier RECIPE_TYPE_ID = avaritiaId("extreme_smithing_recipe");

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, net.minecraft.core.BlockPos pos,
                                  @Nullable BlockEntity be) {
        return isAvaritiaLoaded()
                && BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
                .equals(EXTREME_SMITHING_BLOCK);
    }

    @Override
    public Identifier requiredAdapterId(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return AdapterIds.AVARITIA_EXTREME_SMITHING;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, net.minecraft.core.BlockPos mainPos, IPatternDetails pattern) {
        if (!recognizesMain(level, mainPos, level.getBlockEntity(mainPos)) || !hasSingleItemOutput(pattern)) {
            return null;
        }

        var handle = searchHandle(level, pattern);
        if (handle == null) {
            return null;
        }
        return new BindingResult(handle, BindingMode.VIRTUAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, net.minecraft.core.BlockPos mainPos, Object handle) {
        return true;
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, net.minecraft.core.BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        return null;
    }

    @Override
    @Nullable
    public VirtualCraftingResult planVirtualWithBinding(ServerLevel level, net.minecraft.core.BlockPos mainPos,
                                                        IPatternDetails pattern, KeyCounter[] inputs,
                                                        Object handle, IActionSource source) {
        if (!(handle instanceof SmithingBindHandle bind)) {
            return null;
        }
        if (!recognizesMain(level, mainPos, level.getBlockEntity(mainPos))) {
            return null;
        }

        var available = new LinkedHashMap<AEItemKey, Long>();
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                available.merge(itemKey, amount, Long::sum);
            }
        }
        if (available.isEmpty()) {
            return null;
        }

        var match = assignStrict(bind.recipe(), available, bind.craftRatio());
        if (match == null) {
            return null;
        }

        var input = AvaritiaExtremeSmithingAccess.createInput(match.template(), match.base(), match.additions());
        if (input == null || !recipeMatches(bind.recipe(), input, level)) {
            return null;
        }

        var result = assemble(bind.recipe(), input, level);
        if (result == null || result.isEmpty()) {
            return null;
        }

        long totalCount = (long) bind.craftRatio() * result.getCount();
        if (totalCount <= 0 || totalCount > Integer.MAX_VALUE) {
            return null;
        }
        if (!outputMatchesBatch(pattern, result, totalCount)) {
            return null;
        }

        return new VirtualCraftingResult(List.of(
                new GenericStack(AEItemKey.of(result), totalCount)));
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, net.minecraft.core.BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        return List.of();
    }

    @Nullable
    private static SmithingBindHandle searchHandle(ServerLevel level, IPatternDetails pattern) {
        var patternOut = pattern.getOutputs().getFirst();
        if (!(patternOut.what() instanceof AEItemKey patternOutKey) || patternOut.amount() <= 0) {
            return null;
        }

        var provided = new LinkedHashMap<AEItemKey, Long>();
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length == 0 || !(possible[0].what() instanceof AEItemKey itemKey)) {
                return null;
            }
            long stackAmount = Math.max(1L, possible[0].amount());
            long multiplier = Math.max(1L, input.getMultiplier());
            long amount;
            try {
                amount = Math.multiplyExact(stackAmount, multiplier);
            } catch (ArithmeticException ignored) {
                return null;
            }
            provided.merge(itemKey, amount, Long::sum);
        }
        if (provided.isEmpty()) {
            return null;
        }

        boolean overload = OverloadPatternSemantics.isIdOnlyOutput(pattern, 0);

        for (var holder : recipes(level)) {
            var recipe = holder.value();
            var resultPreview = resultItem(recipe, level);
            if (resultPreview == null || resultPreview.isEmpty()) {
                continue;
            }
            if (resultPreview.getItem() != patternOutKey.getItem()) {
                continue;
            }

            int resultCount = resultPreview.getCount();
            if (resultCount <= 0 || patternOut.amount() % resultCount != 0) {
                continue;
            }
            long k = patternOut.amount() / resultCount;
            if (k <= 0 || k > Integer.MAX_VALUE) {
                continue;
            }

            var match = overload
                    ? assignByItemId(recipe, provided, k)
                    : assignStrict(recipe, provided, k);
            if (match == null) {
                continue;
            }

            var input = AvaritiaExtremeSmithingAccess.createInput(match.template(), match.base(), match.additions());
            if (input == null || !recipeMatches(recipe, input, level)) {
                continue;
            }

            var result = assemble(recipe, input, level);
            if (result == null || result.isEmpty()) {
                continue;
            }

            long totalCount = k * result.getCount();
            if (totalCount <= 0 || totalCount > Integer.MAX_VALUE) {
                continue;
            }
            if (!outputMatchesBatch(pattern, result, totalCount)) {
                continue;
            }

            return new SmithingBindHandle(recipe, (int) k);
        }
        return null;
    }

    @Nullable
    private static SmithingMatch assignStrict(Object recipe,
                                              LinkedHashMap<AEItemKey, Long> available,
                                              long craftRatio) {
        return assign(recipe, available, craftRatio,
                key -> AvaritiaExtremeSmithingAccess.matchesTemplate(recipe, key.toStack(1)),
                key -> AvaritiaExtremeSmithingAccess.matchesBase(recipe, key.toStack(1)),
                key -> AvaritiaExtremeSmithingAccess.matchesAddition(recipe, key.toStack(1)));
    }

    @Nullable
    private static SmithingMatch assignByItemId(Object recipe,
                                                LinkedHashMap<AEItemKey, Long> available,
                                                long craftRatio) {
        return assign(recipe, available, craftRatio,
                key -> AvaritiaExtremeSmithingAccess.templateAcceptsItemId(recipe, key.getItem()),
                key -> AvaritiaExtremeSmithingAccess.baseAcceptsItemId(recipe, key.getItem()),
                key -> AvaritiaExtremeSmithingAccess.additionAcceptsItemId(recipe, key.getItem()));
    }

    @Nullable
    private static SmithingMatch assign(Object recipe,
                                        LinkedHashMap<AEItemKey, Long> available,
                                        long craftRatio,
                                        java.util.function.Predicate<AEItemKey> templateMatcher,
                                        java.util.function.Predicate<AEItemKey> baseMatcher,
                                        java.util.function.Predicate<AEItemKey> additionMatcher) {
        var units = expandUnits(available, craftRatio);
        if (units == null) {
            return null;
        }

        var match = AvaritiaExtremeSmithingInputMatcher.match(
                units, templateMatcher, baseMatcher, additionMatcher);
        if (match == null) {
            return null;
        }

        var additions = new ArrayList<ItemStack>(3);
        for (var addition : match.additions()) {
            additions.add(addition.toStack(1));
        }
        return new SmithingMatch(
                match.template().toStack(1),
                match.base().toStack(1),
                List.copyOf(additions));
    }

    @Nullable
    private static List<AEItemKey> expandUnits(LinkedHashMap<AEItemKey, Long> available,
                                               long craftRatio) {
        if (craftRatio <= 0) {
            return null;
        }
        var units = new ArrayList<AEItemKey>(5);
        for (var entry : available.entrySet()) {
            long amount = entry.getValue();
            if (amount <= 0) {
                continue;
            }
            if (amount % craftRatio != 0) {
                return null;
            }
            long copies = amount / craftRatio;
            if (copies > 5 - units.size()) {
                return null;
            }
            for (long i = 0; i < copies; i++) {
                units.add(entry.getKey());
            }
        }
        return units.size() == 5 ? List.copyOf(units) : null;
    }

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    private static boolean outputMatchesBatch(IPatternDetails pattern, ItemStack result,
                                              long expectedTotalCount) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) {
            return false;
        }
        var expected = outputs.getFirst();
        if (!(expected.what() instanceof AEItemKey expectedKey)
                || expected.amount() != expectedTotalCount) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return AvaritiaExtremeSmithingOutputMatcher.matches(
                expected.amount(), expectedKey,
                expectedTotalCount, actual,
                AEItemKey::dropSecondary);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean recipeMatches(Object recipe, RecipeInput input, ServerLevel level) {
        try {
            return recipe instanceof Recipe<?> r && ((Recipe) r).matches(input, level);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ItemStack assemble(Object recipe, RecipeInput input, ServerLevel level) {
        try {
            return recipe instanceof Recipe<?> r
                    ? ((Recipe) r).assemble(input)
                    : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static ItemStack resultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        try {
            return com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26.resultItem(r, level);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(RECIPE_TYPE_ID)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26.byType(level, type))
                .orElse(List.of());
    }

    private static boolean isAvaritiaLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static Identifier avaritiaId(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private record SmithingBindHandle(Object recipe, int craftRatio) {
    }

    private record SmithingMatch(ItemStack template, ItemStack base, List<ItemStack> additions) {
        SmithingMatch {
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(base, "base");
            additions = List.copyOf(additions);
            if (additions.size() != 3) {
                throw new IllegalArgumentException("Extreme smithing requires exactly three additions");
            }
        }
    }

}
