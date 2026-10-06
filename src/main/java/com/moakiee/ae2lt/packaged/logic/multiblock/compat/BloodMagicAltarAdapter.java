package com.moakiee.ae2lt.packaged.logic.multiblock.compat;

import java.util.List;
import java.util.ArrayList;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterBlocks;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.SlotHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterRecipeTypes;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import com.moakiee.ae2lt.packaged.logic.multiblock.BlockCapabilities;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

public final class BloodMagicAltarAdapter implements MultiblockAdapter {
    private static final ResourceLocation BLOCK = new ResourceLocation("bloodmagic", "altar");
    private static final ResourceLocation TYPE = new ResourceLocation("bloodmagic", "altar");
    private static final String MOD_ID = "bloodmagic";
    private static final String HARVEST_STATE = "bloodmagic_altar:harvest";

    @Override public int priority() { return 100; }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
        return be != null && ModList.get().isLoaded(MOD_ID)
                && BLOCK.equals(AdapterBlocks.idOf(be.getBlockState()));
    }

    @Override public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.BLOOD_MAGIC_ALTAR;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        if (!recognizesMain(level, mainPos, level.getBlockEntity(mainPos)) || pattern.getOutputs().length != 1
                || !(pattern.getOutputs()[0].what() instanceof AEItemKey)) return null;
        var candidates = new ArrayList<Object>();
        for (var recipe : recipes(level)) {
            var input = invoke(recipe, "getInput", Ingredient.class);
            var output = output(recipe, level);
            if (input != null && output != null && outputMatches(pattern, output)) candidates.add(recipe);
        }
        return candidates.isEmpty() ? null : new BindingResult(new Handle(List.copyOf(candidates)), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        return canDispatch(level, mainPos, handle, AdapterPersistentScope.NOOP);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle, AdapterPersistentScope scope) {
        if (!(handle instanceof Handle) || !recognizesMain(level, mainPos, level.getBlockEntity(mainPos))) return false;
        var entity = level.getBlockEntity(mainPos);
        if (!Boolean.FALSE.equals(invoke(entity, "isActive", Boolean.class))
                || ReflectionSupport.findMethodCached(entity.getClass(), "startCycle").isEmpty()) return false;
        var handler = handler(level, mainPos);
        boolean empty = handler != null && handler.getSlots() > 0 && handler.getStackInSlot(0).isEmpty();
        return empty && !SlotHarvestOwnership.blocksDispatch(level, mainPos, scope, HARVEST_STATE, empty);
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos, IPatternDetails pattern,
                                        KeyCounter[] inputs, Object handle, IActionSource source) {
        return planWithBinding(level, mainPos, pattern, inputs, handle, source, AdapterPersistentScope.NOOP);
    }

    @Override
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos, IPatternDetails pattern,
                                        KeyCounter[] inputs, Object handle, IActionSource source,
                                        AdapterPersistentScope scope) {
        if (!(handle instanceof Handle bound) || !canDispatch(level, mainPos, handle, scope)) return null;
        var input = singleInput(inputs);
        if (input == null || input.amount != 1) return null;
        for (var recipe : bound.recipes) {
            var ingredient = invoke(recipe, "getInput", Ingredient.class);
            var output = output(recipe, level);
            var tier = invoke(level.getBlockEntity(mainPos), "getTier", Integer.class);
            var minimumTier = invoke(recipe, "getMinimumTier", Integer.class);
            if (ingredient != null && output != null && ingredient.test(input.stack())
                    && outputMatches(pattern, output) && tier != null && minimumTier != null
                    && meetsMinimumTier(tier, minimumTier)) {
                var target = new TargetSlot(level, mainPos, null, List.of(new GenericStack(input.key, 1)),
                        InsertionStrategy.CUSTOM, inserter(level, mainPos, input.stack()));
                return new DispatchPlan(List.of(target), () -> {
                    SlotHarvestOwnership.store(level, mainPos, scope, HARVEST_STATE, output, ((Recipe<?>) recipe).getId());
                    invokeVoid(level.getBlockEntity(mainPos), "startCycle");
                    if (!Boolean.TRUE.equals(invoke(level.getBlockEntity(mainPos), "isActive", Boolean.class))) {
                        throw new IllegalStateException("Blood altar refused to start cycle");
                    }
                }, (accepted, recovered) -> {
                    if (accepted.size() != 1 || accepted.get(0).target() != target) return;
                    var entity = level.getBlockEntity(mainPos);
                    if (entity == null || !Boolean.FALSE.equals(invoke(entity, "isActive", Boolean.class))) return;
                    var extracted = recoverInput(handler(level, mainPos), input.stack());
                    if (extracted.isEmpty()) return;
                    recovered.accept(new GenericStack(input.key, extracted.getCount()));
                    SlotHarvestOwnership.clear(level, mainPos, scope, HARVEST_STATE);
                });
            }
        }
        return null;
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                              AllowedOutputFilter filter, IActionSource source) {
        return List.of();
    }

    @Override
    public boolean supportsPatternIndependentHarvest() {
        return true;
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                              AllowedOutputFilter filter, IActionSource source,
                                              AdapterPersistentScope scope) {
        if (!recognizesMain(level, mainPos, level.getBlockEntity(mainPos))) return List.of();
        if (!Boolean.FALSE.equals(invoke(level.getBlockEntity(mainPos), "isActive", Boolean.class))) return List.of();
        var ownership = SlotHarvestOwnership.load(level, mainPos, scope, HARVEST_STATE);
        if (ownership == null) return List.of();
        var handler = handler(level, mainPos);
        if (handler == null || handler.getSlots() == 0) return List.of();
        var stack = handler.getStackInSlot(0);
        var expected = ownership.expectedOutput();
        if (stack.getCount() != expected.getCount() || !ItemStack.isSameItemSameTags(stack, expected)
                || !filter.matches(AEItemKey.of(stack))) return List.of();
        var extracted = handler.extractItem(0, stack.getCount(), false);
        if (!extracted.isEmpty()) SlotHarvestOwnership.clear(level, mainPos, scope, HARVEST_STATE);
        return extracted.isEmpty() ? List.of() : List.of(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
    }

    private static BiFunction<GenericStack, Actionable, Long> inserter(ServerLevel level, BlockPos pos, ItemStack expected) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !(stack.what() instanceof AEItemKey key)
                    || !ItemStack.isSameItemSameTags(expected, key.toStack())) return 0L;
            var handler = handler(level, pos);
            if (handler == null || handler.getSlots() == 0 || !handler.getStackInSlot(0).isEmpty()) return 0L;
            var remainder = handler.insertItem(0, expected.copy(), mode == Actionable.SIMULATE);
            return remainder.isEmpty() ? 1L : 0L;
        };
    }

    static ItemStack recoverInput(@Nullable IItemHandler handler, ItemStack expected) {
        if (handler == null || handler.getSlots() == 0) return ItemStack.EMPTY;
        var current = handler.getStackInSlot(0);
        if (current.getCount() != 1 || !ItemStack.isSameItemSameTags(current, expected)) return ItemStack.EMPTY;
        var simulated = handler.extractItem(0, 1, true);
        if (simulated.getCount() != 1 || !ItemStack.isSameItemSameTags(simulated, expected)) return ItemStack.EMPTY;
        var extracted = handler.extractItem(0, 1, false);
        return extracted.getCount() == 1 && ItemStack.isSameItemSameTags(extracted, expected)
                ? extracted : ItemStack.EMPTY;
    }

    @Nullable private static IItemHandler handler(ServerLevel level, BlockPos pos) {
        return BlockCapabilities.find(level, pos, net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"}) private static List<Recipe<?>> recipes(ServerLevel level) {
        var type = AdapterRecipeTypes.find(TYPE);
        if (type == null) return List.of();
        return (List<Recipe<?>>)(List<?>)level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }

    @Nullable private static ItemStack output(Object recipe, ServerLevel level) {
        var direct = invoke(recipe, "getOutput", ItemStack.class);
        if (direct != null) return direct.copy();
        if (recipe instanceof Recipe<?> vanilla) try { return vanilla.getResultItem(level.registryAccess()).copy(); }
        catch (RuntimeException ignored) { }
        return null;
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack output) {
        var expected = pattern.getOutputs()[0];
        return expected.what() instanceof AEItemKey key && matchesOutput(
                key, expected.amount(), output, OverloadPatternSemantics.isIdOnlyOutput(pattern, 0));
    }

    static boolean matchesOutput(AEItemKey expected, long amount, ItemStack output, boolean idOnly) {
        if (output.isEmpty() || amount != output.getCount()) return false;
        var actual = AEItemKey.of(output);
        return idOnly ? expected.dropSecondary().equals(actual.dropSecondary()) : expected.equals(actual);
    }

    static boolean meetsMinimumTier(int tier, int minimumTier) {
        return tier > minimumTier;
    }

    @Nullable private static <T> T invoke(Object target, String name, Class<T> type) {
        if (target == null) return null;
        return ReflectionSupport.findMethodCached(target.getClass(), name)
                .flatMap(method -> ReflectionSupport.invoke(method, target))
                .filter(type::isInstance).map(type::cast).orElse(null);
    }
    private static void invokeVoid(Object target, String name) {
        if (target == null) throw new IllegalStateException("Blood altar is unavailable");
        var method = ReflectionSupport.findMethodCached(target.getClass(), name).orElse(null);
        ReflectionSupport.invokeMutation(method, target);
    }

    private record Handle(List<Object> recipes) { }
    private record Input(AEItemKey key, ItemStack stack, long amount) { }
    @Nullable private static Input singleInput(KeyCounter[] inputs) {
        AEItemKey key = null; long amount = 0;
        for (var counter : inputs) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey itemKey) || (key != null && !key.equals(itemKey))) return null;
            key = itemKey; amount += entry.getLongValue();
        }
        return key == null || amount <= 0 ? null : new Input(key, key.toStack((int)Math.min(amount, Integer.MAX_VALUE)), amount);
    }
}
