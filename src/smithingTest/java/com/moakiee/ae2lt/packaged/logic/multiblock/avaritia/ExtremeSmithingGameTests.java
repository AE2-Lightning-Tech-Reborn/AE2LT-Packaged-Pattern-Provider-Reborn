package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.google.gson.Gson;
import committee.nova.mods.avaritia.common.crafting.input.ExtremeSmithingRecipeInput;
import committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingOutputBuffer;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import appeng.core.definitions.AEBlocks;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

/** Native recipe parity, admission boundaries and opt-in fault injection in a disposable world. */
@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ExtremeSmithingGameTests {
    private static final AvaritiaExtremeSmithingAdapter ADAPTER = new AvaritiaExtremeSmithingAdapter();
    private static final IActionSource SOURCE = IActionSource.empty();

    @GameTest(template = "smithing_test")
    public static void nativeApiColdFirstCallsAndInputCopies(GameTestHelper h) throws Exception {
        var recipe = recipe(h);
        // Reset only the test world's adapter cache, independently exercising every first accessor.
        for (String operation : List.of("template", "base", "addition", "templateId", "baseId", "additionId", "input")) {
            var done = AvaritiaExtremeSmithingAccess.class.getDeclaredField("lookupDone");
            done.setAccessible(true);
            done.setBoolean(null, false);
            for (var field : AvaritiaExtremeSmithingAccess.class.getDeclaredFields()) {
                if (field.getType() == java.lang.reflect.Method.class || field.getType() == java.lang.reflect.Field.class
                        || field.getType() == java.lang.reflect.Constructor.class) {
                    field.setAccessible(true);
                    field.set(null, null);
                }
            }
            boolean ok = switch (operation) {
                case "template" -> AvaritiaExtremeSmithingAccess.matchesTemplate(recipe, new ItemStack(Items.PAPER));
                case "base" -> AvaritiaExtremeSmithingAccess.matchesBase(recipe, new ItemStack(Items.IRON_SWORD));
                case "addition" -> AvaritiaExtremeSmithingAccess.matchesAddition(recipe, new ItemStack(Items.IRON_INGOT));
                case "templateId" -> AvaritiaExtremeSmithingAccess.templateAcceptsItemId(recipe, Items.PAPER);
                case "baseId" -> AvaritiaExtremeSmithingAccess.baseAcceptsItemId(recipe, Items.IRON_SWORD);
                case "additionId" -> AvaritiaExtremeSmithingAccess.additionAcceptsItemId(recipe, Items.IRON_INGOT);
                default -> AvaritiaExtremeSmithingAccess.createInput(new ItemStack(Items.PAPER),
                        new ItemStack(Items.IRON_SWORD), List.of(new ItemStack(Items.IRON_INGOT),
                                new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.COPPER_INGOT))) != null;
            };
            probe(h, "cold-" + operation, Map.of("accepted", ok));
            h.assertTrue(ok, "first optional API call succeeds: " + operation);
        }
        var base = named(Items.IRON_SWORD, "original");
        var template = new ItemStack(Items.PAPER);
        var addition = new ItemStack(Items.IRON_INGOT);
        var input = AvaritiaExtremeSmithingAccess.createInput(template, base, List.of(addition, addition, addition));
        h.assertTrue(input != null, "native input created");
        input.getItem(0).shrink(1);
        input.getItem(1).set(DataComponents.CUSTOM_NAME, Component.literal("mutated"));
        input.getItem(2).shrink(1);
        h.assertTrue(template.getCount() == 1 && addition.getCount() == 1
                && base.getHoverName().getString().equals("original") && input.getItem(3).getCount() == 1,
                "all five slots are independent copies");
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void repeatedMixedAndShuffledAdditionsMatchNativeAssembly(GameTestHelper h) {
        var pos = table(h);
        for (var additions : List.of(List.of(item(Items.IRON_INGOT, 3)),
                List.of(item(Items.COPPER_INGOT, 1), item(Items.IRON_INGOT, 1), item(Items.GOLD_INGOT, 1)))) {
            var inputs = new ArrayList<>(additions);
            inputs.add(item(Items.IRON_SWORD, 1));
            inputs.add(item(Items.PAPER, 1));
            var pattern = pattern(h, inputs, List.of(item(Items.DIAMOND_SWORD, 1)));
            var actual = plan(h, pos, pattern, counters(inputs));
            h.assertTrue(actual != null && actual.outputs().equals(List.of(item(Items.DIAMOND_SWORD, 1))), "native addition alternatives");
        }
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void componentsDamageAndRecipeOverridesEqualNativeResult(GameTestHelper h) {
        var pos = table(h);
        var base = named(Items.IRON_SWORD, "inherited");
        base.setDamageValue(17);
        var inputs = inputs(AEItemKey.of(base), 1);
        var actual = plan(h, pos, pattern(h, inputs, List.of(item(Items.DIAMOND_SWORD, 1))), counters(inputs));
        var nativeInput = new ExtremeSmithingRecipeInput(new ItemStack(Items.PAPER), base,
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT));
        var expected = recipe(h).assemble(nativeInput, h.getLevel().registryAccess());
        h.assertTrue(actual != null && actual.outputs().getFirst().what().equals(AEItemKey.of(expected)),
                "plain output pattern preserves actual native name and damage");
        h.assertTrue(base.getDamageValue() == 17 && base.getHoverName().getString().equals("inherited"), "planning leaves input unchanged");
        var result = named(Items.DIAMOND_SWORD, "recipe override");
        try (var ignored = new RecipeScope(h, new ExtremeSmithingRecipe(Ingredient.of(Items.PAPER),
                Ingredient.of(Items.IRON_SWORD), Ingredient.of(Items.IRON_INGOT), result))) {
            actual = plan(h, pos, pattern(h, inputs, List.of(item(Items.DIAMOND_SWORD, 1))), counters(inputs));
            h.assertTrue(actual != null && actual.outputs().getFirst().what().equals(AEItemKey.of(
                    ignored.recipe.assemble(nativeInput, h.getLevel().registryAccess()))),
                    "recipe component patches override inherited values exactly as native");
        }
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void missingExcessWrongInputsAndOutputShapesAreRejected(GameTestHelper h) {
        var pos = table(h);
        for (var invalid : List.of(List.of(item(Items.PAPER, 1), item(Items.IRON_SWORD, 1), item(Items.IRON_INGOT, 2)),
                List.of(item(Items.PAPER, 1), item(Items.IRON_SWORD, 1), item(Items.IRON_INGOT, 4)),
                List.of(item(Items.PAPER, 1), item(Items.STONE_SWORD, 1), item(Items.IRON_INGOT, 3)),
                List.of(item(Items.PAPER, 1), item(Items.IRON_SWORD, 1), item(Items.DIRT, 3)))) {
            h.assertTrue(ADAPTER.bind(h.getLevel(), pos, pattern(h, invalid, List.of(item(Items.DIAMOND_SWORD, 1)))) == null,
                    "invalid recipe must not bind: " + invalid);
        }
        for (var outputs : List.of(List.of(item(Items.DIAMOND_SWORD, 2)), List.of(item(Items.EMERALD, 1)),
                List.of(item(Items.DIAMOND_SWORD, 1), item(Items.DIRT, 1)),
                List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000)))) {
            h.assertTrue(ADAPTER.bind(h.getLevel(), pos, pattern(h, ordinary(1), outputs)) == null, "wrong output shape refused");
        }
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void realCountersAreRevalidatedAndNeverMutated(GameTestHelper h) {
        var pos = table(h);
        var pattern = pattern(h, ordinary(2), List.of(item(Items.DIAMOND_SWORD, 2)));
        var binding = ADAPTER.bind(h.getLevel(), pos, pattern);
        h.assertTrue(binding != null, "scaled recipe binds");
        for (var invalid : List.of(ordinary(1), ordinary(3),
                List.of(item(Items.PAPER, 2), item(Items.IRON_SWORD, 2), item(Items.IRON_INGOT, 5)),
                List.of(item(Items.PAPER, 2), item(Items.IRON_SWORD, 2), item(Items.IRON_INGOT, 6), item(Items.DIRT, 1)),
                List.of(item(Items.PAPER, 2), item(Items.IRON_SWORD, 2), new GenericStack(AEFluidKey.of(Fluids.WATER), 6)))) {
            var counters = counters(invalid);
            h.assertTrue(ADAPTER.planVirtualWithBinding(h.getLevel(), pos, pattern, counters, binding.handle(), SOURCE) == null,
                    "actual input quantities/type refused: " + invalid);
            invalid.forEach(stack -> h.assertTrue(counters[0].get(stack.what()) == stack.amount(), "rejected counters unchanged"));
        }
        h.assertTrue(ADAPTER.planVirtualWithBinding(h.getLevel(), pos, pattern, new KeyCounter[0], binding.handle(), SOURCE) == null,
                "empty counters refused");
        h.assertTrue(ADAPTER.planVirtualWithBinding(h.getLevel(), pos, pattern, counters(ordinary(2)), new Object(), SOURCE) == null,
                "foreign binding refused");
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void splitCountersAndIntegerBatchBoundaries(GameTestHelper h) {
        var pos = table(h);
        for (long count : new long[]{1, 2, 64, 65, 257, Integer.MAX_VALUE}) {
            var inputs = ordinary(count);
            var pattern = pattern(h, inputs, List.of(item(Items.DIAMOND_SWORD, count)));
            var split = new KeyCounter[]{counters(List.of(inputs.get(0)))[0], counters(List.of(inputs.get(1)))[0],
                    counters(List.of(item(Items.IRON_INGOT, count)))[0], counters(List.of(item(Items.IRON_INGOT, 2 * count)))[0]};
            var result = plan(h, pos, pattern, split);
            h.assertTrue(result != null && result.outputs().equals(List.of(item(Items.DIAMOND_SWORD, count))), "exact scaled count " + count);
            h.assertTrue(split[3].get(AEItemKey.of(Items.IRON_INGOT)) == 2 * count, "successful planning leaves counters unchanged");
        }
        long above = (long) Integer.MAX_VALUE + 1;
        h.assertTrue(ADAPTER.bind(h.getLevel(), pos, pattern(h, ordinary(above), List.of(item(Items.DIAMOND_SWORD, above)))) == null,
                "single-pattern integer limit fails promptly without expanding billions of inputs");
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void mergedCounterOverflowMustNotWrapIntoValidRecipe(GameTestHelper h) {
        var pos = table(h);
        var pattern = pattern(h, ordinary(1), List.of(item(Items.DIAMOND_SWORD, 1)));
        var binding = ADAPTER.bind(h.getLevel(), pos, pattern);
        h.assertTrue(binding != null, "ordinary recipe binds");
        var counters = new KeyCounter[]{counters(List.of(item(Items.PAPER, 1), item(Items.IRON_SWORD, 1), item(Items.IRON_INGOT, Long.MAX_VALUE)))[0],
                counters(List.of(item(Items.IRON_INGOT, Long.MAX_VALUE)))[0], counters(List.of(item(Items.IRON_INGOT, 5)))[0]};
        var result = ADAPTER.planVirtualWithBinding(h.getLevel(), pos, pattern, counters, binding.handle(), SOURCE);
        probe(h, "counter-overflow", Map.of("accepted", result != null));
        h.assertTrue(result == null, "MAX + MAX + 5 must not wrap into three payable additions");
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void encodedInputSumAndMultiplierOverflowAreRejected(GameTestHelper h) {
        var pos = table(h);
        var ordinary = pattern(h, ordinary(1), List.of(item(Items.DIAMOND_SWORD, 1)));
        for (boolean multiply : new boolean[]{false, true}) {
            var stacks = multiply ? List.of(item(Items.PAPER, 1), item(Items.IRON_SWORD, 1), item(Items.IRON_INGOT, Long.MAX_VALUE))
                    : List.of(item(Items.PAPER, 1), item(Items.IRON_SWORD, 1), item(Items.IRON_INGOT, Long.MAX_VALUE),
                            item(Items.IRON_INGOT, Long.MAX_VALUE), item(Items.IRON_INGOT, 5));
            var slots = new ArrayList<IPatternDetails.IInput>();
            for (var stack : stacks) {
                var original = ordinary.getInputs()[0];
                long multiplier = multiply && stack.what().equals(AEItemKey.of(Items.IRON_INGOT)) ? 3 : 1;
                slots.add((IPatternDetails.IInput) java.lang.reflect.Proxy.newProxyInstance(
                        IPatternDetails.class.getClassLoader(), new Class<?>[]{IPatternDetails.IInput.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "getPossibleInputs" -> new GenericStack[]{stack};
                            case "getMultiplier" -> multiplier;
                            default -> method.invoke(original, args);
                        }));
            }
            var malformed = (IPatternDetails) java.lang.reflect.Proxy.newProxyInstance(
                    IPatternDetails.class.getClassLoader(), new Class<?>[]{IPatternDetails.class},
                    (proxy, method, args) -> method.getName().equals("getInputs")
                            ? slots.toArray(IPatternDetails.IInput[]::new) : method.invoke(ordinary, args));
            h.assertTrue(ADAPTER.bind(h.getLevel(), pos, malformed) == null, "encoded input arithmetic overflow refused; multiply=" + multiply);
        }
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void componentSensitiveRecipeRechecksActualBase(GameTestHelper h) {
        var pos = table(h);
        var required = named(Items.IRON_SWORD, "required");
        var recipe = new ExtremeSmithingRecipe(Ingredient.of(Items.PAPER),
                DataComponentIngredient.of(false, DataComponents.CUSTOM_NAME, Component.literal("required"), Items.IRON_SWORD),
                Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SWORD));
        try (var scope = new RecipeScope(h, recipe)) {
            var inputs = inputs(AEItemKey.of(required), 1);
            var pattern = pattern(h, inputs, List.of(item(Items.DIAMOND_SWORD, 1)));
            var binding = ADAPTER.bind(h.getLevel(), pos, pattern);
            h.assertTrue(binding != null, "component-sensitive base binds");
            h.assertTrue(plan(h, pos, pattern, counters(inputs)) != null, "valid components are accepted");
            h.assertTrue(ADAPTER.bind(h.getLevel(), pos, pattern(h, ordinary(1), List.of(item(Items.DIAMOND_SWORD, 1)))) == null,
                    "same item ID without required components cannot bind");
            h.assertTrue(ADAPTER.planVirtualWithBinding(h.getLevel(), pos, pattern, counters(ordinary(1)), binding.handle(), SOURCE) == null,
                    "actual base cannot bypass native component constraints");
        }
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void originalInfinityClockRecipeMatchesNativeAssembly(GameTestHelper h) {
        var pos = table(h);
        var clock = BuiltInRegistries.ITEM.get(id("avaritia:infinity_clock"));
        h.assertTrue(clock != Items.AIR, "native clock item exists");
        var holder = h.getLevel().getRecipeManager().getRecipes().stream()
                .filter(value -> value.value() instanceof ExtremeSmithingRecipe smith && smith.result.is(clock)).findFirst().orElseThrow();
        var recipe = (ExtremeSmithingRecipe) holder.value();
        var template = recipe.template.getItems()[0];
        var base = recipe.base.getItems()[0];
        var additions = recipe.additions.getItems();
        h.assertTrue(additions.length >= 3, "native recipe exposes three addition alternatives");
        var nativeInput = new ExtremeSmithingRecipeInput(template, base, additions[0], additions[1], additions[2]);
        var expected = recipe.assemble(nativeInput, h.getLevel().registryAccess());
        var inputs = List.of(new GenericStack(AEItemKey.of(template), 1), new GenericStack(AEItemKey.of(base), 1),
                new GenericStack(AEItemKey.of(additions[0]), 1), new GenericStack(AEItemKey.of(additions[1]), 1),
                new GenericStack(AEItemKey.of(additions[2]), 1));
        var output = new GenericStack(AEItemKey.of(expected), expected.getCount());
        var result = plan(h, pos, pattern(h, inputs, List.of(output)), counters(inputs));
        h.assertTrue(result != null && result.outputs().equals(List.of(output)), "unmodified bundled recipe matches native assembly");
        probe(h, "native-recipe", Map.of("recipe", holder.id().toString(), "result", output.toString()));
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void multiOutputCountAndNonDivisibleRatios(GameTestHelper h) {
        var pos = table(h);
        try (var ignored = new RecipeScope(h, new ExtremeSmithingRecipe(Ingredient.of(Items.PAPER), Ingredient.of(Items.IRON_SWORD),
                Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SWORD, 2)))) {
            var pattern = pattern(h, ordinary(3), List.of(item(Items.DIAMOND_SWORD, 6)));
            var result = plan(h, pos, pattern, counters(ordinary(3)));
            h.assertTrue(result != null && result.outputs().getFirst().amount() == 6, "native result count participates in ratio");
            h.assertTrue(ADAPTER.bind(h.getLevel(), pos, pattern(h, ordinary(3), List.of(item(Items.DIAMOND_SWORD, 5)))) == null,
                    "nondivisible output ratio refused");
        }
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void removedOrReplacedRecipeCannotCraftThroughOldSmithingHandle(GameTestHelper h) {
        var pos = table(h);
        var pattern = pattern(h, ordinary(1), List.of(item(Items.DIAMOND_SWORD, 1)));
        var binding = ADAPTER.bind(h.getLevel(), pos, pattern);
        h.assertTrue(binding != null, "original smithing recipe binds");
        try (var replacement = new RecipeScope(h, new ExtremeSmithingRecipe(Ingredient.of(Items.PAPER),
                Ingredient.of(Items.STONE_SWORD), Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SWORD)))) {
            h.assertTrue(ADAPTER.planVirtualWithBinding(h.getLevel(), pos, pattern, counters(ordinary(1)), binding.handle(), SOURCE) == null,
                    "removed smithing recipe object is no longer dispatchable");
        }
        h.assertTrue(plan(h, pos, pattern, counters(ordinary(1))) != null, "restored native recipe binds again");
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void nativeExceptionsFailClosedAndNextRecipeStillWorks(GameTestHelper h) {
        var pos = table(h);
        for (String phase : List.of("preview", "template", "base", "addition", "matches", "assemble")) {
            for (boolean linkage : new boolean[]{false, true}) {
                try (var scope = new RecipeScope(h, new FaultRecipe(phase, linkage))) {
                    var pattern = pattern(h, ordinary(1), List.of(item(Items.DIAMOND_SWORD, 1)));
                    probe(h, "inject", Map.of("operation", phase, "linkage", linkage));
                    h.assertTrue(ADAPTER.bind(h.getLevel(), pos, pattern) == null, "native fault is rejected: " + phase);
                }
                h.assertTrue(plan(h, pos, pattern(h, ordinary(1), List.of(item(Items.DIAMOND_SWORD, 1))), counters(ordinary(1))) != null,
                        "recipe failure must not poison later healthy planning");
            }
        }
        h.succeed();
    }

    @GameTest(template = "smithing_test")
    public static void dispatchTimeNativeFaultsAndRemovedTableRefuseOutput(GameTestHelper h) {
        var pos = table(h);
        for (String phase : List.of("template", "base", "addition", "matches", "assemble")) {
            for (boolean linkage : new boolean[]{false, true}) {
                var faulty = new FaultRecipe("disabled", linkage);
                try (var scope = new RecipeScope(h, faulty)) {
                    var pattern = pattern(h, ordinary(1), List.of(item(Items.DIAMOND_SWORD, 1)));
                    var binding = ADAPTER.bind(h.getLevel(), pos, pattern);
                    h.assertTrue(binding != null, "bind healthy recipe before injecting fault");
                    faulty.phase = phase;
                    h.assertTrue(ADAPTER.planVirtualWithBinding(h.getLevel(), pos, pattern, counters(ordinary(1)), binding.handle(), SOURCE) == null,
                            "dispatch revalidates native calls: " + phase);
                }
            }
        }
        var pattern = pattern(h, ordinary(1), List.of(item(Items.DIAMOND_SWORD, 1)));
        var binding = ADAPTER.bind(h.getLevel(), pos, pattern);
        h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        h.assertTrue(ADAPTER.planVirtualWithBinding(h.getLevel(), pos, pattern, counters(ordinary(1)), binding.handle(), SOURCE) == null,
                "stale handle cannot craft after table is removed");
        h.succeed();
    }

    @GameTest(template = "smithing_test", timeoutTicks = 200)
    public static void wiredProviderRepeatedDispatchAndPendingNbt(GameTestHelper h) { provider(h, false); }

    @GameTest(template = "smithing_test", timeoutTicks = 200)
    public static void wirelessProviderRepeatedDispatchAndPendingNbt(GameTestHelper h) { provider(h, true); }

    private static void provider(GameTestHelper h, boolean wireless) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        net.minecraft.world.level.block.Block providerBlock = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get()
                : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        h.getLevel().setBlockAndUpdate(pos, providerBlock.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var target = pos.east(wireless ? 3 : 1);
        h.getLevel().setBlockAndUpdate(target, BuiltInRegistries.BLOCK.get(id("avaritia:extreme_smithing_table")).defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(pos);
        provider.getAdapterInv().setItemDirect(0, PPItems.AVARITIA_EXTREME_SMITHING_ADAPTER.toStack());
        if (wireless) h.assertTrue(provider.addOrUpdateConnection(h.getLevel().dimension(), target, Direction.WEST), "wireless binding");
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        var base = AEItemKey.of(named(Items.IRON_SWORD, "provider input"));
        var inputs = inputs(base, 1);
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(item(Items.DIAMOND_SWORD, 1))));
        h.runAfterDelay(50, () -> {
            logic.updatePatterns();
            h.assertTrue(logic.getAvailablePatterns().size() == 1, "provider exposes the bound recipe");
            var pattern = logic.getAvailablePatterns().getFirst();
            h.assertTrue(logic.pushBatch(pattern, counters(inputs), 257) == 0, "257-copy provider batch fully accepted");
            for (int i = 0; i < 65; i++) h.assertTrue(logic.pushPattern(pattern, counters(inputs)), "same-tick dispatch " + i);
            var key = AEItemKey.of(named(Items.DIAMOND_SWORD, "provider input"));
            h.assertTrue(pending(logic).snapshot().stream().filter(s -> s.what().equals(key)).mapToLong(GenericStack::amount).sum() == 322,
                    "provider retains 257 + 65 actual component-bearing results");
            var saved = new CompoundTag();
            logic.writeToNBT(saved, h.getLevel().registryAccess());
            logic.clearContent();
            logic.readFromNBT(saved, h.getLevel().registryAccess());
            h.assertTrue(pending(logic).snapshot().stream().filter(s -> s.what().equals(key)).mapToLong(GenericStack::amount).sum() == 322,
                    "pending component-bearing output survives reload exactly once");
            // NBT decoding creates new pattern objects; dispatch must use the current inventory's pattern.
            logic.updatePatterns();
            pattern = logic.getAvailablePatterns().getFirst();
            try (var scope = new RecipeScope(h, new ExtremeSmithingRecipe(Ingredient.of(Items.PAPER), Ingredient.of(Items.STONE_SWORD),
                    Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SWORD)))) {
                logic.onRecipesReloaded();
                h.assertTrue(!logic.pushPattern(pattern, counters(inputs)), "reload invalidates the old successful binding");
            }
            logic.onRecipesReloaded();
            h.assertTrue(logic.pushPattern(pattern, counters(inputs)), "healthy recipe rebinds after restoration");
            h.assertTrue(pending(logic).snapshot().stream().filter(s -> s.what().equals(key)).mapToLong(GenericStack::amount).sum() == 323,
                    "rejected reloaded recipe created no output");
            probe(h, "provider-complete", Map.of("wireless", wireless, "outputs", 323, "reload", "rebound"));
            h.succeed();
        });
    }

    private static PendingOutputBuffer pending(PackagedPatternProviderLogic logic) {
        try {
            var field = PackagedPatternProviderLogic.class.getDeclaredField("pendingOutputs");
            field.setAccessible(true);
            return (PendingOutputBuffer) field.get(logic);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static VirtualCraftingResult plan(GameTestHelper h, BlockPos pos, IPatternDetails pattern, KeyCounter[] counters) {
        var binding = ADAPTER.bind(h.getLevel(), pos, pattern);
        probe(h, "bind", Map.of("accepted", binding != null, "inputs", pattern.getInputs().length,
                "outputs", pattern.getOutputs().toString(), "counters", describe(counters)));
        if (binding == null) return null;
        var result = ADAPTER.planVirtualWithBinding(h.getLevel(), pos, pattern, counters, binding.handle(), SOURCE);
        probe(h, "plan", Map.of("accepted", result != null, "result", result == null ? List.of()
                : result.outputs().stream().map(stack -> describe(stack)).toList()));
        return result;
    }

    private static BlockPos table(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded("avaritia"), "Re-Avaritia is required; absence is not a passing native test");
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        var block = BuiltInRegistries.BLOCK.get(id("avaritia:extreme_smithing_table"));
        h.assertTrue(block != Blocks.AIR, "native table registered");
        h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        return pos;
    }

    private static ExtremeSmithingRecipe recipe(GameTestHelper h) {
        return (ExtremeSmithingRecipe) h.getLevel().getRecipeManager().byKey(id("ae2ltpp:smithing_test")).orElseThrow().value();
    }

    private static List<GenericStack> ordinary(long count) { return inputs(AEItemKey.of(Items.IRON_SWORD), count); }
    private static List<GenericStack> inputs(AEItemKey base, long count) {
        return List.of(item(Items.PAPER, count), new GenericStack(base, count), item(Items.IRON_INGOT, 3 * count));
    }
    private static GenericStack item(Item item, long count) { return new GenericStack(AEItemKey.of(item), count); }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static ItemStack named(Item item, String name) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }
    private static IPatternDetails pattern(GameTestHelper h, List<GenericStack> inputs, List<GenericStack> outputs) {
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs, outputs), h.getLevel());
        h.assertTrue(pattern != null, "real processing pattern decoded");
        return pattern;
    }
    private static KeyCounter[] counters(List<GenericStack> stacks) {
        var counter = new KeyCounter();
        stacks.forEach(stack -> counter.add(stack.what(), stack.amount()));
        return new KeyCounter[]{counter};
    }

    private static List<Object> describe(KeyCounter[] counters) {
        var slots = new ArrayList<Object>();
        for (var counter : counters) {
            var values = new ArrayList<Object>();
            for (var entry : counter) values.add(describe(new GenericStack(entry.getKey(), entry.getLongValue())));
            slots.add(values);
        }
        return slots;
    }

    private static Map<String, Object> describe(GenericStack stack) {
        return Map.of("key", stack.what().toString(), "amount", stack.amount(), "components",
                stack.what() instanceof AEItemKey item ? item.toStack(1).getComponentsPatch().toString() : "");
    }

    private static void probe(GameTestHelper h, String stage, Map<String, ?> details) {
        var event = new LinkedHashMap<String, Object>();
        event.put("scenario", StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> frame.getClassName().equals(ExtremeSmithingGameTests.class.getName()))
                .filter(frame -> !List.of("probe", "plan").contains(frame.getMethodName()))
                .map(StackWalker.StackFrame::getMethodName).findFirst().orElse("unknown")));
        event.put("origin", h.absolutePos(BlockPos.ZERO).toShortString());
        event.put("stage", stage);
        event.put("tick", h.getLevel().getGameTime());
        event.put("thread", Thread.currentThread().getName());
        event.put("avaritia", ModList.get().getModContainerById("avaritia").orElseThrow().getModInfo().getVersion().toString());
        event.putAll(details);
        try {
            Files.writeString(FMLPaths.GAMEDIR.get().resolve("smithing-probe.jsonl"), new Gson().toJson(event) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException failure) { throw new AssertionError("Cannot write smithing probe evidence", failure); }
    }

    /** Synchronous only: restore the global manager before any other GameTest can run a tick. */
    private static final class RecipeScope implements AutoCloseable {
        final GameTestHelper helper;
        final List<RecipeHolder<?>> previous;
        final ExtremeSmithingRecipe recipe;
        RecipeScope(GameTestHelper helper, ExtremeSmithingRecipe recipe) {
            this.helper = helper;
            this.recipe = recipe;
            previous = List.copyOf(helper.getLevel().getRecipeManager().getRecipes());
            var replacements = new ArrayList<>(previous);
            replacements.removeIf(holder -> holder.value() instanceof ExtremeSmithingRecipe);
            replacements.add(new RecipeHolder<>(id("ae2ltpp:smithing_fault"), recipe));
            helper.getLevel().getRecipeManager().replaceRecipes(replacements);
        }
        public void close() { helper.getLevel().getRecipeManager().replaceRecipes(previous); }
    }

    private static final class FaultRecipe extends ExtremeSmithingRecipe {
        String phase;
        final boolean linkage;
        FaultRecipe(String phase, boolean linkage) {
            super(Ingredient.of(Items.PAPER), Ingredient.of(Items.IRON_SWORD), Ingredient.of(Items.IRON_INGOT), new ItemStack(Items.DIAMOND_SWORD));
            this.phase = phase;
            this.linkage = linkage;
        }
        void fault(String operation) {
            if (!phase.equals(operation)) return;
            if (linkage) throw new NoSuchMethodError("smithing probe: " + operation);
            throw new IllegalStateException("smithing probe: " + operation);
        }
        @Override public boolean isTemplateIngredient(ItemStack stack) { fault("template"); return super.isTemplateIngredient(stack); }
        @Override public boolean isBaseIngredient(ItemStack stack) { fault("base"); return super.isBaseIngredient(stack); }
        @Override public boolean isAdditionIngredient(ItemStack stack) { fault("addition"); return super.isAdditionIngredient(stack); }
        @Override public boolean matches(ExtremeSmithingRecipeInput input, Level level) { fault("matches"); return super.matches(input, level); }
        @Override public ItemStack assemble(ExtremeSmithingRecipeInput input, HolderLookup.Provider registries) {
            fault("assemble"); return super.assemble(input, registries);
        }
        @Override public ItemStack getResultItem(HolderLookup.Provider registries) { fault("preview"); return super.getResultItem(registries); }
    }
}
