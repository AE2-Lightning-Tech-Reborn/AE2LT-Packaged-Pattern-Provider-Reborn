package com.moakiee.ae2lt.packaged.logic.multiblock.apothic;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.recipe.*;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.packaged.resource.ExperienceOrbPickupStrategy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Raven-only native infusion semantics with receipt-based auxiliary resource payment. */
public final class RavenInfusionAdapter implements MultiblockAdapter {
    private record Match(RecipeHolder<?> recipe) {}
    private record PaidInput(ItemStack result, int missingLapis, long missingFluid) {}
    @Override public int priority() { return 134; }
    @Override public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) { return AdapterIds.APOTHIC_INFUSION; }
    @Override public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return level.isLoaded(pos) && be != null && BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).equals(ApothicAccess.RAVEN);
    }
    @Override public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        if (!recognizesMain(level, pos, level.getBlockEntity(pos))) return null;
        try {
            var amounts = declared(pattern);
            var recipes = new ArrayList<RecipeHolder<?>>();
            recipes.addAll(level.getRecipeManager().getAllRecipesFor(RavenRecipes.TYPE.get()));
            recipes.addAll(ApothicAccess.recipes(level));
            for (var recipe : recipes) if (match(recipe, pattern, amounts) != null)
                return new BindingResult(new Match(recipe), BindingMode.REAL);
        } catch (RuntimeException | LinkageError e) { failure("bind", e); }
        return null;
    }
    @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        return recognizesMain(level, pos, level.getBlockEntity(pos)) && handle instanceof Match;
    }
    @Override public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        if (!(handle instanceof Match bound) || !canDispatch(level, pos, handle)
                || level.getRecipeManager().byKey(bound.recipe().id()).map(RecipeHolder::value).orElse(null) != bound.recipe().value()) return null;
        try {
            var amounts = actual(inputs);
            var matched = match(bound.recipe(), pattern, amounts);
            if (matched == null) return null;
            var table = level.getBlockEntity(pos);
            var resources = new RavenResources(table, source);
            if (!resources.available(matched.missingLapis(), matched.missingFluid())) return null;
            var escrow = new KeyCounter();
            boolean[] completed = {false};
            var targets = new ArrayList<TargetSlot>();
            for (var entry : amounts) if (entry.getLongValue() > 0) {
                var stack = new GenericStack(entry.getKey(), entry.getLongValue());
                targets.add(new TargetSlot(level, pos, null, List.of(stack), InsertionStrategy.CUSTOM, (incoming, mode) -> {
                    if (!canDispatch(level, pos, handle) || level.getBlockEntity(pos) != table || !incoming.equals(stack)) return 0L;
                    if (mode == Actionable.MODULATE) escrow.add(stack.what(), stack.amount());
                    return stack.amount();
                }));
            }
            return new DispatchPlan(targets, null, (accepted, sink) -> {
                if (completed[0]) return;
                for (var entry : escrow) if (entry.getLongValue() > 0) sink.accept(new GenericStack(entry.getKey(), entry.getLongValue()));
                escrow.clear();
            }, retain -> {
                if (!canDispatch(level, pos, handle) || level.getBlockEntity(pos) != table)
                    throw new IllegalStateException("Raven table removed before payment");
                boolean charged = false;
                try {
                    charged = resources.charge(matched.missingLapis(), matched.missingFluid());
                    if (!charged) throw new IllegalStateException("Raven infusion resources changed before payment");
                    retain.accept(new GenericStack(AEItemKey.of(matched.result()), matched.result().getCount()));
                    completed[0] = true;
                    table.setChanged();
                } finally {
                    if (!completed[0]) resources.refund(retain);
                }
            });
        } catch (RuntimeException | LinkageError e) { failure("plan", e); return null; }
    }
    private static PaidInput match(RecipeHolder<?> holder, IPatternDetails pattern, KeyCounter amounts) {
        if (amounts == null || pattern.getOutputs().size() != 1) return null;
        var recipe = holder.value();
        int xp = recipe instanceof RavenCoreRecipe ? RavenCoreRecipe.EXPERIENCE : ApothicAccess.experience(recipe);
        if (xp <= 0) return null;
        long cost = (long) xp * ExperienceOrbPickupStrategy.MB_PER_XP;
        long suppliedFluid = 0;
        for (var entry : amounts) {
            if (entry.getLongValue() <= 0) return null;
            if (entry.getKey() instanceof AEFluidKey fluid && fluid.getFluid().is(ExperienceOrbPickupStrategy.EXPERIENCE))
                suppliedFluid = Math.addExact(suppliedFluid, entry.getLongValue());
            else if (!(entry.getKey() instanceof AEItemKey)) return null;
        }
        if (suppliedFluid > cost) return null;
        var lapis = AEItemKey.of(Items.LAPIS_LAZULI);
        for (var candidate : amounts) {
            if (!(candidate.getKey() instanceof AEItemKey item)) continue;
            var input = item.toStack();
            if (recipe instanceof RavenCoreRecipe ? !input.is(PPItems.BASIC_PACKAGED_CORE.get()) : !ApothicAccess.input(recipe).test(input)) continue;
            long suppliedLapis = amounts.get(lapis) - (item.equals(lapis) ? 1 : 0);
            if (suppliedLapis < 0 || suppliedLapis > 3) continue;
            boolean valid = true;
            for (var entry : amounts) if (entry.getKey() instanceof AEItemKey key
                    && entry.getLongValue() != (key.equals(item) ? 1 : 0) + (key.equals(lapis) ? suppliedLapis : 0)) valid = false;
            if (!valid) continue;
            var result = recipe instanceof RavenCoreRecipe ? PPItems.APOTHIC_INFUSION_ADAPTER.toStack() : ApothicAccess.assemble(recipe, input);
            if (!result.isEmpty() && AEItemKey.of(result).equals(pattern.getPrimaryOutput().what())
                    && result.getCount() == pattern.getPrimaryOutput().amount())
                return new PaidInput(result, 3 - (int) suppliedLapis, cost - suppliedFluid);
        }
        return null;
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
        for (var input : inputs) for (var entry : input)
            amounts.set(entry.getKey(), Math.addExact(amounts.get(entry.getKey()), entry.getLongValue()));
        return amounts;
    }
    @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos, AllowedOutputFilter filter, IActionSource source) { return List.of(); }
    private static void failure(String phase, Throwable e) { CompatibilityDiagnostics.failure(RavenInfusionAdapter.class, phase, e); }
}
