package com.moakiee.ae2lt.packaged.gametest;

import java.util.ArrayList;
import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.iface.PatternProviderReturnInventory;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.ars.ArsNouveauImbuementChamberAdapter;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Uses the real Forge Ars fire-essence recipe, ticker, capabilities and NBT loader. */
@GameTestHolder("ae2ltpp_ars")
@PrefixGameTestTemplate(false)
public final class ArsResidentCatalystTests {
    private static final String RESIDENT_FLAG = "ae2ltpp:imbuement_resident_catalysts";

    private ArsResidentCatalystTests() {}

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 200)
    public static void residentCatalystsSurvive64NativeCraftsAndReloads(GameTestHelper h)
            throws ReflectiveOperationException {
        var fixture = new Fixture(h);
        fixture.placeCatalysts();
        var pattern = fixture.pattern(List.of(fixture.reagent), List.of(fixture.product));
        long collected = 0;
        for (int order = 0; order < 64; order++) {
            var binding = fixture.adapter.bind(h.getLevel(), fixture.pos, pattern);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(binding != null, "Resident pattern failed to bind at order " + order);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.adapter.canDispatch(h.getLevel(), fixture.pos, binding.handle()),
                    "Resident chamber unavailable at order " + order);
            var inputs = counters(List.of(fixture.reagent));
            var plan = fixture.adapter.planWithBinding(h.getLevel(), fixture.pos, pattern, inputs,
                    binding.handle(), IActionSource.empty());
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan != null && plan.targets().size() == 1, "Resident plan must only target chamber");
            var before = fixture.chamber().saveWithFullMetadata();
            var target = plan.targets().get(0);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.customInserter().apply(fixture.reagent, Actionable.SIMULATE) == 1L,
                    "Valid resident simulation was rejected");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(before.equals(fixture.chamber().saveWithFullMetadata()), "Simulation mutated chamber NBT");
            fixture.assertCatalysts();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(DispatchExecutor.execute(plan, IActionSource.empty(), new PatternProviderReturnInventory(() -> {}))
                    .success(), "Resident dispatch failed");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(inputs[0].get(fixture.reagent.what()) == 1, "Dispatch changed borrowed input template");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.chamber().getPersistentData().getBoolean(RESIDENT_FLAG), "Resident flag missing");
            fixture.tick(20);
            // Recreate the entire machine while an order is in flight, then again
            // after completion. This exercises Forge serialization, not load on self.
            fixture.reload();
            fixture.finish();
            fixture.reload();
            var harvested = fixture.adapter.extractOutputs(h.getLevel(), fixture.pos, fixture.filter(), IActionSource.empty());
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(harvested.equals(List.of(fixture.product)), "Resident harvest touched catalysts: " + harvested);
            collected += harvested.get(0).amount();
            fixture.assertCatalysts();
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.adapter.extractOutputs(h.getLevel(), fixture.pos, fixture.filter(), IActionSource.empty())
                    .isEmpty(), "Resident output was harvested twice");
        }
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(collected == 64, "Resident output count changed across reloads");
        org.slf4j.LoggerFactory.getLogger("ae2ltpp-stress").info(
                "STRESS PASS native Ars resident crafts=64 machineReloads=128 catalysts=NbtAndCountPreserved");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 200)
    public static void residentDispatchRechecksMissingWrongAndExtraCatalysts(GameTestHelper h) {
        var fixture = new Fixture(h);
        var pattern = fixture.pattern(List.of(fixture.reagent), List.of(fixture.product));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.adapter.bind(h.getLevel(), fixture.pos, pattern) == null, "Missing catalysts bound");
        fixture.placeCatalysts();
        var binding = fixture.adapter.bind(h.getLevel(), fixture.pos, pattern);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(binding != null, "Valid resident pattern failed to bind");
        var inputs = counters(List.of(fixture.reagent));
        var plan = fixture.adapter.planWithBinding(h.getLevel(), fixture.pos, pattern, inputs,
                binding.handle(), IActionSource.empty());
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan != null, "Valid resident plan missing");
        var before = fixture.chamber().saveWithFullMetadata();
        for (var invalid : List.of(ItemStack.EMPTY, new ItemStack(Items.DIRT))) {
            fixture.pedestal(0).setItem(0, invalid);
            fixture.assertRejected(pattern, binding.handle(), inputs);
            var target = plan.targets().get(0);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.customInserter().apply(fixture.reagent, Actionable.SIMULATE) == 0L,
                    "Stale plan accepted invalid catalysts in simulation");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(target.customInserter().apply(fixture.reagent, Actionable.MODULATE) == 0L,
                    "Stale plan accepted invalid catalysts in modulation");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!DispatchExecutor.execute(plan, IActionSource.empty(), new PatternProviderReturnInventory(() -> {}))
                    .success(), "Executor accepted invalid resident catalysts");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(before.equals(fixture.chamber().saveWithFullMetadata()), "Rejected dispatch mutated machine");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(inputs[0].get(fixture.reagent.what()) == 1, "Rejected dispatch consumed template");
            fixture.placeCatalysts();
        }
        var extra = fixture.pos.offset(0, 0, -1);
        place(h, extra, "ars_nouveau:arcane_pedestal");
        ((Container) h.getLevel().getBlockEntity(extra)).setItem(0, new ItemStack(Items.DIRT));
        fixture.assertRejected(pattern, binding.handle(), inputs);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan.targets().get(0).customInserter().apply(fixture.reagent, Actionable.SIMULATE) == 0L,
                "Stale plan accepted extra occupied pedestal");
        ((Container) h.getLevel().getBlockEntity(extra)).clearContent();
        var inventedOutputs = List.of(fixture.product, fixture.catalysts.get(0));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.adapter.bind(h.getLevel(), fixture.pos,
                fixture.pattern(List.of(fixture.reagent), inventedOutputs)) == null,
                "Resident pattern promised a catalyst it does not supply");
        var borrowedInputs = new ArrayList<>(fixture.catalysts);
        borrowedInputs.add(fixture.reagent);
        var borrowed = fixture.adapter.bind(h.getLevel(), fixture.pos,
                fixture.pattern(borrowedInputs, List.of(fixture.product)));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(borrowed != null && !fixture.adapter.canDispatch(h.getLevel(), fixture.pos, borrowed.handle()),
                "Borrowed mode accepted occupied resident pedestals");
        fixture.assertCatalysts();
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 200)
    public static void completedResidentProductAndBorrowedModeSwitchPreserveOwnership(GameTestHelper h)
            throws ReflectiveOperationException {
        var fixture = new Fixture(h);
        fixture.placeCatalysts();
        fixture.dispatch(List.of(fixture.reagent), List.of(fixture.product));
        fixture.finish();
        // Changing/removing resident catalysts after completion cannot block the
        // finished product or make the provider scavenge the replacement items.
        fixture.pedestal(0).setItem(0, ItemStack.EMPTY);
        var replacement = new ItemStack(Items.DIRT, 7);
        replacement.setHoverName(Component.literal("manual replacement"));
        fixture.pedestal(1).setItem(0, replacement.copy());
        fixture.reload();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.adapter.extractOutputs(h.getLevel(), fixture.pos, fixture.filter(), IActionSource.empty())
                .equals(List.of(fixture.product)), "Changed catalysts blocked resident product recovery");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.pedestal(0).getItem(0).isEmpty(), "Harvest restored removed catalyst");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(ItemStack.matches(replacement, fixture.pedestal(1).getItem(0)), "Harvest scavenged manual replacement");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(ItemStack.matches(fixture.catalystStacks.get(2), fixture.pedestal(2).getItem(0)),
                "Harvest scavenged unchanged resident catalyst");
        for (int i = 0; i < 3; i++) fixture.pedestal(i).clearContent();
        var inputs = new ArrayList<>(fixture.catalysts);
        inputs.add(fixture.reagent);
        var outputs = new ArrayList<GenericStack>();
        outputs.add(fixture.product);
        outputs.addAll(fixture.catalysts);
        fixture.dispatch(inputs, outputs);
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!fixture.chamber().getPersistentData().contains(RESIDENT_FLAG), "Borrowed dispatch retained resident flag");
        fixture.reload();
        fixture.finish();
        fixture.reload();
        var returned = fixture.adapter.extractOutputs(h.getLevel(), fixture.pos, fixture.filter(), IActionSource.empty());
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(returned.size() == outputs.size() && returned.containsAll(outputs), "Borrowed catalysts not returned: " + returned);
        for (int i = 0; i < 3; i++) com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.pedestal(i).isEmpty(), "Borrowed pedestal not emptied");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(fixture.adapter.extractOutputs(h.getLevel(), fixture.pos, fixture.filter(), IActionSource.empty()).isEmpty(),
                "Mode switch duplicated output");
        h.succeed();
    }

    private static final class Fixture {
        final GameTestHelper h;
        final BlockPos pos;
        final ArsNouveauImbuementChamberAdapter adapter = new ArsNouveauImbuementChamberAdapter();
        final List<BlockPos> pedestals;
        final List<ItemStack> catalystStacks;
        final List<GenericStack> catalysts;
        final GenericStack reagent = generic(item("ars_nouveau:source_gem"));
        final GenericStack product = generic(item("ars_nouveau:fire_essence"));

        Fixture(GameTestHelper h) {
            this.h = h;
            pos = h.absolutePos(new BlockPos(7, 2, 7));
            place(h, pos, "ars_nouveau:imbuement_chamber");
            pedestals = List.of(pos.offset(-1, 0, -1), pos.offset(1, 0, -1), pos.offset(0, 0, 1));
            pedestals.forEach(pedestal -> place(h, pedestal, "ars_nouveau:arcane_pedestal"));
            catalystStacks = List.of(named(Items.FLINT_AND_STEEL.getDefaultInstance(), "resident fire tool"),
                    named(new ItemStack(Items.TORCH, 3), "resident torches"),
                    named(new ItemStack(Items.GUNPOWDER, 5), "resident powder"));
            // Borrowed recipe inputs use one item per pedestal; keys retain NBT.
            catalysts = catalystStacks.stream().map(stack -> new GenericStack(AEItemKey.of(stack), 1)).toList();
        }

        Container pedestal(int index) { return (Container) h.getLevel().getBlockEntity(pedestals.get(index)); }
        BlockEntity chamber() { return h.getLevel().getBlockEntity(pos); }
        void placeCatalysts() {
            for (int i = 0; i < 3; i++) pedestal(i).setItem(0, catalystStacks.get(i).copy());
        }
        void assertCatalysts() {
            for (int i = 0; i < 3; i++) com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(ItemStack.matches(catalystStacks.get(i), pedestal(i).getItem(0)),
                    "Resident catalyst NBT/count changed at pedestal " + i);
        }
        IPatternDetails pattern(List<GenericStack> inputs, List<GenericStack> outputs) {
            return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    inputs.toArray(GenericStack[]::new), outputs.toArray(GenericStack[]::new)), h.getLevel());
        }
        AllowedOutputFilter filter() {
            var filter = new AllowedOutputFilter();
            filter.allowStrict(product.what());
            catalysts.forEach(catalyst -> filter.allowStrict(catalyst.what()));
            filter.allowIdOnly(AEItemKey.of(Items.DIRT.getDefaultInstance()));
            return filter;
        }
        void assertRejected(IPatternDetails pattern, Object handle, KeyCounter[] inputs) {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.bind(h.getLevel(), pos, pattern) == null, "Invalid catalysts bound");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!adapter.canDispatch(h.getLevel(), pos, handle), "Invalid catalysts passed canDispatch");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(adapter.planWithBinding(h.getLevel(), pos, pattern, inputs, handle, IActionSource.empty()) == null,
                    "Invalid catalysts produced a plan");
        }
        void dispatch(List<GenericStack> inputs, List<GenericStack> outputs) {
            var pattern = pattern(inputs, outputs);
            var binding = adapter.bind(h.getLevel(), pos, pattern);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(binding != null, "Pattern did not bind");
            var plan = adapter.planWithBinding(h.getLevel(), pos, pattern, counters(inputs), binding.handle(), IActionSource.empty());
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan != null && DispatchExecutor.execute(plan, IActionSource.empty(), new PatternProviderReturnInventory(() -> {}))
                    .success(), "Pattern did not dispatch");
        }
        void reload() {
            var original = chamber();
            var tag = original.saveWithFullMetadata();
            var replacement = BlockEntity.loadStatic(pos, original.getBlockState(), tag);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(replacement != null && replacement != original, "Machine was not reconstructed");
            h.getLevel().removeBlockEntity(pos);
            h.getLevel().setBlockEntity(replacement);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(original.getPersistentData().equals(replacement.getPersistentData()), "Forge lost persistent catalyst mode");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(ItemStack.matches(((Container) original).getItem(0), ((Container) replacement).getItem(0)),
                    "Forge reload changed chamber inventory");
        }
        void tick(int count) throws ReflectiveOperationException {
            var chamber = chamber();
            chamber.getClass().getMethod("setSource", int.class).invoke(chamber, 10000);
            var ticker = chamber.getClass().getMethod("tick");
            for (int i = 0; i < count; i++) ticker.invoke(chamber);
        }
        void finish() throws ReflectiveOperationException {
            for (int i = 0; i < 200 && !product.what().equals(AEItemKey.of(((Container) chamber()).getItem(0))); i++) tick(1);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(product.what().equals(AEItemKey.of(((Container) chamber()).getItem(0))), "Native Ars recipe did not finish");
        }
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.setHoverName(Component.literal(name));
        return stack;
    }
    private static ItemStack item(String id) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null) throw new IllegalStateException("Missing native item " + id);
        return item.getDefaultInstance();
    }
    private static GenericStack generic(ItemStack stack) { return new GenericStack(AEItemKey.of(stack), stack.getCount()); }
    private static KeyCounter[] counters(List<GenericStack> inputs) {
        return inputs.stream().map(input -> {
            var counter = new KeyCounter();
            counter.add(input.what(), input.amount());
            return counter;
        }).toArray(KeyCounter[]::new);
    }
    private static void place(GameTestHelper h, BlockPos pos, String id) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(block != null, "Missing native block " + id);
        h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
    }
}
