package com.moakiee.ae2lt.packaged.logic.multiblock.botania;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.BoundedConstraintMatcher;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Single-trade virtual adapter requiring an open Alfheim Portal. Binding
 * validates pattern outputs; dispatch validates real inputs against the recipe
 * and returns recipe outputs. Portal mana and lifetime remain machine-owned.
 */
public final class AlfheimPortalAdapter implements VirtualCraftingAdapter {

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
        return BotaniaReflection.isAlfheimPortal(be);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.BOTANIA_ALFHEIM_PORTAL;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (!recognizesMain(level, mainPos, be)) {
            return null;
        }
        var holder = BotaniaRecipeLookup.findElvenTradeByOutputs(level, pattern);
        if (holder == null) {
            return null;
        }
        return new BindingResult(new PortalBindHandle(holder), BindingMode.VIRTUAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        var be = level.getBlockEntity(mainPos);
        return BotaniaReflection.isAlfheimPortal(be)
                && BotaniaReflection.portalIsOpen(be, level.getBlockState(mainPos));
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        return null;
    }

    @Override
    @Nullable
    public VirtualCraftingResult planVirtualWithBinding(ServerLevel level, BlockPos mainPos,
                                                        IPatternDetails pattern, KeyCounter[] inputs,
                                                        Object handle, IActionSource source) {
        if (!(handle instanceof PortalBindHandle bind)) {
            return null;
        }
        var be = level.getBlockEntity(mainPos);
        if (!recognizesMain(level, mainPos, be)
                || !BotaniaReflection.portalIsOpen(be, level.getBlockState(mainPos))) {
            return null;
        }

        var recipe = bind.holder();
        var ingredients = recipe.getIngredients();
        if (!consumeIngredientsStrict(ingredients, inputs)) {
            return null;
        }

        var outputs = BotaniaRecipeLookup.elvenTradeOutputs(recipe);
        if (outputs.isEmpty()) {
            return null;
        }
        if (!BotaniaRecipeLookup.validatePatternMultiOutput(pattern, outputs)) {
            return null;
        }

        var stacks = new ArrayList<GenericStack>(outputs.size());
        for (var out : outputs) {
            if (out == null || out.isEmpty()) {
                continue;
            }
            stacks.add(new GenericStack(AEItemKey.of(out), out.getCount()));
        }
        if (stacks.isEmpty()) {
            return null;
        }
        return new VirtualCraftingResult(stacks);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter, IActionSource source) {
        return List.of();
    }

    private static boolean consumeIngredientsStrict(List<Ingredient> ingredients, KeyCounter[] inputs) {
        var available = new LinkedHashMap<AEItemKey, Long>();
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return false;
                }
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                try {
                    available.put(itemKey, Math.addExact(
                            available.getOrDefault(itemKey, 0L), amount));
                } catch (ArithmeticException overflow) {
                    return false;
                }
            }
        }
        var required = ingredients.stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        if (available.isEmpty() || required.isEmpty()) {
            return false;
        }

        var tokens = new ArrayList<AEItemKey>();
        for (var entry : available.entrySet()) {
            int count = (int) Math.min((long) required.size(), entry.getValue());
            for (int i = 0; i < count; i++) {
                tokens.add(entry.getKey());
            }
        }
        if (tokens.size() < required.size()) {
            return false;
        }
        var constraints = required.stream()
                .<java.util.function.Predicate<AEItemKey>>map(
                        ingredient -> key -> ingredient.test(key.toStack(1)))
                .toList();
        // matchWithin: tokens model available capacity; inputs the pattern
        // carries beyond the recipe must not invalidate a valid assignment.
        return BoundedConstraintMatcher.matchWithin(tokens, constraints) != null;
    }

    private record PortalBindHandle(Recipe<?> holder) {
    }
}
