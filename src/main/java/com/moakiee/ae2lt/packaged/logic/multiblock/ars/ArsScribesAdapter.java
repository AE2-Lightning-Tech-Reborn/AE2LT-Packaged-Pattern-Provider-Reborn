package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.IngredientAssignment;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.resource.ExperienceFluidPayment;
import com.moakiee.ae2lt.packaged.resource.ExperienceOrbPickupStrategy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native glyph production; recipe experience points are paid as 20 mB each. */
public final class ArsScribesAdapter implements MultiblockAdapter {
    private record Match(RecipeHolder<?> recipe) {}
    private record Inputs(List<ItemStack> items, long missingExperience) {}
    @Override public int priority() { return 100; }
    @Override public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) { return AdapterIds.ARS_SCRIBES; }
    @Override public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be instanceof ArsScribesMachine && ArsScribesAccess.head(level, pos) != null;
    }
    @Override public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        try {
            if (ArsScribesAccess.head(level, pos) == null) return null;
            var amounts = declared(pattern);
            for (var recipe : ArsScribesAccess.recipes(level)) if (match(level, recipe, pattern, amounts) != null)
                return new BindingResult(new Match(recipe), BindingMode.REAL);
        } catch (RuntimeException | LinkageError e) { failure("bind", e); }
        return null;
    }
    @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        var head = ArsScribesAccess.head(level, pos);
        return handle instanceof Match && head instanceof ArsScribesMachine machine && machine.ae2ltpp$isScribeIdle();
    }
    @Override public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        if (!(handle instanceof Match bound) || !canDispatch(level, pos, handle)
                || level.getRecipeManager().byKey(bound.recipe().id()).map(RecipeHolder::value).orElse(null) != bound.recipe().value()) return null;
        try {
            var amounts = actual(inputs);
            var matched = match(level, bound.recipe(), pattern, amounts);
            if (matched == null) return null;
            var head = ArsScribesAccess.head(level, pos);
            var machine = (ArsScribesMachine) head;
            var payment = new ExperienceFluidPayment(head, source);
            if (!payment.available(matched.missingExperience())) return null;
            var escrow = new KeyCounter();
            boolean[] started = {false};
            var stacks = new ArrayList<GenericStack>();
            for (var entry : amounts) if (entry.getLongValue() > 0) stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            if (stacks.isEmpty()) return null;
            var target = new TargetSlot(level, head.getBlockPos(), null, List.copyOf(stacks), InsertionStrategy.CUSTOM, (stack, mode) -> {
                if (ArsScribesAccess.head(level, pos) != head || !machine.ae2ltpp$isScribeIdle()) return 0L;
                long accepted = Math.min(stack.amount(), amounts.get(stack.what()) - escrow.get(stack.what()));
                if (accepted > 0 && mode == Actionable.MODULATE) escrow.add(stack.what(), accepted);
                return Math.max(0, accepted);
            });
            return new DispatchPlan(List.of(target), null, (receipts, sink) -> {
                if (started[0]) return;
                for (var receipt : receipts) {
                    var stack = receipt.stack();
                    long recovered = Math.min(escrow.get(stack.what()), stack.amount());
                    if (recovered > 0) {
                        escrow.remove(stack.what(), recovered);
                        sink.accept(new GenericStack(stack.what(), recovered));
                    }
                }
            }, retain -> {
                try {
                    if (ArsScribesAccess.head(level, pos) != head || !machine.ae2ltpp$isScribeIdle()
                            || !payment.charge(matched.missingExperience())) throw new IllegalStateException("Scribe payment changed before commit");
                    if (ArsScribesAccess.head(level, pos) != head || !machine.ae2ltpp$isScribeIdle())
                        throw new IllegalStateException("Scribes table changed during payment");
                    machine.ae2ltpp$beginScribing(bound.recipe(), matched.items());
                    started[0] = true;
                } finally {
                    if (!started[0]) payment.refund(retain);
                }
            });
        } catch (RuntimeException | LinkageError e) { failure("plan", e); return null; }
    }

    private static Inputs match(ServerLevel level, RecipeHolder<?> holder, IPatternDetails pattern, KeyCounter amounts) {
        if (amounts == null || pattern.getOutputs().size() != 1) return null;
        var output = holder.value().getResultItem(level.registryAccess());
        if (output.isEmpty() || !AEItemKey.of(output).equals(pattern.getPrimaryOutput().what())
                || output.getCount() != pattern.getPrimaryOutput().amount()) return null;
        long cost = ArsScribesAccess.cost(holder.value());
        if (cost < 0) return null;
        long suppliedFluid = 0;
        var items = new ArrayList<IngredientAssignment.Input<AEItemKey>>();
        for (var entry : amounts) {
            if (entry.getLongValue() <= 0) return null;
            if (entry.getKey() instanceof AEItemKey key) items.add(new IngredientAssignment.Input<>(key, entry.getLongValue()));
            else if (entry.getKey() instanceof AEFluidKey fluid && fluid.getFluid().is(ExperienceOrbPickupStrategy.EXPERIENCE))
                suppliedFluid = Math.addExact(suppliedFluid, entry.getLongValue());
            else return null;
        }
        if (suppliedFluid > cost) return null;
        var ingredients = ArsScribesAccess.ingredients(holder.value());
        if (ingredients.size() > 4096) return null;
        var assigned = IngredientAssignment.assign(ingredients, items, 1, (ingredient, key) -> ingredient.test(key.toStack()));
        return assigned == null ? null : new Inputs(assigned.stream().map(AEItemKey::toStack).toList(), cost - suppliedFluid);
    }
    private static KeyCounter declared(IPatternDetails pattern) {
        var amounts = new KeyCounter();
        for (var input : pattern.getInputs()) {
            if (input.getPossibleInputs().length != 1) return null;
            var stack = input.getPossibleInputs()[0];
            amounts.set(stack.what(), Math.addExact(amounts.get(stack.what()), Math.multiplyExact(stack.amount(), input.getMultiplier())));
        }
        return amounts;
    }
    private static KeyCounter actual(KeyCounter[] inputs) {
        var amounts = new KeyCounter();
        for (var counter : inputs) for (var entry : counter) {
            if (entry.getLongValue() < 0) return null;
            if (entry.getLongValue() != 0) amounts.set(entry.getKey(), Math.addExact(amounts.get(entry.getKey()), entry.getLongValue()));
        }
        return amounts;
    }
    @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos, AllowedOutputFilter filter, IActionSource source) {
        var head = ArsScribesAccess.head(level, pos);
        return head == null ? List.of() : ArsScribesState.drain(head);
    }
    private static void failure(String phase, Throwable error) { CompatibilityDiagnostics.failure(ArsScribesAdapter.class, phase, error); }
}
