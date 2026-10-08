package com.moakiee.ae2lt.packaged.logic.multiblock.chisel;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/** Chipped 3.x uses a recipe type per workbench and requires both physical halves. */
public final class ChippedWorkbenchAdapter implements VirtualCraftingAdapter {
    private static final Set<String> BENCHES = Set.of("botanist_workbench", "glassblower", "carpenters_table",
            "loom_table", "mason_table", "alchemy_bench", "tinkering_table");
    private static Method recipeType;
    private static Method getResults;

    @Override public int priority() { return 120; }
    @Override public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) { return AdapterIds.CHISELING; }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
        var state = level.getBlockState(pos);
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("chipped") || !BENCHES.contains(id.getPath())
                || !model(state).equals("main") || !state.hasProperty(HorizontalDirectionalBlock.FACING)) return false;
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        var sidePos = pos.relative(facing.getClockWise());
        if (!level.isLoaded(sidePos)) return false;
        var side = level.getBlockState(sidePos);
        return side.is(state.getBlock()) && model(side).equals("side")
                && side.getValue(HorizontalDirectionalBlock.FACING) == facing;
    }

    private static String model(BlockState state) {
        for (var property : state.getProperties()) {
            if (property.getName().equals("model")) return valueName(state, property);
        }
        return "";
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean permits(ServerLevel level, BlockPos pos, ChiselConversion conversion) {
        if (conversion.inputAmount() != conversion.outputAmount()) return false;
        try {
            if (recipeType == null) {
                recipeType = Class.forName("earth.terrarium.chipped.common.blocks.WorkbenchBlock")
                        .getMethod("recipeType");
            }
            var type = (RecipeType<?>) recipeType.invoke(level.getBlockState(pos).getBlock());
            if (type == null) return false;
            var input = new SimpleContainer(conversion.input().toStack(1));
            var recipe = (Recipe<?>) level.getRecipeManager().getRecipeFor((RecipeType) type, input, level).orElse(null);
            if (recipe == null) return false;
            if (getResults == null) {
                getResults = Class.forName("earth.terrarium.chipped.common.recipes.ChippedRecipe")
                        .getMethod("getResults", ItemStack.class);
            }
            try (var results = (Stream<?>) getResults.invoke(recipe, conversion.input().toStack(1))) {
                return results.anyMatch(value -> value instanceof ItemStack stack && !stack.isEmpty()
                        && conversion.output().equals(AEItemKey.of(stack)));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            CompatibilityDiagnostics.failure(ChippedWorkbenchAdapter.class, "results", failure);
            return false;
        }
    }

    @Override @Nullable
    public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        if (!recognizesMain(level, pos, level.getBlockEntity(pos))) return null;
        var conversion = ChiselConversion.fromPattern(pattern);
        return conversion != null && permits(level, pos, conversion) ? new BindingResult(conversion, BindingMode.VIRTUAL) : null;
    }

    @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        return recognizesMain(level, pos, level.getBlockEntity(pos));
    }
    @Override @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) { return null; }

    @Override @Nullable
    public VirtualCraftingResult planVirtualWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        if (!(handle instanceof ChiselConversion conversion) || !canDispatch(level, pos, handle)
                || !conversion.equals(ChiselConversion.fromPattern(pattern)) || !conversion.matchesInputs(inputs)
                || !permits(level, pos, conversion)) return null;
        return new VirtualCraftingResult(List.of(new GenericStack(conversion.output(), conversion.outputAmount())));
    }

    @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos,
            AllowedOutputFilter filter, IActionSource source) { return List.of(); }
}
