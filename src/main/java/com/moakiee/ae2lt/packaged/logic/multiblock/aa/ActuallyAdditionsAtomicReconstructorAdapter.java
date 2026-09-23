package com.moakiee.ae2lt.packaged.logic.multiblock.aa;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Virtual adapter for Actually Additions' Atomic Reconstructor laser
 * conversions.
 *
 * <p>The Atomic Reconstructor is "instant" in spirit — once the beam fires
 * (every redstone pulse or once per tick when continuously powered) a single
 * item passes through the laser AABB and converts in the same tick. The mod's
 * baseline {@code MethodHandler.invokeReconstructor} contains no animation
 * delay; the ~visual delay players see is the item-entity drop / settle, not
 * the conversion itself.
 *
 * <p>So we equivalent-virtualize it: compute the output, consume the input
 * from AE, and surface the result through the virtual batch — but still
 * <b>extract the machine's internal RF/CF</b> via the AA API (the canonical
 * {@code IEnergyTile.extractEnergy(int)}), keeping the cost side-effect
 * faithful so the player still has to feed the reconstructor power. If the
 * reconstructor is starved, the batch returns null and the next push reattempts.
 *
 * <p>The default conversion-lens check is preserved as well: only the stock
 * conversion lens triggers vanilla recipes, so other lenses (death, miner,
 * color, …) are silently ignored — those have side effects that aren't safely
 * virtualizable.
 */
public final class ActuallyAdditionsAtomicReconstructorAdapter implements VirtualCraftingAdapter {

    private static final String MOD_ID = "actuallyadditions";
    private static final Identifier ATOMIC_RECONSTRUCTOR_BLOCK = aaId("atomic_reconstructor");
    private static final Identifier LASER_RECIPE_TYPE = aaId("laser");
    /**
     * Per-craft baseline RF charged by the reconstructor regardless of recipe
     * (matches AA's headroom over {@code recipe.getEnergy()} in 1.21 builds).
     */
    private static final int BASE_ENERGY_USE = 1000;
    private static final int MAX_INPUT_AMOUNT = 64;

    @Override
    public int priority() {
        return 90;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null
                && isActuallyAdditionsLoaded()
                && blockId(be.getBlockState()).equals(ATOMIC_RECONSTRUCTOR_BLOCK)
                && ActuallyAdditionsAtomicReconstructorAccess.isAtomicReconstructor(be);
    }

    @Override
    public Identifier requiredAdapterId(ServerLevel level, BlockPos pos) {
        return com.moakiee.ae2lt.packaged.item.AdapterIds.AA_RECONSTRUCTOR;
    }

    @Override
    public Identifier flushSoundId() {
        return null;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return null;
        }
        if (!hasSingleItemOutput(pattern)) {
            return null;
        }
        var found = findCandidateRecipe(level, pattern);
        if (found == null) {
            return null;
        }
        return new BindingResult(found, BindingMode.VIRTUAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        // Virtual-only path: live charge / lens checks happen inside
        // planVirtualWithBinding, so the gate is unconditional here.
        return true;
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        // Virtual-only.
        return null;
    }

    @Override
    @Nullable
    public VirtualCraftingResult planVirtualWithBinding(ServerLevel level, BlockPos mainPos,
                                                        IPatternDetails pattern, KeyCounter[] inputs,
                                                        Object handle, IActionSource source) {
        if (!(handle instanceof ReconstructorBindHandle bind)) {
            return null;
        }

        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return null;
        }
        // Lens gating: only the stock conversion lens runs vanilla recipes
        // virtualizably; specialized lenses (miner, death, ...) have side
        // effects we can't faithfully simulate without world interaction.
        if (!ActuallyAdditionsAtomicReconstructorAccess.hasDefaultConversionLens(be)) {
            return null;
        }

        var input = singleInput(inputs);
        if (input == null) {
            return null;
        }

        var ingredient = ActuallyAdditionsAtomicReconstructorAccess.getInput(bind.recipe());
        if (ingredient == null || !ingredient.test(input.singleStack())) {
            return null;
        }
        var result = resultItem(bind.recipe(), level);
        if (result.isEmpty()) {
            return null;
        }

        // Parallel batch accounting: one push covers the whole batch encoded into the
        // pattern, so we derive the expected total output from input ratio
        // and reject the recipe only when the pattern's declared output
        // doesn't line up.
        long perCraftInput = perCraftInputAmount(pattern);
        if (perCraftInput <= 0 || input.amount() % perCraftInput != 0) {
            return null;
        }
        long craftRatio = input.amount() / perCraftInput;
        long totalCount;
        try {
            totalCount = Math.multiplyExact(craftRatio, (long) result.getCount());
        } catch (ArithmeticException ignored) {
            return null;
        }
        if (totalCount <= 0 || totalCount > Integer.MAX_VALUE) {
            return null;
        }
        if (!outputKeyMatchesBatch(pattern, result, totalCount)) {
            return null;
        }

        // Charge accounting:
        //   per-craft cost = BASE_ENERGY_USE + recipe.getEnergy()
        //   total           = per-craft * craftRatio   (NOT * input.amount,
        //   because each craft transforms a single recipe-input quantity)
        long perCraftCost = (long) BASE_ENERGY_USE + bind.recipeEnergy();
        long totalCost;
        try {
            totalCost = Math.multiplyExact(perCraftCost, craftRatio);
        } catch (ArithmeticException ignored) {
            return null;
        }
        if (totalCost > Integer.MAX_VALUE) {
            return null;
        }
        int storedCharge = ActuallyAdditionsAtomicReconstructorAccess.getEnergy(be);
        if (storedCharge < totalCost) {
            return null;
        }

        // Modulate phase — side effect is intentional and matches what the
        // reconstructor would have drawn for craftRatio physical crafts.
        if (!ActuallyAdditionsAtomicReconstructorAccess.extractEnergy(be, (int) totalCost)) {
            return null;
        }

        return new VirtualCraftingResult(List.of(
                new GenericStack(AEItemKey.of(result), totalCount)));
    }

    @Override
    public void onVirtualBatchFlush(ServerLevel level, BlockPos mainPos, Object handle, IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return;
        }
        ActuallyAdditionsAtomicReconstructorAccess.invokeReconstructor(be);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        // Virtual path produces no in-world items.
        return List.of();
    }

    /**
     * Bind-time recipe search: pick the recipe whose result <i>item</i>
     * matches the pattern's primary output, ignoring amount. Batch
     * verification is deferred to plan time where the input ratio is known.
     */
    @Nullable
    private static ReconstructorBindHandle findCandidateRecipe(ServerLevel level, IPatternDetails pattern) {
        for (var holder : recipes(level)) {
            var recipe = holder.value();
            int energy = ActuallyAdditionsAtomicReconstructorAccess.getRecipeEnergy(recipe);
            if (energy <= 0) {
                continue;
            }
            var result = resultItem(recipe, level);
            if (result.isEmpty()) {
                continue;
            }
            if (!outputKeyMatchesByItem(pattern, result)) {
                continue;
            }
            return new ReconstructorBindHandle(recipe, energy);
        }
        return null;
    }

    private static boolean outputKeyMatchesByItem(IPatternDetails pattern, ItemStack result) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) {
            return false;
        }
        var expected = outputs.getFirst();
        if (!(expected.what() instanceof AEItemKey expectedKey)) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? expectedKey.dropSecondary().equals(actual.dropSecondary())
                : expectedKey.equals(actual);
    }

    private static boolean outputKeyMatchesBatch(IPatternDetails pattern, ItemStack result,
                                                  long expectedTotalCount) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) {
            return false;
        }
        var expected = outputs.getFirst();
        if (!(expected.what() instanceof AEItemKey expectedKey)) {
            return false;
        }
        if (expected.amount() != expectedTotalCount) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? expectedKey.dropSecondary().equals(actual.dropSecondary())
                : expectedKey.equals(actual);
    }

    private static long perCraftInputAmount(IPatternDetails pattern) {
        var inputs = pattern.getInputs();
        if (inputs.length > 0) {
            return Math.max(1, inputs[0].getMultiplier());
        }
        return 1L;
    }

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    @Nullable
    private static PlannedInput singleInput(KeyCounter[] inputs) {
        AEItemKey key = null;
        long amount = 0;

        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long entryAmount = entry.getLongValue();
                if (entryAmount <= 0) {
                    continue;
                }
                if (key == null) {
                    key = itemKey;
                } else if (!key.equals(itemKey)) {
                    return null;
                }
                amount += entryAmount;
                if (amount > MAX_INPUT_AMOUNT) {
                    return null;
                }
            }
        }

        return key != null && amount > 0 ? new PlannedInput(key, amount) : null;
    }

    private static ItemStack resultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> typedRecipe)) {
            return ItemStack.EMPTY;
        }
        try {
            return com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26.resultItem(typedRecipe, level).copy();
        } catch (RuntimeException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(LASER_RECIPE_TYPE)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26.byType(level, type))
                .orElse(List.of());
    }

    private static boolean isActuallyAdditionsLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static Identifier blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static Identifier aaId(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private record PlannedInput(AEItemKey key, long amount) {
        PlannedInput {
            Objects.requireNonNull(key, "key");
        }

        ItemStack singleStack() {
            return key.toStack(1);
        }
    }

    /** Opaque binding handle returned from {@link #bind}. */
    private record ReconstructorBindHandle(Object recipe, int recipeEnergy) {
    }

}
