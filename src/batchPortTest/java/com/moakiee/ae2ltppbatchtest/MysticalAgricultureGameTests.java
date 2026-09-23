package com.moakiee.ae2ltppbatchtest;

import java.util.ArrayList;
import java.util.List;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.ma.InfusionAltarAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ma.AwakeningAltarAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** Exercises the real Mystical Agriculture altar and pedestal inventory bridge. */
public final class MysticalAgricultureGameTests {
    private static final BlockPos[] PEDESTALS = {
            new BlockPos(3, 0, 0), new BlockPos(0, 0, 3),
            new BlockPos(-3, 0, 0), new BlockPos(0, 0, -3),
            new BlockPos(2, 0, 2), new BlockPos(2, 0, -2),
            new BlockPos(-2, 0, 2), new BlockPos(-2, 0, -2)
    };
    private static final BlockPos[] AWAKENING_SUB_SLOTS = {
            new BlockPos(-3, 0, 0), new BlockPos(2, 0, 2),
            new BlockPos(3, 0, 0), new BlockPos(-2, 0, -2),
            new BlockPos(0, 0, -3), new BlockPos(2, 0, -2),
            new BlockPos(0, 0, 3), new BlockPos(-2, 0, 2)
    };

    private MysticalAgricultureGameTests() {}

    public static void infusionAltarDistributesNativeRecipe(GameTestHelper helper) {
        var level = helper.getLevel();
        var altar = helper.absolutePos(new BlockPos(6, 2, 6));
        level.setBlockAndUpdate(altar, block("mysticalagriculture:infusion_altar").defaultBlockState());
        for (var offset : PEDESTALS) {
            level.setBlockAndUpdate(altar.offset(offset),
                    block("mysticalagriculture:infusion_pedestal").defaultBlockState());
        }
        var inputs = new ArrayList<GenericStack>();
        inputs.add(stack("mysticalagriculture:blank_augment", 1));
        for (int i = 0; i < 4; i++) {
            inputs.add(stack("minecraft:golden_apple", 1));
            inputs.add(stack("mysticalagriculture:inferium_essence", 1));
        }
        var output = stack("mysticalagriculture:absorption_i_augment", 1);
        var encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(output));
        var pattern = PatternDetailsHelper.decodePattern(encoded, level);
        var adapter = new InfusionAltarAdapter();
        var binding = adapter.bind(level, altar, pattern);
        helper.assertTrue(binding != null, "MA infusion recipe did not bind to the real altar");
        helper.assertTrue(adapter.canDispatch(level, altar, binding.handle()),
                "MA altar with eight empty pedestals rejected dispatch");
        var plan = adapter.planWithBinding(level, altar, pattern, counters(inputs), binding.handle(), null);
        helper.assertTrue(plan != null && plan.targets().size() == 9,
                "MA infusion recipe did not plan all nine inputs");
        var retained = new ArrayList<GenericStack>();
        var result = DispatchExecutor.execute(plan, null, retained::addAll);
        helper.assertTrue(result.success() && retained.isEmpty(),
                "MA infusion dispatch lost ownership or retained inputs");
        var altarInv = CucumberInventoryAccess.get(level.getBlockEntity(altar));
        helper.assertTrue(altarInv != null && altarInv.getStackInSlot(0).is(item("mysticalagriculture:blank_augment")),
                "MA altar did not receive central input");
        for (var offset : PEDESTALS) {
            var inv = CucumberInventoryAccess.get(level.getBlockEntity(altar.offset(offset)));
            helper.assertTrue(inv != null && !inv.getStackInSlot(0).isEmpty(),
                    "MA pedestal did not receive its input at " + offset);
        }
        helper.runAfterDelay(125, () -> {
            var completed = CucumberInventoryAccess.get(level.getBlockEntity(altar));
            helper.assertTrue(completed != null
                            && completed.getStackInSlot(1).is(item("mysticalagriculture:absorption_i_augment")),
                    "MA infusion altar did not produce the native recipe output");
            helper.succeed();
        });
    }

    public static void awakeningAltarDistributesNativeRecipe(GameTestHelper helper) {
        var level = helper.getLevel();
        var altar = helper.absolutePos(new BlockPos(6, 2, 6));
        level.setBlockAndUpdate(altar, block("mysticalagriculture:awakening_altar").defaultBlockState());
        for (int i = 0; i < AWAKENING_SUB_SLOTS.length; i++) {
            var name = i < 4 ? "mysticalagriculture:awakening_pedestal" : "mysticalagriculture:essence_vessel";
            level.setBlockAndUpdate(altar.offset(AWAKENING_SUB_SLOTS[i]), block(name).defaultBlockState());
        }
        var inputs = new ArrayList<GenericStack>();
        inputs.add(stack("mysticalagriculture:supremium_block", 1));
        for (int i = 0; i < 4; i++) inputs.add(stack("mysticalagriculture:cognizant_dust", 1));
        for (var element : List.of("air", "earth", "water", "fire")) {
            inputs.add(stack("mysticalagriculture:" + element + "_essence", 10));
        }
        var output = stack("mysticalagriculture:awakened_supremium_block", 1);
        var encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(output));
        var pattern = PatternDetailsHelper.decodePattern(encoded, level);
        var adapter = new AwakeningAltarAdapter();
        var binding = adapter.bind(level, altar, pattern);
        helper.assertTrue(binding != null, "MA awakening recipe did not bind to the real altar");
        helper.assertTrue(adapter.canDispatch(level, altar, binding.handle()),
                "MA awakening altar with four pedestals and four vessels rejected dispatch");
        var plan = adapter.planWithBinding(level, altar, pattern, counters(inputs), binding.handle(), null);
        helper.assertTrue(plan != null && plan.targets().size() == 9,
                "MA awakening recipe did not plan all nine inputs");
        var retained = new ArrayList<GenericStack>();
        var result = DispatchExecutor.execute(plan, null, retained::addAll);
        helper.assertTrue(result.success() && retained.isEmpty(),
                "MA awakening dispatch lost ownership or retained inputs");
        var altarInv = CucumberInventoryAccess.get(level.getBlockEntity(altar));
        helper.assertTrue(altarInv != null && altarInv.getStackInSlot(0).is(item("mysticalagriculture:supremium_block")),
                "MA awakening altar did not receive central input");
        for (int i = 0; i < AWAKENING_SUB_SLOTS.length; i++) {
            var inv = CucumberInventoryAccess.get(level.getBlockEntity(altar.offset(AWAKENING_SUB_SLOTS[i])));
            var expectedCount = i < 4 ? 1 : 10;
            helper.assertTrue(inv != null && inv.getStackInSlot(0).getCount() == expectedCount,
                    "MA awakening sub-slot did not receive its exact input at index " + i);
        }
        helper.runAfterDelay(125, () -> {
            var completed = CucumberInventoryAccess.get(level.getBlockEntity(altar));
            helper.assertTrue(completed != null
                            && completed.getStackInSlot(1).is(item("mysticalagriculture:awakened_supremium_block")),
                    "MA awakening altar did not produce the native recipe output");
            helper.succeed();
        });
    }

    public static void providerRoutesInfusionRecipe(GameTestHelper helper) {
        var level = helper.getLevel();
        var altar = helper.absolutePos(new BlockPos(6, 2, 6));
        level.setBlockAndUpdate(altar, block("mysticalagriculture:infusion_altar").defaultBlockState());
        for (var offset : PEDESTALS) {
            level.setBlockAndUpdate(altar.offset(offset),
                    block("mysticalagriculture:infusion_pedestal").defaultBlockState());
        }
        var providerPos = altar.west();
        level.setBlockAndUpdate(providerPos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(providerPos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(providerPos);
        provider.getAdapterInv().setItemDirect(0, PPItems.MA_INFUSION_ADAPTER.toStack());
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        var inputs = new ArrayList<GenericStack>();
        inputs.add(stack("mysticalagriculture:blank_augment", 1));
        for (int i = 0; i < 4; i++) {
            inputs.add(stack("minecraft:golden_apple", 1));
            inputs.add(stack("mysticalagriculture:inferium_essence", 1));
        }
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                inputs, List.of(stack("mysticalagriculture:absorption_i_augment", 1))));
        helper.runAfterDelay(50, () -> {
            logic.updatePatterns();
            helper.assertTrue(!logic.getAvailablePatterns().isEmpty(), "MA pattern was not advertised by PP");
            var pattern = logic.getAvailablePatterns().getFirst();
            helper.assertTrue(logic.pushPattern(pattern, counters(inputs)),
                    "PP did not route MA infusion pattern through its installed adapter");
            var inventory = CucumberInventoryAccess.get(level.getBlockEntity(altar));
            helper.assertTrue(inventory != null && inventory.getStackInSlot(0)
                            .is(item("mysticalagriculture:blank_augment")),
                    "PP dispatch did not place the central MA input");
            helper.runAfterDelay(125, () -> {
                var completed = CucumberInventoryAccess.get(level.getBlockEntity(altar));
                helper.assertTrue(completed != null && completed.getStackInSlot(1)
                                .is(item("mysticalagriculture:absorption_i_augment")),
                        "PP-driven MA infusion did not complete its native recipe");
                helper.succeed();
            });
        });
    }

    private static KeyCounter[] counters(List<GenericStack> stacks) {
        var result = new KeyCounter[stacks.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = new KeyCounter();
            result[i].add(stacks.get(i).what(), stacks.get(i).amount());
        }
        return result;
    }

    private static net.minecraft.world.level.block.Block block(String name) {
        return BuiltInRegistries.BLOCK.get(Identifier.parse(name)).orElseThrow().value();
    }

    private static Item item(String name) {
        return BuiltInRegistries.ITEM.get(Identifier.parse(name)).orElseThrow().value();
    }

    private static GenericStack stack(String name, long count) {
        return new GenericStack(AEItemKey.of(item(name)), count);
    }
}
