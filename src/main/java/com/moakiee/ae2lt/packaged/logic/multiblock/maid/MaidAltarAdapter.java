package com.moakiee.ae2lt.packaged.logic.multiblock.maid;

import java.util.*;
import java.util.function.Consumer;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.IngredientAssignment;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.resource.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/** Native six-pillar altar crafts plus the explicit 1 item -> 0.6 P conversion. */
public final class MaidAltarAdapter implements VirtualCraftingAdapter {
    public static final long POWER_PER_ITEM = 60;
    public static final ResourceLocation POWER_ITEM = new ResourceLocation("touhou_little_maid:power_point");
    private enum Conversion { INSTANCE }
    private record Match(Recipe<Container> recipe, long power) {}
    @Override public int priority() { return 135; }
    @Override public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) { return AdapterIds.MAID_ALTAR; }
    @Override public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) { return MaidAltarAccess.isAltar(be); }

    @Override public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        if (MaidAltarAccess.layout(level, pos, false) == null) return null;
        var amounts = declared(pattern);
        if (conversion(pattern, amounts) != null) return new BindingResult(Conversion.INSTANCE, BindingMode.VIRTUAL);
        var type = MaidAltarAccess.recipeType();
        if (type == null) return null;
        for (var recipe : level.getRecipeManager().getAllRecipesFor(type)) {
            long power = MaidPowerAmounts.recipeCost(MaidAltarAccess.power(recipe));
            if (power >= 0 && MaidAltarAccess.itemCraft(recipe) && matchesOutput(level, recipe, pattern)
                    && assign(recipe, amounts, power) != null) {
                return new BindingResult(new Match(recipe, power), BindingMode.REAL);
            }
        }
        return null;
    }
    @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        return MaidAltarAccess.layout(level, pos, true) != null;
    }
    @Override public VirtualCraftingResult planVirtualWithBinding(ServerLevel level, BlockPos pos,
            IPatternDetails pattern, KeyCounter[] inputs, Object handle, IActionSource source) {
        return handle == Conversion.INSTANCE && canDispatch(level, pos, handle) ? conversion(pattern, actual(inputs)) : null;
    }
    @Override public ResourceLocation flushSoundId() { return new ResourceLocation("touhou_little_maid:altar_craft"); }

    private static VirtualCraftingResult conversion(IPatternDetails pattern, KeyCounter amounts) {
        if (amounts == null) return null;
        var item = AEItemKey.of(BuiltInRegistries.ITEM.get(POWER_ITEM));
        long count = amounts.get(item);
        if (count <= 0 || count > Long.MAX_VALUE / POWER_PER_ITEM) return null;
        for (var entry : amounts) if (entry.getLongValue() > 0 && !entry.getKey().equals(item)) return null;
        long power = count * POWER_PER_ITEM;
        return output(pattern, MaidPowerKey.INSTANCE, power)
                ? new VirtualCraftingResult(List.of(new GenericStack(MaidPowerKey.INSTANCE, power))) : null;
    }
    @Override public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        if (!(handle instanceof Match match)
                || level.getRecipeManager().byKey(match.recipe().getId()).orElse(null) != match.recipe()
                || !matchesOutput(level, match.recipe(), pattern)) return null;
        var layout = MaidAltarAccess.layout(level, pos, true);
        var keys = assign(match.recipe(), actual(inputs), match.power());
        if (layout == null || keys == null || keys.size() > 6) return null;
        var grid = new ArrayList<ItemStack>();
        keys.forEach(key -> grid.add(key.toStack()));
        while (grid.size() < 6) grid.add(ItemStack.EMPTY);
        // Native lookup, including conflicts and datapack ordering, remains authoritative.
        if (level.getRecipeManager().getRecipeFor(MaidAltarAccess.recipeType(), MaidAltarAccess.input(grid), level)
                .map(Recipe::getId).filter(match.recipe().getId()::equals).isEmpty()) return null;

        long[] escrow = {0};
        boolean[] emitted = {false};
        var targets = new ArrayList<TargetSlot>();
        if (match.power() > 0) {
            targets.add(new TargetSlot(level, layout.anchor().getBlockPos(), null,
                    List.of(new GenericStack(MaidPowerKey.INSTANCE, match.power())), InsertionStrategy.CUSTOM,
                    (stack, mode) -> {
                        if (!MaidAltarAccess.live(layout.anchor()) || stack.what() != MaidPowerKey.INSTANCE) return 0L;
                        long accepted = Math.min(stack.amount(), match.power() - escrow[0]);
                        if (mode == Actionable.MODULATE) escrow[0] += accepted;
                        return accepted;
                    }));
        }
        for (int i = 0; i < keys.size(); i++) {
            var pillar = layout.pillars().get(i);
            var key = keys.get(i);
            targets.add(new TargetSlot(level, pillar.getBlockPos(), null, List.of(new GenericStack(key, 1)),
                    InsertionStrategy.CUSTOM, (stack, mode) -> {
                        if (!MaidAltarAccess.live(pillar) || !key.equals(stack.what()) || stack.amount() != 1) return 0L;
                        long accepted = 1L - MaidAltarAccess.inventory(pillar).insertItem(0, key.toStack(), mode == Actionable.SIMULATE).getCount();
                        if (accepted > 0 && mode == Actionable.MODULATE) MaidAltarAccess.refresh(pillar);
                        return accepted;
                    }));
        }
        return new DispatchPlan(targets, null, (accepted, sink) -> {
            for (var receipt : accepted) {
                if (receipt.stack().what() == MaidPowerKey.INSTANCE) {
                    long refund = Math.min(escrow[0], receipt.stack().amount());
                    escrow[0] -= refund;
                    if (refund > 0) sink.accept(new GenericStack(MaidPowerKey.INSTANCE, refund));
                } else if (!emitted[0]) {
                    var pillar = level.getBlockEntity(receipt.target().pos());
                    if (!MaidAltarAccess.isAltar(pillar) || !MaidAltarAccess.live(pillar)) continue;
                    var inventory = MaidAltarAccess.inventory(pillar);
                    if (!receipt.stack().what().equals(AEItemKey.of(inventory.getStackInSlot(0)))) continue;
                    var recovered = inventory.extractItem(0, 1, false);
                    if (!recovered.isEmpty()) {
                        sink.accept(new GenericStack(AEItemKey.of(recovered), recovered.getCount()));
                        MaidAltarAccess.refresh(pillar);
                    }
                }
            }
        }, sink -> {
            if (escrow[0] != match.power() || MaidAltarAccess.layout(level, pos, false) == null
                    || level.getRecipeManager().getRecipeFor(MaidAltarAccess.recipeType(), MaidAltarAccess.input(layout), level)
                    .map(Recipe::getId).filter(match.recipe().getId()::equals).isEmpty()) {
                throw new IllegalStateException("Maid altar changed before commit");
            }
            var area = new AABB(layout.output()).inflate(1);
            var before = new HashSet<UUID>();
            level.getEntitiesOfClass(ItemEntity.class, area).forEach(entity -> before.add(entity.getUUID()));
            var actor = MaidAltarAccess.actor(level);
            MaidAltarAccess.setPower(actor, MaidAltarAccess.power(match.recipe()));
            try {
                MaidAltarAccess.craft(level, layout, actor);
            } finally {
                // Only newly emitted native items are owned by this transaction.
                // Nearby manual drops never enter the provider's return queue.
                long remaining = MaidPowerAmounts.recipeCost(MaidAltarAccess.remainingPower(actor));
                escrow[0] = Math.max(0, Math.min(match.power(), remaining));
                MaidAltarAccess.setPower(actor, 0);
                for (var entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
                    if (before.contains(entity.getUUID()) || entity.getItem().isEmpty()) continue;
                    emitted[0] = true;
                    sink.accept(new GenericStack(AEItemKey.of(entity.getItem()), entity.getItem().getCount()));
                    entity.discard();
                }
            }
            if (!emitted[0] || escrow[0] != 0) throw new IllegalStateException("Maid altar did not complete native craft");
        });
    }
    @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos, AllowedOutputFilter filter, IActionSource source) {
        return List.of(); // Native outputs are retained during the same server-thread commit.
    }
    private static boolean matchesOutput(ServerLevel level, Recipe<?> recipe, IPatternDetails pattern) {
        var result = recipe.getResultItem(level.registryAccess());
        return !result.isEmpty() && output(pattern, AEItemKey.of(result), result.getCount());
    }
    private static boolean output(IPatternDetails pattern, AEKey key, long amount) {
        return pattern.getOutputs().length == 1 && pattern.getPrimaryOutput().what().equals(key)
                && pattern.getPrimaryOutput().amount() == amount;
    }
    private static List<AEItemKey> assign(Recipe<?> recipe, KeyCounter amounts, long cost) {
        if (amounts == null || amounts.get(MaidPowerKey.INSTANCE) != cost) return null;
        var inputs = new ArrayList<IngredientAssignment.Input<AEItemKey>>();
        for (var entry : amounts) {
            if (entry.getLongValue() <= 0) continue;
            if (entry.getKey() instanceof AEItemKey item) inputs.add(new IngredientAssignment.Input<>(item, entry.getLongValue()));
            else if (entry.getKey() != MaidPowerKey.INSTANCE) return null;
        }
        return IngredientAssignment.assign(recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList(), inputs, 1, (ingredient, key) -> ingredient.test(key.toStack()));
    }
    static KeyCounter declared(IPatternDetails pattern) {
        var amounts = new KeyCounter();
        try {
            for (var input : pattern.getInputs()) {
                if (input.getPossibleInputs().length != 1) return null;
                var stack = input.getPossibleInputs()[0];
                amounts.set(stack.what(), Math.addExact(amounts.get(stack.what()), Math.multiplyExact(stack.amount(), input.getMultiplier())));
            }
        } catch (ArithmeticException e) { return null; }
        return amounts;
    }
    static KeyCounter actual(KeyCounter[] inputs) {
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
