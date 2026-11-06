package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.items.IItemHandler;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;

/** Native Forge table operations shared without changing recipe or remainder semantics. */
public final class TableCraftingSupport {
    private TableCraftingSupport() {}

    public static List<GenericStack> virtualOutputs(ItemStack result, NonNullList<ItemStack> remaining,
                                                     int craftRatio) {
        var outputs = new ArrayList<GenericStack>();
        outputs.add(new GenericStack(AEItemKey.of(result), (long) craftRatio * result.getCount()));
        for (var stack : remaining) {
            if (!stack.isEmpty()) {
                outputs.add(new GenericStack(AEItemKey.of(stack), (long) craftRatio * stack.getCount()));
            }
        }
        return List.copyOf(outputs);
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ItemStack assemble(Object recipe, CraftingContainer input, ServerLevel level) {
        try {
            return recipe instanceof Recipe<?> r
                    ? ((Recipe) r).assemble(input, level.registryAccess())
                    : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean outputMatchesBatch(IPatternDetails pattern, ItemStack result,
                                               long expectedTotalCount) {
        var outputs = pattern.getOutputs();
        if (outputs.length != 1) {
            return false;
        }
        var expected = outputs[0];
        if (!(expected.what() instanceof AEItemKey expectedKey)
                || expected.amount() != expectedTotalCount) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? expectedKey.dropSecondary().equals(actual.dropSecondary())
                : expectedKey.equals(actual);
    }

    public static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.length == 1 && outputs[0].what() instanceof AEItemKey;
    }

    public static boolean gridIsEmpty(IItemHandler handler, int slots) {
        for (int i = 0; i < slots; i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static List<ItemStack> currentStacks(IItemHandler handler, int slots) {
        var stacks = NonNullList.withSize(slots, ItemStack.EMPTY);
        for (int i = 0; i < slots; i++) {
            stacks.set(i, handler.getStackInSlot(i).copy());
        }
        return stacks;
    }
}
