package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import static com.moakiee.ae2lt.packaged.logic.multiblock.fa.FAGameTests.*;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.resource.ForbiddenEssenceResources;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;

/** Opt-in tests: native ticks produce all outputs, and absence is a failure. */
@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class FAForgeEdgeGameTests {
    private static final IActionSource SOURCE = IActionSource.empty();

    private static void installed(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded("forbidden_arcanus"), "FA must actually be loaded; absence is not a pass");
    }

    @GameTestGenerator
    public static Collection<TestFunction> nativeRituals() {
        var tests = new ArrayList<TestFunction>();
        for (var name : List.of("boss_catcher", "eternal_stella", "ferrognetic_mixture", "quantum_catcher",
                "quantum_injector", "sea_prism", "smelter_prism", "soul_binding_crystal", "terrastomp_prism", "whirlwind_prism",
                "draco_arcanus_boots", "draco_arcanus_chestplate", "draco_arcanus_helmet", "draco_arcanus_leggings",
                "tyr_boots", "tyr_chestplate", "tyr_helmet", "tyr_leggings")) {
            tests.add(new TestFunction("forge_native", "ae2ltpp:forge_native_" + name,
                    "ae2ltpp:core_test", 900, 0, true, h -> nativeRitual(h, name)));
        }
        return tests;
    }

    private static BlockEntity fullForge(GameTestHelper h, int tier) {
        installed(h);
        var forge = forge(h);
        if (tier != 1) forge = block(h, forge.getBlockPos(), "forbidden_arcanus:hephaestus_forge_tier_" + tier);
        for (var offset : List.of(new BlockPos(0,0,-3), new BlockPos(-2,0,-2), new BlockPos(2,0,-2),
                new BlockPos(-2,0,2), new BlockPos(2,0,2))) {
            block(h, forge.getBlockPos().offset(offset), "forbidden_arcanus:darkstone_pedestal");
        }
        return forge;
    }

    private static List<GenericStack> ritualInputs(Object ritual) {
        var list = new ArrayList<GenericStack>();
        var main = ((Ingredient) FAAccess.call(ritual, "mainIngredient")).getItems()[0];
        list.add(new GenericStack(AEItemKey.of(main), 1));
        for (var input : (List<?>) FAAccess.call(ritual, "inputs")) {
            var ingredient = ((Ingredient) FAAccess.call(input, "ingredient")).getItems()[0];
            list.add(new GenericStack(AEItemKey.of(ingredient), (int) FAAccess.call(input, "amount")));
        }
        return list;
    }

    private static void nativeRitual(GameTestHelper h, String name) {
        installed(h);
        var ritual = FAAccess.registry(h.getLevel(), "hephaestus_forge/ritual").get(id("forbidden_arcanus:" + name));
        h.assertTrue(ritual != null, "real native ritual exists: " + name);
        var forge = fullForge(h, 5);
        var requirements = FAAccess.call(ritual, "requirements");
        int slot = 0;
        for (var enhancer : (HolderSet<?>) FAAccess.call(requirements, "enhancers")) {
            var display = (Holder<?>) FAAccess.call(enhancer.value(), "displayItem");
            FAAccess.setStack(forge, slot++, new ItemStack((Item) display.value()));
        }
        var list = ritualInputs(ritual);
        if (name.startsWith("tyr_") || name.startsWith("draco_")) {
            var main = ((AEItemKey) list.getFirst().what()).toStack();
            main.set(DataComponents.CUSTOM_NAME, Component.literal("Named armor: " + name));
            main.setDamageValue(7);
            main.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 3);
            list.set(0, new GenericStack(AEItemKey.of(main), 1));
        }
        var expected = FAAccess.ritualResult(ritual, ((AEItemKey) list.getFirst().what()).toStack());
        var pattern = pattern(h, list, List.of(new GenericStack(AEItemKey.of(expected), expected.getCount())));
        h.runAfterDelay(100, () -> {
            var costs = FAAccess.call(requirements, "essences");
            int[] before = new int[4], cost = new int[4];
            for (int i = 0; i < 4; i++) {
                cost[i] = (int) FAAccess.call(costs, "get", FAAccess.essence(FAForgeConversion.name(i)));
                before[i] = Math.min(FAAccess.capacity(forge, FAForgeConversion.name(i)), cost[i] * 2 + 23);
                FAAccess.amount(forge, FAForgeConversion.name(i), before[i]);
            }
            var adapter = new FAForgeAdapter();
            var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
            h.assertTrue(binding != null, name + " binds");
            var plan = adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE);
            h.assertTrue(plan != null, name + " plans with native tier and enhancers");
            dispatch(h, plan);
            h.assertTrue(!FAAccess.active(forge) && forge.getPersistentData().contains(FAForgeAdapter.JOB), name + " waits for input sync");
            h.assertTrue(!adapter.canDispatch(h.getLevel(), forge.getBlockPos(), binding.handle()), "active ritual cannot be overwritten");
            h.succeedWhen(() -> {
                var result = adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE);
                h.assertTrue(result.equals(List.of(new GenericStack(AEItemKey.of(expected), expected.getCount()))), name + " native result");
                for (int i = 0; i < 4; i++) h.assertTrue(FAAccess.amount(forge, FAForgeConversion.name(i)) == before[i] - cost[i], name + " exact cost " + i);
                h.assertTrue(adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE).isEmpty(), "no duplicate return");
                h.assertTrue(FAForgeAdapter.idle(forge), "native ingredients and owned job cleared");
            });
        });
    }

    private static void dispatch(GameTestHelper h, DispatchPlan plan) {
        h.assertTrue(DispatchExecutor.execute(plan, SOURCE, s -> h.fail("unexpected retained input " + s)).success(), "dispatch commits");
    }

    private static IPatternDetails conversionPattern(GameTestHelper h, ItemStack input, int count) {
        var description = FAForgeConversion.describe(h.getLevel(), AEItemKey.of(input), count);
        h.assertTrue(description != null, "supported native conversion: " + input);
        var output = new ArrayList<GenericStack>();
        output.add(new GenericStack(ForbiddenEssenceResources.ALL.get(description.index()), description.min()));
        if (!description.remainder().isEmpty()) output.add(new GenericStack(AEItemKey.of(description.remainder()), description.remainder().getCount()));
        return pattern(h, List.of(new GenericStack(AEItemKey.of(input), count)), output);
    }

    private static void convert(GameTestHelper h, ItemStack input, int count, boolean reload) {
        var forge = fullForge(h, 5);
        var adapter = new FAForgeAdapter();
        var pattern = conversionPattern(h, input, count);
        var description = FAForgeConversion.describe(h.getLevel(), AEItemKey.of(input), count);
        int baseline = 17;
        FAAccess.amount(forge, FAForgeConversion.name(description.index()), baseline);
        var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
        h.assertTrue(binding != null, "conversion binds");
        var plan = adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern,
                inputs(List.of(new GenericStack(AEItemKey.of(input), count))), binding.handle(), SOURCE);
        h.assertTrue(plan != null, "conversion plans");
        dispatch(h, plan);
        h.assertTrue(adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE).isEmpty(), "no premature output");
        if (reload) {
            var saved = forge.saveWithFullMetadata(h.getLevel().registryAccess());
            forge.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(forge.getPersistentData().contains(FAForgeAdapter.JOB), "in-flight conversion survives reload");
        }
        h.succeedWhen(() -> {
            var result = adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE);
            h.assertTrue(!result.isEmpty(), "native conversion completed");
            var actual = new KeyCounter(); result.forEach(s -> actual.add(s.what(), s.amount()));
            long essence = actual.get(ForbiddenEssenceResources.ALL.get(description.index()));
            h.assertTrue(essence >= description.min() && essence <= description.max(), "native random yield stays in declared bounds");
            var remainder = description.remainder();
            if (!remainder.isEmpty()) h.assertTrue(actual.get(AEItemKey.of(remainder)) == remainder.getCount(), "exact native container/components");
            h.assertTrue(actual.size() == (remainder.isEmpty() ? 1 : 2), "no extra outputs");
            h.assertTrue(FAAccess.amount(forge, FAForgeConversion.name(description.index())) == baseline, "preexisting essence preserved");
            h.assertTrue(FAForgeAdapter.idle(forge), "forge available for next job");
            h.assertTrue(adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE).isEmpty(), "collected once");
        });
    }

    private static ItemStack enchanted(GameTestHelper h, boolean curse, boolean onlyCurse) {
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        tool.set(DataComponents.CUSTOM_NAME, Component.literal("Component preservation probe"));
        tool.set(DataComponents.REPAIR_COST, 31);
        tool.setDamageValue(137);
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        if (!onlyCurse) tool.enchant(registry.getHolderOrThrow(Enchantments.EFFICIENCY), 5);
        if (curse) tool.enchant(registry.getHolderOrThrow(Enchantments.VANISHING_CURSE), 1);
        return tool;
    }

    @GameTest(template="core_test", timeoutTicks=200)
    public static void enchantedDamagedNamedTool(GameTestHelper h) { convert(h, enchanted(h, false, false), 1, false); }

    @GameTest(template="core_test", timeoutTicks=200)
    public static void mixedCurseRetainsCurseAndComponents(GameTestHelper h) { convert(h, enchanted(h, true, false), 1, true); }

    @GameTest(template="core_test", timeoutTicks=250)
    public static void fullStackOfBottles(GameTestHelper h) { var input = item("forbidden_arcanus:aureal_bottle"); convert(h, input, input.getMaxStackSize(), false); }

    @GameTest(template="core_test", timeoutTicks=250)
    public static void fullStackOfExperienceOrbs(GameTestHelper h) { var input = item("forbidden_arcanus:xpetrified_orb"); convert(h, input, input.getMaxStackSize(), true); }

    @GameTest(template="core_test", timeoutTicks=250)
    public static void fullStackOfSouls(GameTestHelper h) { convert(h, item("forbidden_arcanus:soul"), 64, false); }

    @GameTest(template="core_test", timeoutTicks=300)
    public static void partialBloodContainerReload(GameTestHelper h) {
        installed(h);
        var blood = (ItemStack) FAAccess.call(FAAccess.type(FAAccess.BASE + "common.essence.EssenceHelper"), "createStorageItem",
                item("forbidden_arcanus:blood_test_tube").getItem(), FAAccess.essence("blood"), 57, 1000);
        blood.set(DataComponents.CUSTOM_NAME, Component.literal("Partly filled tube"));
        convert(h, blood, 1, true);
    }

    @GameTest(template="core_test")
    public static void unsupportedAndMalformedConversions(GameTestHelper h) {
        installed(h);
        h.assertTrue(FAForgeConversion.describe(h.getLevel(), AEItemKey.of(enchanted(h, true, true)), 1) == null, "curse-only has no extractable XP");
        h.assertTrue(FAForgeConversion.describe(h.getLevel(), AEItemKey.of(enchanted(h, false, false)), 2) == null, "stacked tools rejected");
        var forge = fullForge(h, 1); var adapter = new FAForgeAdapter();
        var bottles = List.of(stack("forbidden_arcanus:aureal_bottle", 1));
        for (long wrong : new long[] {34, 36, 70}) {
            h.assertTrue(adapter.bind(h.getLevel(), forge.getBlockPos(), pattern(h, bottles,
                    List.of(new GenericStack(ForbiddenEssenceResources.AUREAL, wrong)))) == null, "incorrect yield rejected " + wrong);
        }
        h.assertTrue(adapter.bind(h.getLevel(), forge.getBlockPos(), pattern(h,
                List.of(stack("forbidden_arcanus:aureal_bottle", 65)), List.of(new GenericStack(ForbiddenEssenceResources.AUREAL, 2275)))) == null, "oversized stack rejected");
        var sameSlot = List.of(stack("forbidden_arcanus:soul", 1), stack("forbidden_arcanus:corrupt_soul", 1));
        var first = FAForgeConversion.describe(h.getLevel(), (AEItemKey) sameSlot.get(0).what(), 1);
        var second = FAForgeConversion.describe(h.getLevel(), (AEItemKey) sameSlot.get(1).what(), 1);
        h.assertTrue(adapter.bind(h.getLevel(), forge.getBlockPos(), pattern(h, sameSlot,
                List.of(new GenericStack(ForbiddenEssenceResources.SOULS, first.min() + second.min())))) == null, "two different items cannot share one native slot");
        h.succeed();
    }

    @GameTest(template="core_test", timeoutTicks=180)
    public static void exactCapacityAndOverflow(GameTestHelper h) {
        var forge = fullForge(h, 1); var adapter = new FAForgeAdapter();
        var list = List.of(stack("forbidden_arcanus:aureal_bottle", 2));
        var pattern = conversionPattern(h, item("forbidden_arcanus:aureal_bottle"), 2);
        var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
        int capacity = FAAccess.capacity(forge, "aureal");
        FAAccess.amount(forge, "aureal", capacity - 69);
        h.assertTrue(adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE) == null, "one point too full rejects without inserting");
        h.assertTrue(FAAccess.stack(forge, 5).isEmpty() && !forge.getPersistentData().contains(FAForgeAdapter.JOB), "overflow has no side effects");
        FAAccess.amount(forge, "aureal", capacity - 70);
        var plan = adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE);
        h.assertTrue(plan != null, "exact fit accepted"); dispatch(h, plan);
        h.succeedWhen(() -> {
            var out = adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE);
            h.assertTrue(out.equals(List.of(new GenericStack(ForbiddenEssenceResources.AUREAL, 70))), "exact capacity converts all inputs");
            h.assertTrue(FAAccess.amount(forge, "aureal") == capacity - 70, "capacity baseline preserved");
        });
    }

    @GameTest(template="core_test", timeoutTicks=180)
    public static void nativeTierAndEnhancerGates(GameTestHelper h) {
        var forge = fullForge(h, 1); var adapter = new FAForgeAdapter();
        h.runAfterDelay(100, () -> {
            for (var name : List.of("eternal_stella", "smelter_prism")) {
                var ritual = FAAccess.registry(h.getLevel(), "hephaestus_forge/ritual").get(id("forbidden_arcanus:" + name));
                var list = ritualInputs(ritual);
                var expected = FAAccess.ritualResult(ritual, ((AEItemKey) list.getFirst().what()).toStack());
                var pattern = pattern(h, list, List.of(new GenericStack(AEItemKey.of(expected), expected.getCount())));
                var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
                h.assertTrue(binding != null, "requirements do not hide recipe");
                for (int i = 0; i < 4; i++) FAAccess.amount(forge, FAForgeConversion.name(i), FAAccess.capacity(forge, FAForgeConversion.name(i)));
                h.assertTrue(adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE) == null, "missing native tier/enhancer rejects " + name);
                h.assertTrue(FAAccess.stack(forge, 4).isEmpty() && !FAAccess.active(forge), "gate rejects before inserting");
            }
            h.succeed();
        });
    }

    @GameTest(template="core_test", timeoutTicks=250)
    public static void overlappingMainIngredientKeepsFirstVariant(GameTestHelper h) { overlappingTools(h, false); }

    @GameTest(template="core_test", timeoutTicks=250)
    public static void overlappingMainIngredientKeepsSecondVariant(GameTestHelper h) { overlappingTools(h, true); }

    private static void overlappingTools(GameTestHelper h, boolean second) {
        var forge = fullForge(h, 1); var adapter = new FAForgeAdapter();
        var first = enchanted(h, false, false);
        var other = enchanted(h, true, false);
        other.set(DataComponents.CUSTOM_NAME, Component.literal("Other possible main ingredient"));
        other.setDamageValue(18);
        var main = second ? other : first;
        var expected = main.transmuteCopy(Items.NETHERITE_PICKAXE);
        var list = List.of(new GenericStack(AEItemKey.of(first), 1), new GenericStack(AEItemKey.of(other), 1));
        var pattern = pattern(h, list, List.of(new GenericStack(AEItemKey.of(expected), 1)));
        h.runAfterDelay(100, () -> {
            var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
            h.assertTrue(binding != null, "choose the main ingredient by the promised output components");
            var plan = adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE);
            h.assertTrue(plan != null, "main/pedestal overlap plans"); dispatch(h, plan);
            h.succeedWhen(() -> h.assertTrue(adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE)
                    .equals(List.of(new GenericStack(AEItemKey.of(expected), 1))), "native transmute preserves selected main components"));
        });
    }

    @GameTest(template="core_test", timeoutTicks=250)
    public static void sameItemOnMainAndEightPedestals(GameTestHelper h) {
        fixtureRitual(h, List.of(stack("minecraft:apple", 9)), List.of(stack("minecraft:golden_apple", 3)));
    }

    @GameTest(template="core_test", timeoutTicks=250)
    public static void zeroCostAndComponentOutput(GameTestHelper h) {
        var expected = new ItemStack(Items.DIAMOND, 3);
        expected.set(DataComponents.CUSTOM_NAME, Component.literal("Fixture component output"));
        fixtureRitual(h, List.of(stack("minecraft:stick", 1), stack("minecraft:stone", 1)), List.of(new GenericStack(AEItemKey.of(expected), 3)));
    }

    private static void fixtureRitual(GameTestHelper h, List<GenericStack> list, List<GenericStack> expected) {
        var forge = fullForge(h, 1); var adapter = new FAForgeAdapter();
        var pattern = pattern(h, list, expected);
        h.runAfterDelay(100, () -> {
            var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
            h.assertTrue(binding != null, "fixture ritual bound by real registry");
            var plan = adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE);
            h.assertTrue(plan != null, "fixture ritual planned"); dispatch(h, plan);
            var saved = forge.saveWithFullMetadata(h.getLevel().registryAccess());
            forge.loadWithComponents(saved, h.getLevel().registryAccess());
            h.succeedWhen(() -> {
                h.assertTrue(adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE).equals(expected), "native fixture output with correct count/components");
                h.assertTrue(FAForgeAdapter.idle(forge), "zero-cost/repeated-input fixture clears");
            });
        });
    }

    @GameTest(template="core_test", timeoutTicks=180)
    public static void changedInputsAndOccupiedSlotsAreRejected(GameTestHelper h) {
        var forge = fullForge(h, 1); var adapter = new FAForgeAdapter();
        var list = List.of(stack("forbidden_arcanus:aureal_bottle", 1));
        var pattern = conversionPattern(h, item("forbidden_arcanus:aureal_bottle"), 1);
        var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
        for (var changed : List.of(List.of(stack("forbidden_arcanus:aureal_bottle", 2)),
                List.of(stack("minecraft:stone", 1)), List.of(stack("forbidden_arcanus:soul", 1)))) {
            h.assertTrue(adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(changed), binding.handle(), SOURCE) == null, "changed actual input cannot reuse old binding");
        }
        for (int slot : new int[] {4,5,6,7,8}) {
            FAAccess.setStack(forge, slot, new ItemStack(Items.COBBLESTONE));
            h.assertTrue(!adapter.canDispatch(h.getLevel(), forge.getBlockPos(), binding.handle()), "occupied slot blocks dispatch " + slot);
            h.assertTrue(adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE).isEmpty(), "manual items never collected");
            FAAccess.setStack(forge, slot, ItemStack.EMPTY);
        }
        h.assertTrue(adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE) != null, "cleared slots allow dispatch");
        h.succeed();
    }

    @GameTest(template="core_test", timeoutTicks=180)
    public static void partialNetworkExtractionRollsBack(GameTestHelper h) { partialNetwork(h, false); }

    @GameTest(template="core_test", timeoutTicks=180)
    public static void rejectedNetworkRefundPreservesEssenceInForge(GameTestHelper h) { partialNetwork(h, true); }

    private static void partialNetwork(GameTestHelper h, boolean rejectRefund) {
        var forge = fullForge(h, 1); var provider = provider(h); var adapter = new FAForgeAdapter();
        var list = ritualItems();
        var pattern = pattern(h, list, List.of(new GenericStack(AEItemKey.of(com.moakiee.ae2lt.packaged.registry.PPItems.FA_FORGE_ADAPTER.get()), 1)));
        var stock = new KeyCounter(); ForbiddenEssenceResources.ALL.forEach(key -> stock.add(key, 100));
        var reserve = new MEStorage() {
            public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                long available = Math.min(amount, stock.get(key));
                if (mode == Actionable.MODULATE) { available = Math.min(available, 7); stock.add(key, -available); }
                return available;
            }
            public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                if (rejectRefund) return 0;
                if (mode == Actionable.MODULATE) stock.add(key, amount);
                return amount;
            }
            public void getAvailableStacks(KeyCounter out) { out.addAll(stock); }
            public Component getDescription() { return Component.literal("Racing forge reserve"); }
        };
        h.runAfterDelay(100, () -> {
            provider.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m -> m.mount(reserve, 0));
            var source = IActionSource.ofMachine(provider);
            var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
            h.assertTrue(binding != null, "racing reserve ritual binds");
            var plan = adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), source);
            h.assertTrue(plan != null, "simulation advertises enough essence");
            h.assertTrue(DispatchExecutor.execute(plan, source, s -> h.fail("full rollback must leave CPU inputs with caller")).failure(), "partial extraction prevents ritual start");
            h.assertTrue(!FAAccess.active(forge) && FAForgeAdapter.idle(forge), "no job, main ingredient, or pedestal input remains");
            for (int i = 0; i < 4; i++) {
                long retained = rejectRefund && i == 0 ? 7 : 0;
                h.assertTrue(FAAccess.amount(forge, FAForgeConversion.name(i)) == retained, "unreturnable network essence retained exactly " + i);
                h.assertTrue(stock.get(ForbiddenEssenceResources.ALL.get(i)) == 100 - retained, "network and forge total conserved " + i);
            }
            h.succeed();
        });
    }

    @GameTest(template="core_test", timeoutTicks=180)
    public static void missingBaseOrEighthPedestalRejectsBeforeInserting(GameTestHelper h) {
        var forge = fullForge(h, 1); var adapter = new FAForgeAdapter();
        var list = List.of(stack("minecraft:apple", 9));
        var pattern = pattern(h, list, List.of(stack("minecraft:golden_apple", 3)));
        h.runAfterDelay(100, () -> {
            var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
            h.assertTrue(binding != null, "eight-pedestal fixture exists");
            h.assertTrue(adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE) != null, "complete structure plans");
            var floor = forge.getBlockPos().below(); var original = h.getLevel().getBlockState(floor);
            h.getLevel().removeBlock(floor, false);
            h.assertTrue(adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE) == null, "broken base rejects");
            h.getLevel().setBlockAndUpdate(floor, original);
            h.getLevel().removeBlock(forge.getBlockPos().offset(2, 0, 2), false);
            h.assertTrue(adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE) == null, "seven of eight pedestals rejects");
            h.assertTrue(FAForgeAdapter.idle(forge), "rejections insert nothing");
            h.succeed();
        });
    }

    @GameTest(template="core_test", timeoutTicks=250)
    public static void manuallyReplacedFinishedOutputIsNeverCollected(GameTestHelper h) {
        var forge = fullForge(h, 1); var adapter = new FAForgeAdapter();
        var list = List.of(stack("minecraft:apple", 9));
        var pattern = pattern(h, list, List.of(stack("minecraft:golden_apple", 3)));
        h.runAfterDelay(100, () -> {
            var binding = adapter.bind(h.getLevel(), forge.getBlockPos(), pattern);
            dispatch(h, adapter.planWithBinding(h.getLevel(), forge.getBlockPos(), pattern, inputs(list), binding.handle(), SOURCE));
            h.succeedWhen(() -> {
                h.assertTrue(!FAAccess.active(forge) && FAAccess.stack(forge, 4).is(Items.GOLDEN_APPLE), "wait for real ritual finish");
                var manual = new ItemStack(Items.GOLDEN_APPLE, 3);
                manual.set(DataComponents.CUSTOM_NAME, Component.literal("Player-owned replacement"));
                FAAccess.setStack(forge, 4, manual);
                h.assertTrue(adapter.extractOutputs(h.getLevel(), forge.getBlockPos(), new AllowedOutputFilter(), SOURCE).isEmpty(), "same item/count with different components is not owned output");
                h.assertTrue(ItemStack.matches(FAAccess.stack(forge, 4), manual), "manual replacement preserved");
                h.assertTrue(!forge.getPersistentData().contains(FAForgeAdapter.JOB), "stale reservation released");
            });
        });
    }

    @GameTest(template="core_test")
    public static void nativeForgeUpgradesCannotPromiseInventoryOutputs(GameTestHelper h) {
        var forge = fullForge(h, 1); var adapter = new FAForgeAdapter();
        for (int tier = 2; tier <= 5; tier++) {
            var ritual = FAAccess.registry(h.getLevel(), "hephaestus_forge/ritual").get(id("forbidden_arcanus:upgrade_tier_" + tier));
            var pattern = pattern(h, ritualInputs(ritual), List.of(stack("forbidden_arcanus:hephaestus_forge_tier_" + tier, 1)));
            h.assertTrue(adapter.bind(h.getLevel(), forge.getBlockPos(), pattern) == null, "block upgrade has no inventory result " + tier);
        }
        h.assertTrue(FAForgeAdapter.idle(forge), "unsupported promises do not consume anything"); h.succeed();
    }
}
