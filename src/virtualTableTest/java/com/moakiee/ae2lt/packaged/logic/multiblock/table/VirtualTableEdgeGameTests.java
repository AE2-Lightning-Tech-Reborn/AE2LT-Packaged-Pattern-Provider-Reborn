package com.moakiee.ae2lt.packaged.logic.multiblock.table;

import static com.moakiee.ae2lt.packaged.logic.multiblock.table.VirtualTableTestSupport.*;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class VirtualTableEdgeGameTests {
    @GameTest(template = "workbench_test")
    public static void allEightTableSizesAcceptFullGridAndRejectExtraUnit(GameTestHelper h) {
        for (var family : Family.values()) for (int tier = 1; tier <= 4; tier++) {
            int size = tier * 2 + 1;
            var shape = new ShapedRecipePattern(size, size, NonNullList.withSize(size * size, Ingredient.of(Items.IRON_INGOT)), java.util.Optional.empty());
            try (var scope = new RecipeScope(h, family, family.shaped(shape, new ItemStack(Items.DIAMOND), tier, true))) {
                var f = table(h, family, tier);
                var inputs = List.of(item(Items.IRON_INGOT, size * size));
                var p = pattern(h, inputs, List.of(item(Items.DIAMOND, 1)));
                h.assertTrue(f.plan(p, counters(inputs)) != null, "full " + size + "x" + size + " native grid: " + family);
                h.assertTrue(f.plan(p, counters(List.of(item(Items.IRON_INGOT, size * size + 1)))) == null, "extra unit cannot be lost");
                h.assertTrue(java.util.stream.IntStream.range(0, size * size).allMatch(i -> f.inventory().getStackInSlot(i).isEmpty()), "virtual planning never fills grid");
            }
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void holesRectanglesAndOversizedWidthsRespectNativeShape(GameTestHelper h) {
        for (var family : Family.values()) {
            var shape = ShapedRecipePattern.of(Map.of('I', Ingredient.of(Items.IRON_INGOT), 'G', Ingredient.of(Items.GOLD_INGOT)), "I I", " G ");
            try (var scope = new RecipeScope(h, family, family.shaped(shape, new ItemStack(Items.DIAMOND), 1, false))) {
                for (int tier = 1; tier <= 4; tier++) {
                    var f = table(h, family, tier);
                    var inputs = List.of(item(Items.GOLD_INGOT, 1), item(Items.IRON_INGOT, 2));
                    h.assertTrue(f.plan(pattern(h, inputs, List.of(item(Items.DIAMOND, 1))), counters(inputs)) != null, "holes and rectangular shape retained: " + family);
                }
            }
            var tooWide = new ShapedRecipePattern(5, 1, NonNullList.withSize(5, Ingredient.of(Items.IRON_INGOT)), java.util.Optional.empty());
            try (var scope = new RecipeScope(h, family, family.shaped(tooWide, new ItemStack(Items.DIAMOND), 1, false))) {
                var f = table(h, family, 1);
                var in = List.of(item(Items.IRON_INGOT, 5));
                h.assertTrue(f.plan(pattern(h, in, List.of(item(Items.DIAMOND, 1))), counters(in)) == null, "five occupied cells do not imply width five fits 3x3");
            }
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void exactTierAndMinimumTierFollowNativeRules(GameTestHelper h) {
        for (var family : Family.values()) {
            var shape = ShapedRecipePattern.of(Map.of('I', Ingredient.of(Items.IRON_INGOT)), "III");
            try (var scope = new RecipeScope(h, family, family.shaped(shape, new ItemStack(Items.DIAMOND), 2, true))) {
                for (int tier = 1; tier <= 4; tier++) {
                    var f = table(h, family, tier);
                    var in = List.of(item(Items.IRON_INGOT, 3));
                    h.assertTrue((f.plan(pattern(h, in, List.of(item(Items.DIAMOND, 1))), counters(in)) != null) == (tier == 2), "exact tier 2: " + family + " tier=" + tier);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void shapelessOverlapBacktracksAndPreservesComponentIdentity(GameTestHelper h) {
        for (var family : Family.values()) {
            var named = new ItemStack(Items.IRON_INGOT);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("required"));
            var ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT), DataComponentIngredient.of(false, named));
            try (var scope = new RecipeScope(h, family, family.shapeless(ingredients, new ItemStack(Items.DIAMOND), 1))) {
                var f = table(h, family, 1);
                var in = List.of(new GenericStack(AEItemKey.of(named), 1), item(Items.IRON_INGOT, 1));
                var p = pattern(h, in, List.of(item(Items.DIAMOND, 1)));
                h.assertTrue(f.plan(p, counters(in)) != null, "broad ingredient cannot steal only component-sensitive key");
                h.assertTrue(f.plan(p, counters(List.of(item(Items.IRON_INGOT, 2)))) == null, "wrong actual components rejected");
            }
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void wrongCountsTypesOutputsAndForeignHandlesLeaveInputsUntouched(GameTestHelper h) {
        for (var family : Family.values()) try (var scope = new RecipeScope(h, family, simple(family))) {
            var f = table(h, family, 1);
            var p = pattern(h, List.of(item(Items.IRON_INGOT, 3)), List.of(item(Items.DIAMOND, 1)));
            var binding = f.adapter().bind(h.getLevel(), f.pos(), p);
            h.assertTrue(binding != null, "simple recipe binds");
            for (var in : List.of(List.of(item(Items.IRON_INGOT, 2)), List.of(item(Items.IRON_INGOT, 4)),
                    List.of(item(Items.IRON_INGOT, 3), item(Items.DIRT, 1)), List.of(item(Items.GOLD_INGOT, 3)),
                    List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 3)))) {
                var counters = counters(in);
                h.assertTrue(f.adapter().planVirtualWithBinding(h.getLevel(), f.pos(), p, counters, binding.handle(), SOURCE) == null, "wrong actual input refused");
                for (var stack : in) h.assertTrue(counters[0].get(stack.what()) == stack.amount(), "failed plan preserves caller ownership");
            }
            for (var out : List.of(List.of(item(Items.DIAMOND, 2)), List.of(item(Items.EMERALD, 1)),
                    List.of(item(Items.DIAMOND, 1), item(Items.DIRT, 1)), List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 1)))) {
                h.assertTrue(f.adapter().bind(h.getLevel(), f.pos(), pattern(h, List.of(item(Items.IRON_INGOT, 3)), out)) == null, "wrong output rejected");
            }
            h.assertTrue(f.adapter().planVirtualWithBinding(h.getLevel(), f.pos(), p, new KeyCounter[0], binding.handle(), SOURCE) == null, "empty input");
            h.assertTrue(f.adapter().planVirtualWithBinding(h.getLevel(), f.pos(), p, counters(List.of(item(Items.IRON_INGOT, 3))), new Object(), SOURCE) == null, "foreign handle");
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void splitCountersRejectLongOverflowAndAcceptIntegerBoundary(GameTestHelper h) {
        for (var family : Family.values()) try (var scope = new RecipeScope(h, family, simple(family))) {
            var f = table(h, family, 1);
            var p = pattern(h, List.of(item(Items.IRON_INGOT, 3)), List.of(item(Items.DIAMOND, 1)));
            var overflow = new KeyCounter[]{counters(List.of(item(Items.IRON_INGOT, Long.MAX_VALUE)))[0],
                    counters(List.of(item(Items.IRON_INGOT, Long.MAX_VALUE)))[0], counters(List.of(item(Items.IRON_INGOT, 5)))[0]};
            h.assertTrue(f.plan(p, overflow) == null, "long overflow cannot forge three input units: " + family);
            for (long count : new long[]{1, 65, 257, Integer.MAX_VALUE}) {
                var split = new KeyCounter[]{counters(List.of(item(Items.IRON_INGOT, count)))[0], counters(List.of(item(Items.IRON_INGOT, 2 * count)))[0]};
                var result = f.plan(pattern(h, List.of(item(Items.IRON_INGOT, 3 * count)), List.of(item(Items.DIAMOND, count))), split);
                h.assertTrue(result != null && result.outputs().getFirst().amount() == count, "exact batch output " + count);
            }
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void dirtyGridAndReplacedTableRejectWithoutMutatingPlayerItems(GameTestHelper h) {
        for (var family : Family.values()) try (var scope = new RecipeScope(h, family, simple(family))) {
            var f = table(h, family, 1);
            var in = List.of(item(Items.IRON_INGOT, 3));
            var p = pattern(h, in, List.of(item(Items.DIAMOND, 1)));
            var binding = f.adapter().bind(h.getLevel(), f.pos(), p);
            h.assertTrue(binding != null, "bind before player edits grid");
            for (int slot : new int[]{0, 4, 8}) {
                f.inventory().setStackInSlot(slot, new ItemStack(Items.DIRT));
                h.assertTrue(f.adapter().planVirtualWithBinding(h.getLevel(), f.pos(), p, counters(in), binding.handle(), SOURCE) == null, "any occupied grid slot blocks virtual craft");
                h.assertTrue(f.inventory().getStackInSlot(slot).is(Items.DIRT), "player item preserved");
                f.inventory().setStackInSlot(slot, ItemStack.EMPTY);
            }
            h.getLevel().setBlockAndUpdate(f.pos(), Blocks.STONE.defaultBlockState());
            h.assertTrue(f.adapter().planVirtualWithBinding(h.getLevel(), f.pos(), p, counters(in), binding.handle(), SOURCE) == null, "removed table");
            table(h, family, 2);
            h.assertTrue(f.adapter().planVirtualWithBinding(h.getLevel(), f.pos(), p, counters(in), binding.handle(), SOURCE) == null, "changed table tier invalidates old handle");
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void virtualBucketRemaindersScaleAndPhysicalOffsetCraftReturnsOnce(GameTestHelper h) {
        for (var family : Family.values()) {
            var recipe = family.shaped(ShapedRecipePattern.of(Map.of('M', Ingredient.of(Items.MILK_BUCKET)), "M"), new ItemStack(Items.DIAMOND), 1, false);
            try (var scope = new RecipeScope(h, family, recipe)) {
                var f = table(h, family, 2);
                var in = List.of(item(Items.MILK_BUCKET, 65));
                var result = f.plan(pattern(h, in, List.of(item(Items.DIAMOND, 65))), counters(in));
                h.assertTrue(result != null && amount(result.outputs(), Items.DIAMOND) == 65 && amount(result.outputs(), Items.BUCKET) == 65, "batch remainder scaling");
                f.inventory().setStackInSlot(18, new ItemStack(Items.MILK_BUCKET));
                var filter = new AllowedOutputFilter();
                filter.allowStrict(AEItemKey.of(Items.DIAMOND));
                var output = f.adapter().extractOutputs(h.getLevel(), f.pos(), filter, SOURCE);
                h.assertTrue(amount(output, Items.DIAMOND) == 1, "offset native grid crafted once");
                h.assertTrue(f.inventory().getStackInSlot(18).is(Items.BUCKET), "trim offsets place bucket in original cell");
                h.assertTrue(f.adapter().extractOutputs(h.getLevel(), f.pos(), filter, SOURCE).isEmpty(), "bucket cannot produce duplicate output");
            }
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void removedRecipeMustInvalidatePreviouslySuccessfulHandle(GameTestHelper h) {
        for (var family : Family.values()) try (var scope = new RecipeScope(h, family, simple(family))) {
            var f = table(h, family, 1);
            var in = List.of(item(Items.IRON_INGOT, 3));
            var p = pattern(h, in, List.of(item(Items.DIAMOND, 1)));
            var binding = f.adapter().bind(h.getLevel(), f.pos(), p);
            h.assertTrue(binding != null, "old recipe bound");
            try (var removed = new RecipeScope(h, family)) {
                h.assertTrue(f.adapter().planVirtualWithBinding(h.getLevel(), f.pos(), p, counters(in), binding.handle(), SOURCE) == null,
                        "removed recipe must not survive through cached object: " + family);
            }
            var changed = family.shaped(ShapedRecipePattern.of(Map.of('I', Ingredient.of(Items.IRON_INGOT)), "III"), new ItemStack(Items.EMERALD), 1, false);
            try (var replaced = new RecipeScope(h, family, changed)) {
                h.assertTrue(f.adapter().planVirtualWithBinding(h.getLevel(), f.pos(), p, counters(in), binding.handle(), SOURCE) == null,
                        "same ID with a new recipe object cannot reuse stale result");
                h.assertTrue(f.plan(pattern(h, in, List.of(item(Items.EMERALD, 1))), counters(in)) != null, "replacement recipe binds normally");
            }
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void nativeScriptTransformersReturnExactDamagedToolComponents(GameTestHelper h) {
        for (var family : Family.values()) for (boolean shapeless : new boolean[]{false, true}) {
            var tool = new ItemStack(Items.IRON_PICKAXE);
            tool.setDamageValue(5);
            tool.set(DataComponents.CUSTOM_NAME, Component.literal("script tool"));
            java.util.function.Function<ItemStack, ItemStack> transform = stack -> {
                if (!stack.is(Items.IRON_PICKAXE)) return ItemStack.EMPTY;
                var remainder = stack.copyWithCount(1);
                remainder.setDamageValue(remainder.getDamageValue() + 1);
                return remainder;
            };
            Recipe<?> recipe;
            if (shapeless) {
                recipe = family.shapeless(NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_PICKAXE), Ingredient.of(Items.IRON_INGOT)), new ItemStack(Items.DIAMOND), 1);
                if (recipe instanceof com.blakebr0.extendedcrafting.crafting.recipe.ShapelessTableRecipe ec) ec.setTransformer((slot, stack) -> transform.apply(stack));
                if (recipe instanceof committee.nova.mods.avaritia.common.crafting.recipe.ShapelessTableCraftingRecipe av) av.setTransformers((slot, stack) -> transform.apply(stack));
            } else {
                recipe = family.shaped(ShapedRecipePattern.of(Map.of('T', Ingredient.of(Items.IRON_PICKAXE), 'I', Ingredient.of(Items.IRON_INGOT)), "TI"), new ItemStack(Items.DIAMOND), 1, true);
                if (recipe instanceof com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe ec) ec.setTransformer((x, y, stack) -> transform.apply(stack));
                if (recipe instanceof committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe av) av.setTransformers((x, y, stack) -> transform.apply(stack));
            }
            try (var scope = new RecipeScope(h, family, recipe)) {
                var f = table(h, family, 1);
                var in = List.of(new GenericStack(AEItemKey.of(tool), 1), item(Items.IRON_INGOT, 1));
                var result = f.plan(pattern(h, in, List.of(item(Items.DIAMOND, 1))), counters(in));
                var expected = transform.apply(tool);
                h.assertTrue(result != null && result.outputs().contains(new GenericStack(AEItemKey.of(expected), 1)), "native transformer gets exact name and damage: " + family + " shapeless=" + shapeless);
                h.assertTrue(tool.getDamageValue() == 5, "planning never mutates original tool");
            }
        }
        h.succeed();
    }

    @GameTest(template = "workbench_test")
    public static void nativeRecipeExceptionsRejectAndHealthyCandidateStillRuns(GameTestHelper h) {
        for (var family : Family.values()) for (var phase : List.of("preview", "ingredients", "matches", "assemble", "remaining")) {
            for (boolean linkage : new boolean[]{false, true}) {
                var fault = new FaultRecipe(simple(family), phase, linkage);
                try (var scope = new RecipeScope(h, family, fault)) {
                    var f = table(h, family, 1);
                    var in = List.of(item(Items.IRON_INGOT, 3));
                    h.assertTrue(f.plan(pattern(h, in, List.of(item(Items.DIAMOND, 1))), counters(in)) == null, "native error rejected: " + phase);
                }
            }
            try (var scope = new RecipeScope(h, family, simple(family))) {
                var f = table(h, family, 1);
                var in = List.of(item(Items.IRON_INGOT, 3));
                h.assertTrue(f.plan(pattern(h, in, List.of(item(Items.DIAMOND, 1))), counters(in)) != null, "fault does not poison future healthy recipes");
            }
        }
        h.succeed();
    }

    private static long amount(List<GenericStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(s -> s.what().equals(AEItemKey.of(item))).mapToLong(GenericStack::amount).sum();
    }

    private static final class FaultRecipe implements Recipe<CraftingInput> {
        final Recipe<?> delegate;
        final String phase;
        final boolean linkage;
        FaultRecipe(Recipe<?> delegate, String phase, boolean linkage) { this.delegate = delegate; this.phase = phase; this.linkage = linkage; }
        void fault(String operation) {
            if (!phase.equals(operation)) return;
            if (linkage) throw new NoSuchMethodError("virtual table probe: " + phase);
            throw new IllegalStateException("virtual table probe: " + phase);
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        public boolean matches(CraftingInput input, Level level) { fault("matches"); return ((Recipe) delegate).matches(input, level); }
        @SuppressWarnings({"rawtypes", "unchecked"})
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { fault("assemble"); return ((Recipe) delegate).assemble(input, registries); }
        @SuppressWarnings({"rawtypes", "unchecked"})
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) { fault("remaining"); return ((Recipe) delegate).getRemainingItems(input); }
        public ItemStack getResultItem(HolderLookup.Provider registries) { fault("preview"); return delegate.getResultItem(registries); }
        public NonNullList<Ingredient> getIngredients() { fault("ingredients"); return delegate.getIngredients(); }
        public boolean canCraftInDimensions(int width, int height) { return delegate.canCraftInDimensions(width, height); }
        public RecipeSerializer<?> getSerializer() { return delegate.getSerializer(); }
        public RecipeType<?> getType() { return delegate.getType(); }
    }
}
