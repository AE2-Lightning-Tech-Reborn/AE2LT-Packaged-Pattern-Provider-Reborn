package com.moakiee.ae2ltppbatchtest;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;

import java.util.List;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.ae2lt.overload.pattern.PatternConversionService;
import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.overload.runtime.pattern.Ae2PlainPatternResolver;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.avaritia.AvaritiaTableAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingOutputBuffer;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingTableAdapter;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

/** Per-slot overload modes and recipe selection against actual component-sensitive table recipes. */
public final class OverloadWorkbenchGameTests {
    public static void avStrict(GameTestHelper helper) {
        run(helper, false, false, false, false, 1);
    }

    public static void avStrictLarge(GameTestHelper helper) {
        run(helper, false, false, false, false, 100_000);
    }

    public static void avIdInputStrictOutput(GameTestHelper helper) {
        run(helper, false, true, false, false, 1);
    }

    public static void avIdInputStrictOutputWireless(GameTestHelper helper) {
        run(helper, false, true, false, true, 1);
    }

    public static void avIdInputIdOutput(GameTestHelper helper) {
        run(helper, false, true, true, false, 1);
    }

    public static void avIdInputIdOutputWireless(GameTestHelper helper) {
        run(helper, false, true, true, true, 1);
    }

    public static void avIdInputLarge(GameTestHelper helper) {
        run(helper, false, true, false, false, 100_000);
    }

    public static void ecIdInputStrictOutput(GameTestHelper helper) {
        run(helper, true, true, false, false, 1);
    }

    public static void ecIdInputStrictOutputWireless(GameTestHelper helper) {
        run(helper, true, true, false, true, 1);
    }

    public static void ecIdInputIdOutput(GameTestHelper helper) {
        run(helper, true, true, true, false, 1);
    }

    public static void ecIdInputIdOutputWireless(GameTestHelper helper) {
        run(helper, true, true, true, true, 1);
    }

    public static void ecIdInputLarge(GameTestHelper helper) {
        run(helper, true, true, false, false, 100_000);
    }

    private static void run(GameTestHelper helper, boolean ec, boolean idInput, boolean idOutput,
            boolean wireless, long amount) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        Block block = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get()
                : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        level.setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var tablePos = pos.east(wireless ? 3 : 1);
        String target = ec ? "extendedcrafting:ultimate_table" : "avaritia:extreme_crafting_table";
        level.setBlockAndUpdate(tablePos, BuiltInRegistries.BLOCK.getValue(Identifier.parse(target)).defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        provider.getAdapterInv().setItemDirect(0, ec ? PPItems.EC_ULTIMATE_ADAPTER.toStack()
                : PPItems.AVARITIA_EXTREME_TABLE_ADAPTER.toStack());
        if (wireless) helper.assertTrue(provider.addOrUpdateConnection(level.dimension(), tablePos, Direction.WEST),
                "wireless target connected");

        var milk = key(Items.MILK_BUCKET, idInput ? "required" : null);
        var captured = key(Items.IRON_INGOT, idInput ? "captured" : null);
        var actual = key(Items.IRON_INGOT, idInput ? "required" : null);
        var output = AEItemKey.of(idInput ? Items.HEART_OF_THE_SEA : Items.NAUTILUS_SHELL);
        var encoded = encode(helper, milk, captured, output, idInput, idOutput, amount);
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        logic.getPatternInv().setItemDirect(0, encoded);
        helper.runAfterDelay(50, () -> {
            logic.updatePatterns();
            helper.assertTrue(logic.getAvailablePatterns().size() == 1, "overload registered");
            var pattern = logic.getAvailablePatterns().getFirst();
            helper.assertTrue(pattern.getInputs()[1].isValid(actual, level), "pattern accepts actual recipe input");
            helper.assertTrue(logic.pushPattern(pattern, inputs(milk, actual, amount)),
                    "valid actual input must craft independently of output mode");
            assertOutputs(helper, logic, output, amount);

            if (idInput) {
                // Keep the same binding and pattern; only the incoming concrete components change.
                var alternate = key(Items.IRON_INGOT, "alternate");
                helper.assertTrue(logic.pushPattern(pattern, inputs(milk, alternate, amount)),
                        "cached binding must retain the other valid NBT recipe");
                assertOutputs(helper, logic, output, 2 * amount);
                var invalid = inputs(milk, key(Items.IRON_INGOT, "not_a_recipe"), amount);
                helper.assertTrue(!logic.pushPattern(pattern, invalid), "ID_ONLY cannot bypass the actual table recipe");
                helper.assertTrue(invalid[0].get(milk) == amount, "failed attempt leaves input ownership unchanged");
                assertOutputs(helper, logic, output, 2 * amount);
                assertIndependentModes(helper, ec, tablePos, milk, captured, actual, output, amount);
            }
            System.out.println("PP_OVERLOAD_TABLE_PASS target=" + target + " inputId=" + idInput
                    + " outputId=" + idOutput + " wireless=" + wireless + " amount=" + amount
                    + " cachedRecipeSwitch=" + idInput);
            helper.succeed();
        });
    }

    private static void assertIndependentModes(GameTestHelper helper, boolean ec, BlockPos tablePos,
            AEItemKey milk, AEItemKey captured, AEItemKey actual, AEItemKey output, long amount) {
        VirtualCraftingAdapter adapter = ec ? new ExtendedCraftingTableAdapter() : new AvaritiaTableAdapter();
        var level = helper.getLevel();
        var strictInput = decode(helper, encode(helper, milk, captured, output, false, true, amount));
        helper.assertTrue(adapter.bind(level, tablePos, strictInput) == null,
                "ID_ONLY output cannot loosen a strict input");
        var wrongMilk = decode(helper, encode(helper, key(Items.MILK_BUCKET, "wrong"), captured,
                output, true, true, amount));
        helper.assertTrue(adapter.bind(level, tablePos, wrongMilk) == null,
                "one ID_ONLY input cannot loosen another strict input");

        var differentOutput = key(output.getItem(), "captured_output");
        var strictOutput = decode(helper, encode(helper, milk, captured, differentOutput, true, false, amount));
        helper.assertTrue(adapter.bind(level, tablePos, strictOutput) == null,
                "ID_ONLY input cannot loosen a strict output");
        var fuzzyOutput = decode(helper, encode(helper, milk, captured, differentOutput, true, true, amount));
        var binding = adapter.bind(level, tablePos, fuzzyOutput);
        helper.assertTrue(binding != null, "output ID_ONLY permits the recipe's real output components");
        var planned = adapter.planVirtualWithBinding(level, tablePos, fuzzyOutput,
                inputs(milk, actual, amount), binding.handle(), null);
        helper.assertTrue(planned != null && planned.outputs().getFirst().what().equals(output),
                "virtual craft returns the actual recipe result, preserving its components");
    }

    private static ItemStack encode(GameTestHelper helper, AEItemKey milk, AEItemKey iron, AEItemKey output,
            boolean idInput, boolean idOutput, long amount) {
        var plain = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(milk, amount), new GenericStack(iron, amount)),
                List.of(new GenericStack(output, amount)));
        var flags = EncodedOverloadPattern.builder().input(0, MatchMode.STRICT)
                .input(1, idInput ? MatchMode.ID_ONLY : MatchMode.STRICT)
                .output(0, idOutput ? MatchMode.ID_ONLY : MatchMode.STRICT).build();
        return new PatternConversionService().createOverloadPatternStack(
                (OverloadPatternItem) ModItems.OVERLOAD_PATTERN.get(),
                new Ae2PlainPatternResolver(helper.getLevel()).resolve(plain), flags);
    }

    private static IPatternDetails decode(GameTestHelper helper, ItemStack encoded) {
        return PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
    }

    private static AEItemKey key(Item item, String data) {
        var stack = new ItemStack(item);
        if (data != null) {
            var tag = new CompoundTag();
            tag.putString("pp_overload", data);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return AEItemKey.of(stack);
    }

    private static KeyCounter[] inputs(AEItemKey milk, AEItemKey iron, long amount) {
        var counters = new KeyCounter[] {new KeyCounter(), new KeyCounter()};
        counters[0].add(milk, amount);
        counters[1].add(iron, amount);
        return counters;
    }

    private static void assertOutputs(GameTestHelper helper, PackagedPatternProviderLogic logic,
            AEItemKey output, long amount) {
        try {
            var field = PackagedPatternProviderLogic.class.getDeclaredField("pendingOutputs");
            field.setAccessible(true);
            var outputs = ((PendingOutputBuffer) field.get(logic)).snapshot();
            helper.assertTrue(outputs.stream().filter(stack -> stack.what().equals(output))
                    .mapToLong(GenericStack::amount).sum() == amount, "exact output amount");
            helper.assertTrue(outputs.stream().filter(stack -> stack.what().equals(AEItemKey.of(Items.BUCKET)))
                    .mapToLong(GenericStack::amount).sum() == amount, "exact container returns");
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }
}
