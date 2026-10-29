package com.moakiee.ae2lt.packaged.logic.multiblock.delight;

import java.util.ArrayList;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/** Real native cooking: one paid recipe per pot, including one vessel per serving. */
public final class CookingPotAdapter implements MultiblockAdapter {
    private record Bound(CookingPotAccess.Kind pot, RecipeHolder<Recipe<RecipeWrapper>> recipe) {}
    private record Slot(int index, AEItemKey key, int count) {
        GenericStack generic() { return new GenericStack(key, count); }
    }

    @Override public int priority() { return 136; }

    @Override public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return CookingPotAccess.kind(level, pos, be) != null;
    }

    @Override public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        var kind = CookingPotAccess.kind(level, pos, level.getBlockEntity(pos));
        return kind == null ? null : kind.coreId;
    }

    @Override public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        var pot = CookingPotAccess.pot(level, pos);
        var amounts = declared(pattern);
        if (pot == null || amounts == null) return null;
        for (var kind : CookingPotAccess.Kind.values()) {
            if (kind == CookingPotAccess.Kind.EXTREME && pot.kind() != kind) continue;
            var type = CookingPotAccess.recipeType(kind);
            if (type == null) continue;
            for (var holder : level.getRecipeManager().getAllRecipesFor(type)) {
                var bound = new Bound(pot.kind(), holder);
                if (layout(level, bound, pattern, amounts) != null)
                    return new BindingResult(bound, BindingMode.REAL);
            }
        }
        return null;
    }

    @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        var pot = CookingPotAccess.pot(level, pos);
        return handle instanceof Bound bound && liveRecipe(level, bound) && pot != null
                && pot.kind() == bound.pot() && pot.heated() && pot.empty();
    }

    @Override public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        if (!(handle instanceof Bound bound) || !canDispatch(level, pos, handle)) return null;
        var pot = CookingPotAccess.pot(level, pos);
        var layout = layout(level, bound, pattern, actual(inputs));
        if (pot == null || layout == null) return null;
        var targets = new ArrayList<TargetSlot>();
        for (var slot : layout) {
            targets.add(new TargetSlot(level, pos, null, List.of(slot.generic()), InsertionStrategy.CUSTOM,
                    (stack, mode) -> {
                        if (level.getBlockEntity(pos) != pot.entity() || pot.entity().isRemoved()
                                || !pot.heated() || !liveRecipe(level, bound) || !stack.equals(slot.generic())
                                || !pot.inventory().getStackInSlot(slot.index()).isEmpty()) return 0L;
                        return (long) slot.count() - pot.inventory().insertItem(slot.index(), slot.key().toStack(slot.count()),
                                mode == Actionable.SIMULATE).getCount();
                    }));
        }
        return new DispatchPlan(targets, () -> {
            if (level.getBlockEntity(pos) != pot.entity() || !pot.heated() || !liveRecipe(level, bound))
                throw new IllegalStateException("Cooking pot changed during dispatch");
            var expected = inventory(bound.pot(), layout);
            for (int i = 0; i < expected.getSlots(); i++)
                if (!ItemStack.matches(expected.getStackInSlot(i), pot.inventory().getStackInSlot(i)))
                    throw new IllegalStateException("Cooking pot inventory changed during dispatch");
            if (!nativeRecipeMatches(level, bound, expected))
                throw new IllegalStateException("Cooking pot recipe changed during dispatch");
            pot.entity().setChanged();
        }, (accepted, sink) -> {
            // Only remove receipts from the original entity and exact slots. A replacement pot is unrelated.
            if (level.getBlockEntity(pos) != pot.entity() || pot.entity().isRemoved()) return;
            for (var receipt : accepted) {
                int index = targets.indexOf(receipt.target());
                if (index < 0) continue;
                var slot = layout.get(index);
                if (!slot.key().equals(AEItemKey.of(pot.inventory().getStackInSlot(slot.index())))) continue;
                var recovered = pot.inventory().extractItem(slot.index(), (int) receipt.stack().amount(), false);
                if (!recovered.isEmpty()) sink.accept(new GenericStack(AEItemKey.of(recovered), recovered.getCount()));
            }
        });
    }

    @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos,
            AllowedOutputFilter filter, IActionSource source) {
        var pot = CookingPotAccess.pot(level, pos);
        if (pot == null) return List.of();
        // The meal display is NOT an output slot: taking from it would bypass vessel payment.
        var output = pot.inventory().extractItem(pot.kind().outputSlot(), Integer.MAX_VALUE, false);
        return output.isEmpty() ? List.of() : List.of(new GenericStack(AEItemKey.of(output), output.getCount()));
    }

    private static List<Slot> layout(ServerLevel level, Bound bound, IPatternDetails pattern, KeyCounter amounts) {
        if (amounts == null || pattern.getOutputs().size() != 1) return null;
        var recipe = bound.recipe().value();
        var result = recipe.getResultItem(level.registryAccess());
        if (!outputMatches(pattern, result)) return null;
        var container = CookingPotAccess.container(recipe);
        if (container == null) return null;
        var containerKey = container.isEmpty() ? null : AEItemKey.of(container);
        int vessels = containerKey == null ? 0 : result.getCount();
        if (vessels > container.getMaxStackSize()) return null;
        var ingredients = recipe.getIngredients();
        if (ingredients.isEmpty() || ingredients.size() > bound.pot().ingredients) return null;
        var units = new ArrayList<AEItemKey>();
        for (var entry : amounts) {
            long count = entry.getLongValue();
            if (count == 0) continue;
            if (count < 0 || !(entry.getKey() instanceof AEItemKey key)) return null;
            if (key.equals(containerKey)) count -= vessels;
            if (count < 0 || count > ingredients.size() - units.size()) return null;
            for (long i = 0; i < count; i++) units.add(key);
        }
        if (containerKey != null && amounts.get(containerKey) < vessels) return null;
        var assigned = CookingPotInputAssignment.assign(ingredients, units,
                (ingredient, key) -> ingredient.test(key.toStack()));
        if (assigned == null) return null;
        var slots = new ArrayList<Slot>();
        // Extreme pots first try an ordinary recipe against ONLY slots 0..5, then consume all 81 slots.
        // Keep extreme-only inputs at the far end where possible and verify native precedence below.
        boolean extremeRecipe = recipe.getType() == CookingPotAccess.recipeType(CookingPotAccess.Kind.EXTREME);
        int start = extremeRecipe ? bound.pot().ingredients - assigned.size() : 0;
        for (int i = 0; i < assigned.size(); i++) slots.add(new Slot(start + i, assigned.get(i), 1));
        if (vessels > 0) slots.add(new Slot(bound.pot().containerSlot(), containerKey, vessels));
        var inventory = inventory(bound.pot(), slots);
        var input = new RecipeWrapper(inventory);
        if (!nativeRecipeMatches(level, bound, inventory) || !outputMatches(pattern, recipe.assemble(input, level.registryAccess()))) return null;
        return List.copyOf(slots);
    }

    private static ItemStackHandler inventory(CookingPotAccess.Kind kind, List<Slot> slots) {
        var inventory = new ItemStackHandler(kind.size());
        for (var slot : slots) inventory.setStackInSlot(slot.index(), slot.key().toStack(slot.count()));
        return inventory;
    }

    private static boolean nativeRecipeMatches(ServerLevel level, Bound bound, net.neoforged.neoforge.items.IItemHandler inventory) {
        var input = new RecipeWrapper(inventory);
        for (var kind : CookingPotAccess.Kind.values()) {
            if (kind == CookingPotAccess.Kind.EXTREME && bound.pot() != kind) continue;
            var type = CookingPotAccess.recipeType(kind);
            if (type == null) continue;
            var nativeMatch = level.getRecipeManager().getRecipeFor(type, input, level);
            if (nativeMatch.isPresent()) return nativeMatch.get().id().equals(bound.recipe().id());
        }
        return false;
    }

    private static boolean liveRecipe(ServerLevel level, Bound bound) {
        return level.getRecipeManager().byKey(bound.recipe().id()).map(RecipeHolder::value).orElse(null) == bound.recipe().value();
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack output) {
        if (output.isEmpty() || pattern.getOutputs().size() != 1) return false;
        var expected = pattern.getPrimaryOutput();
        var actual = AEItemKey.of(output);
        return expected.amount() == output.getCount() && expected.what() instanceof AEItemKey item
                && (OverloadPatternSemantics.isIdOnlyOutput(pattern, 0) ? item.getItem() == actual.getItem() : item.equals(actual));
    }

    private static KeyCounter declared(IPatternDetails pattern) {
        var amounts = new KeyCounter();
        try {
            for (var input : pattern.getInputs()) {
                if (input.getPossibleInputs().length != 1 || input.getMultiplier() <= 0) return null;
                var stack = input.getPossibleInputs()[0];
                if (stack.amount() <= 0) return null;
                amounts.set(stack.what(), Math.addExact(amounts.get(stack.what()), Math.multiplyExact(stack.amount(), input.getMultiplier())));
            }
        } catch (ArithmeticException e) { return null; }
        return amounts;
    }

    private static KeyCounter actual(KeyCounter[] inputs) {
        var amounts = new KeyCounter();
        try {
            for (var input : inputs) for (var entry : input) {
                if (entry.getLongValue() < 0) return null;
                amounts.set(entry.getKey(), Math.addExact(amounts.get(entry.getKey()), entry.getLongValue()));
            }
        } catch (ArithmeticException e) { return null; }
        return amounts;
    }
}
