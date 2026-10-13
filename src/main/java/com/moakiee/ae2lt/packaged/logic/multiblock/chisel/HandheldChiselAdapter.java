package com.moakiee.ae2lt.packaged.logic.multiblock.chisel;

import java.util.List;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.ProviderInternalCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class HandheldChiselAdapter implements ProviderInternalCraftingAdapter {
    private final boolean rechiseled;

    public HandheldChiselAdapter(boolean rechiseled) { this.rechiseled = rechiseled; }

    @Override public int priority() { return 120; }
    @Override public Identifier internalLaneId() { return AdapterIds.CHISELING; }
    @Override public Identifier requiredAdapterId(ServerLevel level, BlockPos pos) { return internalLaneId(); }
    // Internal adapters must never claim a neighbor or wireless target.
    @Override public boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) { return false; }

    private boolean permits(ServerLevel level, ChiselConversion conversion) {
        return rechiseled ? RechiseledAccess.permits(level, conversion) : ChiselAccess.permits(conversion);
    }

    @Override @Nullable
    public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        var conversion = ChiselConversion.fromPattern(pattern);
        return conversion != null && permits(level, conversion) ? new BindingResult(conversion, BindingMode.VIRTUAL) : null;
    }

    @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) { return true; }
    @Override @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) { return null; }

    @Override @Nullable
    public VirtualCraftingResult planVirtualWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        if (!(handle instanceof ChiselConversion conversion)
                || !conversion.equals(ChiselConversion.fromPattern(pattern)) || !conversion.matchesInputs(inputs)
                || !permits(level, conversion)) return null;
        return new VirtualCraftingResult(List.of(new GenericStack(conversion.output(), conversion.outputAmount())));
    }

    @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos,
            AllowedOutputFilter filter, IActionSource source) { return List.of(); }
}
