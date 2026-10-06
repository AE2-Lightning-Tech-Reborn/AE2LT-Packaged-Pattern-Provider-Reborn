package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.List;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class MekRoutingGameTests {
    @GameTest(template = "mek_routing_test", timeoutTicks = 100)
    public static void sodiumRecipeRatios(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var rotaryPos = helper.absolutePos(new BlockPos(2, 2, 2));
        var separatorPos = helper.absolutePos(new BlockPos(8, 2, 2));
        place(helper, rotaryPos, "large_rotary_condensentrator");
        place(helper, separatorPos, "large_electrolytic_separator");
        var rotary = level.getBlockEntity(rotaryPos);
        var mode = rotary.getClass().getDeclaredField("mode");
        mode.setAccessible(true);
        mode.setBoolean(rotary, true);
        var brine = AEFluidKey.of(BuiltInRegistries.FLUID.get(ResourceLocation.parse("mekanism:brine")));
        var adapter = new MekanismMoreMachinesAdapter();
        // CDP2 adds 1 brine -> 1 sodium; native electrolysis uses 10 brine -> 1 sodium + 1 chlorine.
        for (long copies : new long[] {1, 10, 64}) {
            var sodium = chemical(MekanismChemicals.SODIUM.asStack(copies));
            var chlorine = chemical(MekanismChemicals.CHLORINE.asStack(copies));
            var rotaryInput = new GenericStack(brine, copies);
            var separatorInput = new GenericStack(brine, 10 * copies);
            var evaporating = pattern(helper, new GenericStack[] {rotaryInput}, new GenericStack[] {sodium});
            var separating = pattern(helper, new GenericStack[] {separatorInput}, new GenericStack[] {sodium, chlorine});
            var sodiumOnly = pattern(helper, new GenericStack[] {separatorInput}, new GenericStack[] {sodium});
            var rotaryBinding = adapter.bind(level, rotaryPos, evaporating);
            var separatorBinding = adapter.bind(level, separatorPos, separating);
            helper.assertTrue(rotaryBinding != null && separatorBinding != null, "both sodium recipes bind at scale " + copies);
            helper.assertTrue(adapter.bind(level, separatorPos, evaporating) == null,
                    "1:1 brine-to-sodium pattern must not bind 10:1 electrolysis at scale " + copies);
            helper.assertTrue(adapter.bind(level, rotaryPos, separating) == null, "rotary cannot supply chlorine");
            helper.assertTrue(adapter.bind(level, separatorPos, sodiumOnly) != null,
                    "correctly proportioned electrolysis can still leave its byproduct native");
            helper.assertTrue(adapter.bind(level, rotaryPos, sodiumOnly) == null,
                    "10:1 sodium-only pattern must not bind 1:1 rotary at scale " + copies);
            helper.assertTrue(adapter.planWithBinding(level, rotaryPos, evaporating, inputs(rotaryInput),
                    rotaryBinding.handle(), null) != null, "matching rotary recipe dispatches");
            helper.assertTrue(adapter.planWithBinding(level, separatorPos, separating, inputs(separatorInput),
                    separatorBinding.handle(), null) != null, "matching electrolysis recipe dispatches");
            helper.assertTrue(adapter.planWithBinding(level, separatorPos, separating, inputs(rotaryInput),
                    separatorBinding.handle(), null) == null, "runtime quantities cannot bypass the binding ratio");
        }
        var wrongInput = pattern(helper, new GenericStack[] {new GenericStack(AEFluidKey.of(Fluids.WATER), 1)},
                new GenericStack[] {chemical(MekanismChemicals.SODIUM.asStack(1))});
        helper.assertTrue(adapter.bind(level, rotaryPos, wrongInput) == null, "same output cannot hide a wrong input at bind time");
        helper.succeed();
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 100)
    public static void chemicalRecipeRatios(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        place(helper, pos, "large_chemical_infuser");
        var oxygen = chemical(MekanismChemicals.OXYGEN.asStack(32));
        var chlorine = chemical(MekanismChemicals.CHLORINE.asStack(32));
        var acid = chemical(MekanismChemicals.SULFURIC_ACID.asStack(64));
        var adapter = new MekanismMoreMachinesAdapter();
        var valid = pattern(helper, new GenericStack[] {chlorine, oxygen}, new GenericStack[] {acid});
        var bound = adapter.bind(level, pos, valid);
        helper.assertTrue(bound != null, "scaled, reordered chemical recipe binds");
        var unequal = chemical(MekanismChemicals.CHLORINE.asStack(16));
        var unbalanced = pattern(helper, new GenericStack[] {oxygen, unequal}, new GenericStack[] {acid});
        helper.assertTrue(adapter.bind(level, pos, unbalanced) == null, "all input ports must use the same recipe multiplier");
        helper.assertTrue(adapter.planWithBinding(level, pos, valid, inputs(oxygen, unequal), bound.handle(), null) == null,
                "runtime input ratios are rechecked");
        var tooMuchOutput = pattern(helper, new GenericStack[] {oxygen, chlorine},
                new GenericStack[] {chemical(MekanismChemicals.SULFURIC_ACID.asStack(65))});
        helper.assertTrue(adapter.bind(level, pos, tooMuchOutput) == null, "output quantity must agree with recipe multiplier");
        var huge = pattern(helper, new GenericStack[] {chemical(MekanismChemicals.OXYGEN.asStack(Long.MAX_VALUE)),
                chemical(MekanismChemicals.CHLORINE.asStack(Long.MAX_VALUE))},
                new GenericStack[] {chemical(MekanismChemicals.SULFURIC_ACID.asStack(Long.MAX_VALUE))});
        helper.assertTrue(adapter.bind(level, pos, huge) == null, "scaled outputs cannot overflow into a positive match");
        helper.succeed();
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 120)
    public static void sameInputsDifferentOutputs(GameTestHelper helper) {
        var level = helper.getLevel();
        var infuserPos = helper.absolutePos(new BlockPos(2, 2, 2));
        var mixerPos = helper.absolutePos(new BlockPos(8, 2, 2));
        place(helper, infuserPos, "large_chemical_infuser");
        place(helper, mixerPos, "large_pigment_mixer");
        var oxygen = chemical(MekanismChemicals.OXYGEN.asStack(32));
        var chlorine = chemical(MekanismChemicals.CHLORINE.asStack(32));
        var acid = chemical(MekanismChemicals.SULFURIC_ACID.asStack(64));
        var hydrofluoric = chemical(MekanismChemicals.HYDROFLUORIC_ACID.asStack(64));
        var infusing = pattern(helper, new GenericStack[] { chlorine, oxygen }, new GenericStack[] { acid });
        var mixing = pattern(helper, new GenericStack[] { chlorine, oxygen }, new GenericStack[] { hydrofluoric });
        var adapter = new MekanismMoreMachinesAdapter();
        var infuserBinding = adapter.bind(level, infuserPos, infusing);
        var mixerBinding = adapter.bind(level, mixerPos, mixing);
        helper.assertTrue(infuserBinding != null && mixerBinding != null, "each native recipe binds its matching machine");
        helper.assertTrue(adapter.bind(level, mixerPos, infusing) == null, "infuser output cannot bind pigment mixer with identical inputs");
        helper.assertTrue(adapter.bind(level, infuserPos, mixing) == null, "pigment output cannot bind chemical infuser with identical inputs");
        var inputs = inputs(chlorine, oxygen);
        var binding = (MekRecipeBinding) infuserBinding.handle();
        var mode = binding.spec().activeMode(level.getBlockEntity(infuserPos));
        var ordered = binding.arrange(level, mode, infusing, inputs);
        helper.assertTrue(ordered != null && ordered.get(0).what().equals(oxygen.what())
                && ordered.get(1).what().equals(chlorine.what()), "native ingredient matching fixes reversed chemical port order");
        helper.assertTrue(binding.arrange(level, mode, infusing,
                inputs(chemical(MekanismChemicals.HYDROGEN.asStack(32)), chlorine)) == null,
                "unrelated runtime inputs are rejected before insertion");
        helper.assertTrue(adapter.planWithBinding(level, infuserPos, infusing, inputs,
                infuserBinding.handle(), null) != null, "matching machine produces a real dispatch plan");
        helper.assertTrue(adapter.planWithBinding(level, mixerPos, infusing, inputs,
                infuserBinding.handle(), null) == null, "cached binding cannot be reused for another machine");
        var unknown = pattern(helper, new GenericStack[] { oxygen, chlorine },
                new GenericStack[] {new GenericStack(AEItemKey.of(Items.DIAMOND), 1)});
        helper.assertTrue(adapter.bind(level, infuserPos, unknown) == null, "unknown output is not blindly accepted");
        helper.succeed();
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 100)
    public static void rotaryModeAndElectrolysis(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var rotaryPos = helper.absolutePos(new BlockPos(2, 2, 2));
        var separatorPos = helper.absolutePos(new BlockPos(8, 2, 2));
        place(helper, rotaryPos, "large_rotary_condensentrator");
        place(helper, separatorPos, "large_electrolytic_separator");
        var water = new GenericStack(AEFluidKey.of(Fluids.WATER), 2);
        var hydrogen = chemical(MekanismChemicals.HYDROGEN.asStack(2));
        var oxygen = chemical(MekanismChemicals.OXYGEN.asStack(1));
        var separating = pattern(helper, new GenericStack[] {water}, new GenericStack[] { hydrogen, oxygen });
        var adapter = new MekanismMoreMachinesAdapter();
        helper.assertTrue(adapter.bind(level, separatorPos, separating) != null, "both electrolysis outputs are recognized");
        helper.assertTrue(adapter.bind(level, rotaryPos, separating) == null, "same water input does not route separating to rotary");
        var vapor = chemical(MekanismChemicals.WATER_VAPOR.asStack(1));
        var evaporating = pattern(helper, new GenericStack[] {new GenericStack(water.what(), 1)}, new GenericStack[] {vapor});
        var rotary = level.getBlockEntity(rotaryPos);
        var mode = rotary.getClass().getDeclaredField("mode"); mode.setAccessible(true);
        mode.setBoolean(rotary, false);
        var bound = adapter.bind(level, rotaryPos, evaporating);
        helper.assertTrue(bound != null, "rotary keeps a recipe binding across direction changes");
        helper.assertTrue(!adapter.canDispatch(level, rotaryPos, bound.handle()), "opposite rotary mode cannot dispatch");
        mode.setBoolean(rotary, true);
        helper.assertTrue(adapter.canDispatch(level, rotaryPos, bound.handle()), "switching to matching mode enables cached binding");
        helper.assertTrue(adapter.planWithBinding(level, rotaryPos, evaporating, inputs(new GenericStack(water.what(), 1)),
                bound.handle(), null) != null, "matching rotary direction builds a dispatch plan");
        helper.succeed();
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 100)
    public static void remainingRecipeFamilies(GameTestHelper helper) {
        var level = helper.getLevel();
        var solarPos = helper.absolutePos(new BlockPos(2, 2, 2));
        var nuclearPos = helper.absolutePos(new BlockPos(8, 2, 2));
        place(helper, solarPos, "large_solar_neutron_activator");
        place(helper, nuclearPos, "large_antiprotonic_nucleosynthesizer");
        var solarRecipe = mekanism.common.recipe.MekanismRecipeType.ACTIVATING.getRecipes(level).getFirst().value();
        var solarInput = solarRecipe.getInput().getRepresentations().getFirst();
        var solar = pattern(helper, new GenericStack[] {chemical(solarInput)},
                new GenericStack[] {chemical(solarRecipe.getOutput(solarInput))});
        var adapter = new MekanismMoreMachinesAdapter();
        var solarBinding = adapter.bind(level, solarPos, solar);
        helper.assertTrue(solarBinding != null && adapter.planWithBinding(level, solarPos, solar,
                inputs(chemical(solarInput)), solarBinding.handle(), null) != null,
                "native solar chemical-to-chemical recipes still dispatch");

        var nuclearRecipe = mekanism.common.recipe.MekanismRecipeType.NUCLEOSYNTHESIZING.getRecipes(level).getFirst().value();
        var item = nuclearRecipe.getItemInput().getRepresentations().getFirst();
        var gas = nuclearRecipe.getChemicalInput().getRepresentations().getFirst();
        var output = nuclearRecipe.getOutput(item, gas);
        var itemInput = new GenericStack(AEItemKey.of(item), item.getCount());
        var nuclear = pattern(helper, new GenericStack[] {itemInput, chemical(gas)},
                new GenericStack[] {new GenericStack(AEItemKey.of(output), output.getCount())});
        var nuclearBinding = adapter.bind(level, nuclearPos, nuclear);
        helper.assertTrue(nuclearBinding != null && adapter.planWithBinding(level, nuclearPos, nuclear,
                inputs(itemInput, chemical(gas)), nuclearBinding.handle(), null) != null,
                "native nucleosynthesizer item-and-chemical recipes still dispatch");
        var timedItem = new GenericStack(AEItemKey.of(Items.COBBLESTONE), 8);
        var timedGas = chemical(MekanismChemicals.ANTIMATTER.asStack(80));
        var perTick = pattern(helper, new GenericStack[] {timedItem, timedGas},
                new GenericStack[] {new GenericStack(AEItemKey.of(Items.DIAMOND), 8)});
        var timedBinding = adapter.bind(level, nuclearPos, perTick);
        helper.assertTrue(timedBinding != null && adapter.planWithBinding(level, nuclearPos, perTick,
                inputs(timedItem, timedGas), timedBinding.handle(), null) != null,
                "per-tick chemical supply must not be mistaken for a fixed recipe amount");
        helper.succeed();
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 100)
    public static void runningSeparatorReturnsBothOutputsAndPreservesFilter(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(5, 2, 5));
        place(helper, pos, "large_electrolytic_separator");
        var machine = level.getBlockEntity(pos);
        var spec = MekmmMachines.ALL.stream().filter(s -> s.block().getPath().equals("large_electrolytic_separator")).findFirst().orElseThrow();
        var facing = MekReflection.getFacing(machine);
        for (var port : spec.activeMode(machine).outputs()) {
            var offset = port.spec();
            var portPos = pos.relative(facing.getOpposite(), offset.backSteps())
                    .relative(facing.getClockWise(), offset.leftSteps()).above(offset.y());
            level.setBlockAndUpdate(portPos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:bounding_block")).defaultBlockState());
            ((mekanism.common.tile.TileEntityBoundingBlock) level.getBlockEntity(portPos)).setMainLocation(pos, false);
        }
        var left = (mekanism.api.chemical.IChemicalTank) machine.getClass().getField("leftTank").get(machine);
        var right = (mekanism.api.chemical.IChemicalTank) machine.getClass().getField("rightTank").get(machine);
        left.setStack(MekanismChemicals.SODIUM.asStack(left.getCapacity()));
        right.setStack(MekanismChemicals.CHLORINE.asStack(right.getCapacity()));
        ((mekanism.common.tile.base.TileEntityMekanism) machine).setActive(true);
        helper.assertTrue(MekReflection.isActive(machine), "separator is active before collection");
        var filter = new com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter();
        filter.allowStrict(chemical(MekanismChemicals.SODIUM.asStack(1)).what());
        var adapter = new MekanismMoreMachinesAdapter();
        var first = adapter.extractOutputs(level, pos, filter, null);
        helper.assertTrue(first.size() == 1 && left.getStored() == 0 && right.getStored() == right.getCapacity(),
                "active collection empties permitted output and preserves excluded byproduct");
        filter.allowStrict(chemical(MekanismChemicals.CHLORINE.asStack(1)).what());
        var second = adapter.extractOutputs(level, pos, filter, null);
        helper.assertTrue(second.size() == 1 && right.getStored() == 0, "second full output can be drained while active");
        helper.succeed();
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 160)
    public static void normalProviderUsesAdaptiveReturnDeadlines(GameTestHelper helper) throws Exception {
        checkProviderReturnSchedule(helper, false);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 160)
    public static void wirelessProviderUsesAdaptiveReturnDeadlines(GameTestHelper helper) throws Exception {
        checkProviderReturnSchedule(helper, true);
    }

    private static void checkProviderReturnSchedule(GameTestHelper helper, boolean wireless) throws Exception {
        var level = helper.getLevel();
        var main = helper.absolutePos(new BlockPos(5, 2, 5));
        place(helper, main, "large_electrolytic_separator");
        var machine = level.getBlockEntity(main);
        var spec = MekmmMachines.ALL.stream().filter(s -> s.block().getPath().equals("large_electrolytic_separator"))
                .findFirst().orElseThrow();
        var facing = MekReflection.getFacing(machine);
        var ports = new java.util.ArrayList<BlockPos>();
        for (var port : spec.activeMode(machine).outputs()) {
            var portPos = main.relative(facing.getOpposite(), port.spec().backSteps())
                    .relative(facing.getClockWise(), port.spec().leftSteps()).above(port.spec().y());
            level.setBlockAndUpdate(portPos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:bounding_block"))
                    .defaultBlockState());
            ((mekanism.common.tile.TileEntityBoundingBlock) level.getBlockEntity(portPos)).setMainLocation(main, false);
            ports.add(portPos);
        }
        var providerPos = wireless ? main.north(3) : main.north();
        net.minecraft.world.level.block.Block providerBlock = wireless
                ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get() : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        level.setBlockAndUpdate(providerPos, providerBlock.defaultBlockState());
        level.setBlockAndUpdate(providerPos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(providerPos);
        provider.getAdapterInv().setItemDirect(0, PPItems.MEKMM_ADAPTER.toStack());
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        var sodium = chemical(MekanismChemicals.SODIUM.asStack(16));
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEFluidKey.of(BuiltInRegistries.FLUID.get(ResourceLocation.parse("mekanism:brine"))), 160)),
                List.of(sodium)));
        if (wireless) {
            helper.assertTrue(provider.getProviderMode() == ProviderMode.WIRELESS, "wireless provider variant placed");
            helper.assertTrue(provider.addOrUpdateConnection(level.dimension(), main, facing), "controller connection added");
            for (var port : ports) {
                helper.assertTrue(provider.addOrUpdateConnection(level.dimension(), port, facing), "bounding port connection added");
            }
        }
        var output = (mekanism.api.chemical.IChemicalTank) machine.getClass().getField("leftTank").get(machine);
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(provider.getMainNode().isActive(), "provider is powered");
            output.setStack(MekanismChemicals.SODIUM.asStack(16));
            logic.tickAutoReturn();
            helper.assertTrue(output.getStored() == 16, "OFF leaves outputs in the machine");
            provider.setReturnMode(ReturnMode.AUTO);
            logic.tickAutoReturn();
            helper.assertTrue(output.getStored() == 0, "AUTO collects immediately on activation");
            output.setStack(MekanismChemicals.SODIUM.asStack(16));
            logic.tickAutoReturn();
            helper.assertTrue(output.getStored() == 16, "repeated same-tick calls do not scan the controller again");
            helper.runAfterDelay(1, () -> {
                logic.tickAutoReturn();
                helper.assertTrue(output.getStored() == 0, "active output is collected on the next tick");
            });
            // After the successful poll at +1, empty polls at +2,+3,+5,+9,+17 back off to +37.
            helper.runAfterDelay(20, () -> {
                output.setStack(MekanismChemicals.SODIUM.asStack(16));
                logic.tickAutoReturn();
                helper.assertTrue(output.getStored() == 16, "regular discovery does not override the idle deadline");
            });
            helper.runAfterDelay(36, () -> {
                logic.tickAutoReturn();
                helper.assertTrue(output.getStored() == 16, "idle target remains asleep until its deadline");
            });
            helper.runAfterDelay(37, () -> {
                logic.tickAutoReturn();
                helper.assertTrue(output.getStored() == 0, "idle poll collects within the 20-tick ceiling");
                provider.setReturnMode(ReturnMode.OFF);
                output.setStack(MekanismChemicals.SODIUM.asStack(16));
                logic.tickAutoReturn();
                helper.assertTrue(output.getStored() == 16, "OFF cancels the fast return schedule");
                provider.setReturnMode(ReturnMode.AUTO);
                logic.tickAutoReturn();
                helper.assertTrue(output.getStored() == 0, "reenabling AUTO discovers the target again");
                if (wireless) {
                    provider.removeConnection(level.dimension(), main);
                    for (var port : ports) provider.removeConnection(level.dimension(), port);
                    output.setStack(MekanismChemicals.SODIUM.asStack(16));
                    helper.runAfterDelay(1, () -> {
                        logic.tickAutoReturn();
                        helper.assertTrue(output.getStored() == 16, "removed connections cannot use cached fast return targets");
                        helper.succeed();
                    });
                } else helper.succeed();
            });
        });
    }

    private static void place(GameTestHelper helper, BlockPos pos, String id) {
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mekmm", id));
        helper.assertTrue(block != net.minecraft.world.level.block.Blocks.AIR, "native MekMM block registered: " + id);
        helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
    }

    private static IPatternDetails pattern(GameTestHelper helper, GenericStack[] inputs, GenericStack[] outputs) {
        var item = PatternDetailsHelper.encodeProcessingPattern(List.of(inputs), List.of(outputs));
        var pattern = PatternDetailsHelper.decodePattern(item, helper.getLevel());
        helper.assertTrue(pattern != null, "native processing pattern decodes");
        return pattern;
    }

    private static GenericStack chemical(ChemicalStack stack) {
        return new GenericStack(MekanismKey.of(stack), stack.getAmount());
    }

    private static KeyCounter[] inputs(GenericStack... stacks) {
        var result = new KeyCounter[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            result[i] = new KeyCounter(); result[i].add(stacks[i].what(), stacks[i].amount());
        }
        return result;
    }
}
