package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.registry.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ArsScribesGameTests {
    private static final ArsScribesAdapter ADAPTER = new ArsScribesAdapter();
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final long COST = 740;
    private static GenericStack item(Item item, long amount) { return new GenericStack(AEItemKey.of(item), amount); }
    private static GenericStack xp(long amount) { return new GenericStack(AEFluidKey.of(Fluids.WATER), amount); }
    private static GenericStack glyph() { return item(BuiltInRegistries.ITEM.get(id("ars_nouveau:glyph_break")), 1); }
    private static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    private static List<GenericStack> inputs(long fluid) {
        var inputs = new ArrayList<>(List.of(item(Items.IRON_INGOT, 2), item(Items.PAPER, 1)));
        if (fluid > 0) inputs.add(xp(fluid));
        return inputs;
    }
    private static IPatternDetails pattern(GameTestHelper h, List<GenericStack> in, GenericStack out) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(in, List.of(out)), h.getLevel());
    }
    private static KeyCounter[] counters(List<GenericStack> in) { var k = new KeyCounter(); in.forEach(s -> k.add(s.what(), s.amount())); return new KeyCounter[]{k}; }
    private static BlockEntity table(GameTestHelper h) { return table(h, h.absolutePos(new BlockPos(4, 2, 4))); }
    private static BlockEntity table(GameTestHelper h, BlockPos head) {
        var block = BuiltInRegistries.BLOCK.get(id("ars_nouveau:scribes_table"));
        var state = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        var prop = state.getProperties().stream().filter(p -> p.getName().equals("part")).findFirst().orElseThrow();
        h.getLevel().setBlock(head.south(), part(state, prop, "foot"), 2);
        h.getLevel().setBlock(head, part(state, prop, "head"), 2);
        var tile = h.getLevel().getBlockEntity(head);
        h.assertTrue(tile instanceof ArsScribesMachine && ArsScribesAccess.head(h.getLevel(), head.south()) == tile, "complete native table and foot resolution");
        return tile;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState part(BlockState state, net.minecraft.world.level.block.state.properties.Property property, String name) {
        return state.setValue(property, (Comparable) property.getValue(name).orElseThrow());
    }
    private static void dispatch(GameTestHelper h, BlockPos target, List<GenericStack> inputs, GenericStack output, IActionSource source) {
        var pattern = pattern(h, inputs, output);
        var binding = ADAPTER.bind(h.getLevel(), target, pattern);
        h.assertTrue(binding != null, "native glyph recipe binds");
        var plan = ADAPTER.planWithBinding(h.getLevel(), target, pattern, counters(inputs), binding.handle(), source);
        h.assertTrue(plan != null, "paid native recipe plans");
        var refunds = new ArrayList<GenericStack>();
        h.assertTrue(DispatchExecutor.execute(plan, source, refunds::addAll).success() && refunds.isEmpty(), "one paid dispatch with no refunds");
    }

    @GameTest(template = "chisel_test")
    public static void requiresExactRecipeItemsOutputAndExperienceFluid(GameTestHelper h) {
        var table = table(h); var pos = table.getBlockPos();
        for (var in : List.of(List.of(item(Items.IRON_INGOT, 1), item(Items.PAPER, 1), xp(COST)),
                List.of(item(Items.IRON_INGOT, 3), item(Items.PAPER, 1), xp(COST)),
                List.of(item(Items.DIAMOND, 2), item(Items.PAPER, 1), xp(COST)),
                List.of(item(Items.IRON_INGOT, 2), item(Items.PAPER, 1), new GenericStack(AEFluidKey.of(Fluids.LAVA), COST)),
                inputs(COST + 1))) {
            h.assertTrue(ADAPTER.bind(h.getLevel(), pos, pattern(h, in, glyph())) == null, "invalid recipe input refused " + in);
        }
        h.assertTrue(ADAPTER.bind(h.getLevel(), pos, pattern(h, inputs(COST), item(Items.DIAMOND, 1))) == null, "wrong declared output rejected");
        h.assertTrue(((ArsScribesMachine) table).ae2ltpp$isScribeIdle(), "validation does not mutate table");
        h.succeed();
    }

    @GameTest(template = "chisel_test", timeoutTicks = 180)
    public static void footDispatchPreservesComponentsNativeTimerAndOwnedOutput(GameTestHelper h) throws Exception {
        var table = table(h);
        var iron = new ItemStack(Items.IRON_INGOT); iron.set(DataComponents.CUSTOM_NAME, Component.literal("scribe material"));
        var in = List.of(new GenericStack(AEItemKey.of(iron), 2), item(Items.PAPER, 1), xp(COST));
        var p = table.getBlockPos();
        var loose = new ItemEntity(h.getLevel(), p.getX() + .5, p.getY() + 1.1, p.getZ() + .5, ((AEItemKey) glyph().what()).toStack());
        loose.setNoGravity(true); loose.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); h.getLevel().addFreshEntity(loose);
        dispatch(h, p.south(), in, glyph(), SOURCE);
        var consumed = (List<?>) table.getClass().getField("consumedStacks").get(table);
        h.assertTrue(consumed.stream().filter(v -> ItemStack.isSameItemSameComponents((ItemStack) v, iron)).count() == 2, "actual material components preserved");
        h.runAfterDelay(100, () -> h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), p, new AllowedOutputFilter(), SOURCE).isEmpty(), "native 120 ticks not skipped"));
        h.runAfterDelay(140, () -> {
            h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), p.south(), new AllowedOutputFilter(), SOURCE).equals(List.of(glyph())), "one native glyph returned via foot");
            h.assertTrue(loose.isAlive(), "unrelated glyph drop untouched");
            h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), p, new AllowedOutputFilter(), SOURCE).isEmpty(), "no duplicate return");
            h.succeed();
        });
    }

    @GameTest(template = "chisel_test")
    public static void insufficientExperienceAndOccupiedTableDoNotConsumeInputs(GameTestHelper h) {
        var table = table(h); var pos = table.getBlockPos(); var in = inputs(COST - 1); var p = pattern(h, in, glyph());
        var binding = ADAPTER.bind(h.getLevel(), pos, p);
        h.assertTrue(binding != null && ADAPTER.planWithBinding(h.getLevel(), pos, p, counters(in), binding.handle(), SOURCE) == null, "one mB short blocks dispatch");
        ((net.minecraft.world.Container) table).setItem(0, new ItemStack(Items.BOOK));
        h.assertTrue(!ADAPTER.canDispatch(h.getLevel(), pos, binding.handle()), "book already on table blocks automation");
        ((net.minecraft.world.Container) table).clearContent();
        dispatch(h, pos, inputs(COST), glyph(), SOURCE);
        h.assertTrue(!ADAPTER.canDispatch(h.getLevel(), pos, binding.handle()), "running native recipe never overwritten");
        h.succeed();
    }

    @GameTest(template = "chisel_test", timeoutTicks = 220)
    public static void unresolvedRecipeAndBufferedResultSurviveReload(GameTestHelper h) {
        var table = table(h); dispatch(h, table.getBlockPos(), inputs(COST), glyph(), SOURCE);
        var loaded = reload(h, reload(h, table));
        h.assertTrue(!((ArsScribesMachine) loaded).ae2ltpp$isScribeIdle(), "paid recipe stays busy before first native tick");
        h.runAfterDelay(145, () -> {
            var outputReload = reload(h, loaded);
            h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), outputReload.getBlockPos(), new AllowedOutputFilter(), SOURCE).equals(List.of(glyph())), "one completed output survives save");
            h.assertTrue(((ArsScribesMachine) outputReload).ae2ltpp$isScribeIdle(), "lane free after drain");
            h.succeed();
        });
    }

    @GameTest(template = "chisel_test", timeoutTicks = 180)
    public static void zeroExperienceRecipeRequiresNoFluid(GameTestHelper h) {
        var table = table(h); var out = item(BuiltInRegistries.ITEM.get(id("ars_nouveau:glyph_touch")), 1);
        dispatch(h, table.getBlockPos(), List.of(item(Items.DIAMOND, 1)), out, SOURCE);
        h.runAfterDelay(140, () -> {
            h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), table.getBlockPos(), new AllowedOutputFilter(), SOURCE).equals(List.of(out)), "zero-XP native recipe completes");
            h.succeed();
        });
    }

    @GameTest(template = "chisel_test", timeoutTicks = 250)
    public static void networkPaysOnlyMissingFluid(GameTestHelper h) { network(h, 0); }
    @GameTest(template = "chisel_test", timeoutTicks = 250)
    public static void failedNetworkPaymentRollsBackBeforeNativeStart(GameTestHelper h) { network(h, 1); }
    @GameTest(template = "chisel_test", timeoutTicks = 250)
    public static void partialNetworkChargeIsRefundedExactly(GameTestHelper h) { network(h, 2); }
    private static void network(GameTestHelper h, int mode) {
        var table = table(h); var provider = provider(h, table.getBlockPos().west(), false); var stock = new Store();
        stock.stock.add(xp(COST - 140).what(), COST - 140);
        h.runAfterDelay(65, () -> {
            provider.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(m -> m.mount(stock, 0));
            var source = IActionSource.ofMachine(provider); var in = inputs(140); var p = pattern(h, in, glyph());
            var binding = ADAPTER.bind(h.getLevel(), table.getBlockPos(), p);
            var plan = ADAPTER.planWithBinding(h.getLevel(), table.getBlockPos(), p, counters(in), binding.handle(), source);
            h.assertTrue(plan != null, "network provides missing experience in simulation"); stock.reject = mode == 1; stock.limit = mode == 2 ? 200 : Long.MAX_VALUE;
            var ret = new ArrayList<GenericStack>(); var result = DispatchExecutor.execute(plan, source, ret::addAll);
            if (mode != 0) {
                h.assertTrue(result.failure() && ret.isEmpty() && ((ArsScribesMachine) table).ae2ltpp$isScribeIdle(), "full confirmed rollback leaves inputs with AE2");
                h.assertTrue(stock.stock.get(xp(1).what()) == COST - 140, "network XP unchanged on rejection"); h.succeed();
            } else {
                h.assertTrue(result.success() && ret.isEmpty() && stock.stock.get(xp(1).what()) == 0, "only 600 mB charged to network");
                h.runAfterDelay(140, () -> { h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), table.getBlockPos(), new AllowedOutputFilter(), source).equals(List.of(glyph())), "real output after paid native ticks"); h.succeed(); });
            }
        });
    }

    @GameTest(template = "chisel_test", timeoutTicks = 180)
    public static void breakingHeadPreservesCompletedOutput(GameTestHelper h) { breakOutput(h, false); }
    @GameTest(template = "chisel_test", timeoutTicks = 180)
    public static void breakingFootPreservesCompletedOutput(GameTestHelper h) { breakOutput(h, true); }
    private static void breakOutput(GameTestHelper h, boolean foot) {
        var table = table(h); dispatch(h, table.getBlockPos(), inputs(COST), glyph(), SOURCE);
        h.runAfterDelay(140, () -> {
            h.getLevel().destroyBlock(foot ? table.getBlockPos().south() : table.getBlockPos(), true);
            long count = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(table.getBlockPos()).inflate(2)).stream()
                    .filter(e -> glyph().what().equals(AEItemKey.of(e.getItem()))).mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(count == 1, "finished glyph drops exactly once when either half is broken"); h.succeed();
        });
    }

    @GameTest(template = "chisel_test")
    public static void unfinishedTeardownMatchesNativeItemsAndExperience(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(4, 2, 4));
        var nativeTable = table(h, pos);
        var recipe = h.getLevel().getRecipeManager().byKey(id("ae2ltpp:scribes_test")).orElseThrow();
        ((ArsScribesMachine) nativeTable).ae2ltpp$beginScribing(recipe,
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.PAPER)));
        nativeTable.getPersistentData().remove("ae2ltpp:scribe_paid"); // Native reference state with no packaged ownership.
        h.getLevel().destroyBlock(pos.south(), true);
        var nativeDrops = takeDrops(h, pos);
        h.assertTrue(nativeDrops.get(0) == 2 && nativeDrops.get(1) == 1, "native dismantling drops the actual paid ingredients");
        var paid = table(h, pos); dispatch(h, pos, inputs(COST), glyph(), SOURCE);
        h.getLevel().destroyBlock(pos.south(), true);
        var paidDrops = takeDrops(h, pos);
        h.assertTrue(nativeDrops.equals(paidDrops), "no extra XP or input refunds beyond native teardown: native=" + nativeDrops + " paid=" + paidDrops);
        h.succeed();
    }
    private static List<Long> takeDrops(GameTestHelper h, BlockPos pos) {
        long iron = 0, paper = 0, xp = 0;
        for (var e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
            if (e.getItem().is(Items.IRON_INGOT)) iron += e.getItem().getCount();
            if (e.getItem().is(Items.PAPER)) paper += e.getItem().getCount();
            e.discard();
        }
        for (var e : h.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(2))) { xp += (long) e.getValue() * (e instanceof com.moakiee.ae2lt.packaged.resource.ExperienceOrbAccess merged ? merged.ae2ltpp$getCount() : 1); e.discard(); }
        return List.of(iron, paper, xp);
    }

    @GameTest(template = "chisel_test", timeoutTicks = 700)
    public static void realCpuCraftsWithExplicitExperienceFluid(GameTestHelper h) { cpu(h, false); }
    @GameTest(template = "chisel_test", timeoutTicks = 700)
    public static void wirelessCpuCraftsWithNetworkExperienceFluid(GameTestHelper h) { cpu(h, true); }
    private static void cpu(GameTestHelper h, boolean wireless) {
        var pos = h.absolutePos(new BlockPos(3, 2, 5)); var p = provider(h, pos, wireless);
        var table = table(h, pos.east(wireless ? 3 : 1));
        p.getAdapterInv().setItemDirect(0, PPItems.ARS_SCRIBES_ADAPTER.toStack());
        if (wireless) h.assertTrue(p.addOrUpdateConnection(h.getLevel().dimension(), table.getBlockPos().south(), Direction.UP), "wireless foot binding");
        var logic = (PackagedPatternProviderLogic) p.getLogic();
        var in = inputs(wireless ? 0 : COST); var stock = new Store();
        inputs(COST).forEach(s -> stock.stock.add(s.what(), s.amount() * 3));
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(in, List.of(glyph())));
        h.runAfterDelay(70, () -> {
            var grid = p.getMainNode().getGrid(); grid.getStorageService().addGlobalStorageProvider(m -> m.mount(stock, 0)); logic.updatePatterns();
            var service = grid.getCraftingService(); var source = IActionSource.ofMachine(p);
            var future = service.beginCraftingCalculation(h.getLevel(), new appeng.api.networking.crafting.ICraftingSimulationRequester() {
                public IActionSource getActionSource() { return source; }
                public appeng.api.networking.IGridNode getGridNode() { return p.getMainNode().getNode(); }
            }, glyph().what(), 3, CalculationStrategy.REPORT_MISSING_ITEMS);
            boolean[] submitted = {false};
            h.succeedWhen(() -> {
                h.assertTrue(future.isDone(), "waiting for native planner");
                if (!submitted[0]) try {
                    var plan = future.get(); h.assertTrue(!plan.simulation() && plan.missingItems().isEmpty(), "executable glyph plan");
                    h.assertTrue(service.submitJob(plan, null, null, false, source).successful(), "real CPU accepts glyph order"); submitted[0] = true;
                } catch (Exception e) { throw new AssertionError(e); }
                h.assertTrue(service.getCpus().stream().noneMatch(c -> c.isBusy()) && stock.stock.get(glyph().what()) == 3, "three native glyphs returned");
                inputs(COST).forEach(s -> h.assertTrue(stock.stock.get(s.what()) == 0, "exact materials and 2220 mB experience charged"));
                h.assertTrue(!service.isRequestingAny() && !logic.isBusy(), "CPU and provider finish cleanly");
            });
        });
    }
    @GameTest(template = "chisel_test", timeoutTicks = 500)
    public static void scribeCoreIsProducedByNativeApparatusRecipe(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(id("ars_nouveau:enchanting_apparatus")).defaultBlockState());
        level.setBlockAndUpdate(pos.below(), BuiltInRegistries.BLOCK.get(id("ars_nouveau:arcane_core")).defaultBlockState());
        for (var pedestal : List.of(pos.east(2), pos.west(2), pos.north(2), pos.south(2))) {
            level.setBlockAndUpdate(pedestal, BuiltInRegistries.BLOCK.get(id("ars_nouveau:arcane_pedestal")).defaultBlockState());
        }
        level.setBlockAndUpdate(pos.above(2), BuiltInRegistries.BLOCK.get(id("ars_nouveau:source_jar")).defaultBlockState());
        var jar = level.getBlockEntity(pos.above(2));
        ArsRitualAccess.call(jar, "setSource", new Class<?>[]{int.class}, 1000);
        var input = List.of(item(com.moakiee.ae2lt.packaged.registry.PPItems.BASIC_PACKAGED_CORE.get(), 1),
                item(BuiltInRegistries.ITEM.get(id("ars_nouveau:scribes_table")), 1), item(BuiltInRegistries.ITEM.get(id("ars_nouveau:source_gem")), 3));
        var output = item(com.moakiee.ae2lt.packaged.registry.PPItems.ARS_SCRIBES_ADAPTER.get(), 1);
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(input, List.of(output)), level);
        var adapter = new ArsNouveauEnchantingApparatusAdapter();
        var binding = adapter.bind(level, pos, pattern);
        h.assertTrue(binding != null, "shipped core recipe matches real native apparatus");
        var plan = adapter.planWithBinding(level, pos, pattern, counters(input), binding.handle(), SOURCE);
        var returns = new ArrayList<GenericStack>();
        h.assertTrue(plan != null && DispatchExecutor.execute(plan, SOURCE, returns::addAll).success(), "paid core recipe dispatch");
        var filter = new AllowedOutputFilter(); filter.allowStrict(output.what());
        h.succeedWhen(() -> {
            returns.addAll(adapter.extractOutputs(level, pos, filter, SOURCE));
            h.assertTrue(returns.equals(List.of(output)), "native apparatus produced exactly one scribe core");
            h.assertTrue(((Number) ArsRitualAccess.call(jar, "getSource")).intValue() == 0, "core recipe consumed exactly 1000 source");
        });
    }

    private static PackagedPatternProviderBlockEntity provider(GameTestHelper h, BlockPos pos, boolean wireless) {
        Block block = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get() : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.north(), AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(pos); provider.setReturnMode(ReturnMode.AUTO); return provider;
    }
    private static BlockEntity reload(GameTestHelper h, BlockEntity tile) {
        var tag = tile.saveWithFullMetadata(h.getLevel().registryAccess()); h.getLevel().removeBlockEntity(tile.getBlockPos());
        var restored = BlockEntity.loadStatic(tile.getBlockPos(), tile.getBlockState(), tag, h.getLevel().registryAccess()); h.getLevel().setBlockEntity(restored); return restored;
    }
    private static final class Store implements MEStorage {
        final KeyCounter stock = new KeyCounter(); boolean reject; long limit = Long.MAX_VALUE;
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) { if (mode == Actionable.MODULATE) stock.add(key, amount); return amount; }
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long accepted = reject && mode == Actionable.MODULATE ? 0 : Math.min(Math.min(amount, stock.get(key)), mode == Actionable.MODULATE ? limit : Long.MAX_VALUE);
            if (mode == Actionable.MODULATE) stock.remove(key, accepted); return accepted;
        }
        public void getAvailableStacks(KeyCounter target) { target.addAll(stock); }
        public Component getDescription() { return Component.literal("Scribe test storage"); }
    }
}
