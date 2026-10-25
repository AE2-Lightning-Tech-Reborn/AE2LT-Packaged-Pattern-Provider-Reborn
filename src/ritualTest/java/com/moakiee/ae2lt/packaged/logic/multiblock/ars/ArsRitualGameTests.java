package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ArsRitualGameTests {
    private static final ArsRitualBrazierAdapter ADAPTER = new ArsRitualBrazierAdapter();
    private static final IActionSource SOURCE = IActionSource.empty();

    @GameTest(template = "chisel_test")
    public static void inputRulesRejectWrongMissingAndRepeatedAugments(GameTestHelper h) {
        var tile = brazier(h);
        for (var input : List.of(
                List.of(item("ritual_awakening", 1), stack(Items.DIAMOND, 1)),
                List.of(item("ritual_awakening", 2), stack(Items.WRITABLE_BOOK, 1)),
                List.of(stack(Items.WRITABLE_BOOK, 1)),
                List.of(item("ritual_warping", 1)),
                List.of(item("ritual_warping", 1), item("warp_scroll", 1)),
                List.of(item("ritual_scrying", 1), stack(Items.STONE, 2)))) {
            h.assertTrue(ADAPTER.bind(h.getLevel(), tile.getBlockPos(), pattern(h, input)) == null,
                    "invalid or incomplete native ritual is rejected: " + input);
        }
        h.assertTrue(ArsRitualAccess.field(tile, "ritual") == null, "validation never installs a ritual");
        h.succeed();
    }

    @GameTest(template = "chisel_test", timeoutTicks = 160)
    public static void unlistedNativeResultsReturnWithoutTakingNearbyItems(GameTestHelper h) {
        var tile = brazier(h);
        var loose = drop(h, tile, new ItemStack(Items.DIAMOND));
        dispatch(h, tile, List.of(item("ritual_awakening", 1), stack(Items.WRITABLE_BOOK, 2)));
        h.assertTrue(Boolean.TRUE.equals(ArsRitualAccess.call(ArsRitualAccess.field(tile, "ritual"), "isRunning")), "starts natively");
        var returned = new ArrayList<GenericStack>();
        h.succeedWhen(() -> {
            returned.addAll(ADAPTER.extractOutputs(h.getLevel(), tile.getBlockPos(), diamondFilter(), SOURCE));
            h.assertTrue(count(returned, item("bookwyrm_charm", 1).what()) == 2, "two real unlisted charms returned");
            h.assertTrue(loose.isAlive() && loose.getItem().is(Items.DIAMOND), "unrelated drop remains in world");
            h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), tile.getBlockPos(), diamondFilter(), SOURCE).isEmpty(), "returns exactly once");
        });
    }

    @GameTest(template = "chisel_test")
    public static void actualComponentsAndAmountsMustEqualBoundInputs(GameTestHelper h) {
        var tile = brazier(h);
        var book = new ItemStack(Items.WRITABLE_BOOK);
        book.set(DataComponents.CUSTOM_NAME, Component.literal("ritual book"));
        var declared = List.of(item("ritual_awakening", 1), new GenericStack(AEItemKey.of(book), 1));
        var pattern = pattern(h, declared);
        var binding = ADAPTER.bind(h.getLevel(), tile.getBlockPos(), pattern);
        h.assertTrue(binding != null, "named native augment accepted");
        for (var actual : List.of(List.of(item("ritual_awakening", 1), stack(Items.WRITABLE_BOOK, 1)),
                List.of(item("ritual_awakening", 1), new GenericStack(AEItemKey.of(book), 2)))) {
            h.assertTrue(ADAPTER.planWithBinding(h.getLevel(), tile.getBlockPos(), pattern, counters(actual), binding.handle(), SOURCE) == null,
                    "changed components or amount refused before payment");
        }
        dispatch(h, tile, declared);
        var consumed = (List<?>) ArsRitualAccess.call(ArsRitualAccess.field(tile, "ritual"), "getConsumedItems");
        h.assertTrue(consumed.size() == 1 && ItemStack.isSameItemSameComponents((ItemStack) consumed.getFirst(), book), "native consumed exact components");
        h.succeed();
    }

    @GameTest(template = "chisel_test")
    public static void manualAndRunningRitualsAreNeverOverwritten(GameTestHelper h) {
        var tile = brazier(h);
        var inputs = List.of(item("ritual_awakening", 1), stack(Items.WRITABLE_BOOK, 1));
        var pattern = pattern(h, inputs);
        var binding = ADAPTER.bind(h.getLevel(), tile.getBlockPos(), pattern);
        ArsRitualAccess.call(tile, "setRitual", new Class<?>[]{ResourceLocation.class}, id("ritual_scrying"));
        h.assertTrue(!ADAPTER.canDispatch(h.getLevel(), tile.getBlockPos(), binding.handle()), "pending manual ritual blocks dispatch");
        h.assertTrue(ADAPTER.planWithBinding(h.getLevel(), tile.getBlockPos(), pattern, counters(inputs), binding.handle(), SOURCE) == null, "no overwrite plan");
        h.succeed();
    }

    @GameTest(template = "chisel_test", timeoutTicks = 200)
    public static void paidRitualAndOutputQueueSurviveSaveReload(GameTestHelper h) {
        var tile = brazier(h);
        dispatch(h, tile, List.of(item("ritual_awakening", 1), stack(Items.WRITABLE_BOOK, 2)));
        var restored = reload(h, tile);
        h.assertTrue(Boolean.TRUE.equals(ArsRitualAccess.call(ArsRitualAccess.field(restored, "ritual"), "isRunning")), "native started state reloads");
        h.runAfterDelay(65, () -> {
            var reloaded = reload(h, restored);
            var results = ADAPTER.extractOutputs(h.getLevel(), reloaded.getBlockPos(), diamondFilter(), SOURCE);
            h.assertTrue(count(results, item("bookwyrm_charm", 1).what()) == 2, "owned outputs survive reload without duplicate charms");
            h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), reloaded.getBlockPos(), diamondFilter(), SOURCE).isEmpty(), "drain is persistent");
            h.succeed();
        });
    }

    @GameTest(template = "chisel_test", timeoutTicks = 180)
    public static void manuallyReplacingRitualReleasesCaptureOwnership(GameTestHelper h) {
        var tile = brazier(h);
        dispatch(h, tile, List.of(item("ritual_awakening", 1), stack(Items.WRITABLE_BOOK, 1)));
        ArsRitualAccess.call(tile, "setRitual", new Class<?>[]{ResourceLocation.class}, id("ritual_awakening"));
        ArsRitualAccess.call(tile, "tryBurnStack", new Class<?>[]{ItemStack.class}, new ItemStack(Items.WRITABLE_BOOK));
        ArsRitualAccess.call(tile, "startRitual", new Class<?>[]{net.minecraft.world.entity.player.Player.class}, (Object) null);
        h.runAfterDelay(45, () -> {
            h.assertTrue(ADAPTER.extractOutputs(h.getLevel(), tile.getBlockPos(), diamondFilter(), SOURCE).isEmpty(), "manual output not captured");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(tile.getBlockPos()).inflate(5));
            h.assertTrue(drops.stream().anyMatch(e -> item("bookwyrm_charm", 1).what().equals(AEItemKey.of(e.getItem()))), "manual charm still drops natively");
            h.succeed();
        });
    }

    @GameTest(template = "chisel_test", timeoutTicks = 120)
    public static void nativeSourceGateWaitsAndChargesJar(GameTestHelper h) {
        var tile = brazier(h);
        dispatch(h, tile, List.of(item("ritual_disintegration", 1)));
        var ritual = ArsRitualAccess.field(tile, "ritual");
        ArsRitualAccess.call(ritual, "setNeedsSource", new Class<?>[]{boolean.class}, true);
        h.runAfterDelay(25, () -> {
            h.assertTrue(Boolean.TRUE.equals(ArsRitualAccess.call(ritual, "needsSourceNow")), "no source is synthesized");
            var pos = tile.getBlockPos().east(2);
            h.getLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(id("source_jar")).defaultBlockState());
            var jar = h.getLevel().getBlockEntity(pos);
            ArsRitualAccess.call(jar, "setSource", new Class<?>[]{int.class}, 1000);
            h.runAfterDelay(25, () -> {
                h.assertTrue(!Boolean.TRUE.equals(ArsRitualAccess.call(ritual, "needsSourceNow")), "native gate resumes");
                h.assertTrue(((Number) ArsRitualAccess.call(jar, "getSource")).intValue() == 700, "native 300 source cost charged");
                h.succeed();
            });
        });
    }

    @GameTest(template = "chisel_test", timeoutTicks = 120)
    public static void breakingBrazierDropsBufferedRealResults(GameTestHelper h) {
        var tile = brazier(h);
        dispatch(h, tile, List.of(item("ritual_awakening", 1), stack(Items.WRITABLE_BOOK, 1)));
        h.runAfterDelay(45, () -> {
            h.getLevel().destroyBlock(tile.getBlockPos(), true);
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(tile.getBlockPos()).inflate(3));
            long charms = drops.stream().filter(e -> item("bookwyrm_charm", 1).what().equals(AEItemKey.of(e.getItem())))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(charms == 1, "exact buffered native charm drops on dismantle");
            h.succeed();
        });
    }

    @GameTest(template = "chisel_test")
    public static void redstonePauseBlocksPayment(GameTestHelper h) {
        var tile = brazier(h);
        var pattern = pattern(h, List.of(item("ritual_awakening", 1), stack(Items.WRITABLE_BOOK, 1)));
        var binding = ADAPTER.bind(h.getLevel(), tile.getBlockPos(), pattern);
        h.getLevel().setBlockAndUpdate(tile.getBlockPos().west(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        h.assertTrue(!ADAPTER.canDispatch(h.getLevel(), tile.getBlockPos(), binding.handle()), "powered brazier refuses input");
        h.succeed();
    }

    @GameTest(template = "chisel_test", timeoutTicks = 600)
    public static void realCpuCompletesThreeNativeRituals(GameTestHelper h) { cpu(h, false); }

    @GameTest(template = "chisel_test", timeoutTicks = 600)
    public static void wirelessCpuCompletesThreeNativeRituals(GameTestHelper h) { cpu(h, true); }

    private static void cpu(GameTestHelper h, boolean wireless) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(3, 2, 5));
        net.minecraft.world.level.block.Block providerBlock = wireless ? com.moakiee.ae2lt.packaged.registry.PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get()
                : com.moakiee.ae2lt.packaged.registry.PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        level.setBlockAndUpdate(pos, providerBlock.defaultBlockState());
        level.setBlockAndUpdate(pos.west(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(pos.north(), appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(pos.south(), appeng.core.definitions.AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) level.getBlockEntity(pos.north());
        drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_64K.stack());
        var provider = (com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        provider.setReturnMode(com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode.AUTO);
        provider.getAdapterInv().setItemDirect(0, com.moakiee.ae2lt.packaged.registry.PPItems.ARS_RITUAL_ADAPTER.toStack());
        var machine = pos.east(wireless ? 3 : 1);
        level.setBlockAndUpdate(machine, BuiltInRegistries.BLOCK.get(id("ritual_brazier")).defaultBlockState());
        if (wireless) h.assertTrue(provider.addOrUpdateConnection(level.dimension(), machine, net.minecraft.core.Direction.UP), "wireless ritual binding");
        var logic = (com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic) provider.getLogic();
        var inputs = List.of(item("ritual_awakening", 1), stack(Items.WRITABLE_BOOK, 2));
        var output = item("bookwyrm_charm", 2);
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(output)));
        h.runAfterDelay(70, () -> {
            logic.updatePatterns();
            var grid = provider.getMainNode().getGrid();
            var stock = grid.getStorageService().getInventory();
            var service = grid.getCraftingService();
            inputs.forEach(input -> h.assertTrue(stock.insert(input.what(), input.amount() * 3,
                    appeng.api.config.Actionable.MODULATE, SOURCE) == input.amount() * 3, "seed exact materials"));
            var future = service.beginCraftingCalculation(level, new appeng.api.networking.crafting.ICraftingSimulationRequester() {
                public IActionSource getActionSource() { return SOURCE; }
                public appeng.api.networking.IGridNode getGridNode() { return provider.getMainNode().getNode(); }
            }, output.what(), 6, appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            boolean[] submitted = {false};
            h.succeedWhen(() -> {
                h.assertTrue(future.isDone(), "real planner finished");
                if (!submitted[0]) {
                    try {
                        var plan = future.get();
                        h.assertTrue(!plan.simulation() && plan.missingItems().isEmpty(), "executable native ritual plan");
                        var submit = service.submitJob(plan, null, null, false, SOURCE);
                        h.assertTrue(submit.successful(), "real CPU accepted job");
                        submitted[0] = true;
                    } catch (Exception e) { throw new AssertionError(e); }
                }
                h.assertTrue(service.getCpus().stream().noneMatch(c -> c.isBusy()), "CPU waits for native rituals");
                h.assertTrue(stock.getAvailableStacks().get(output.what()) == 6, "six native charms delivered to ME");
                inputs.forEach(input -> h.assertTrue(stock.getAvailableStacks().get(input.what()) == 0, "exact materials consumed"));
                h.assertTrue(!service.isRequestingAny() && !logic.isBusy(), "no pending requests or input remainder");
            });
        });
    }

    @GameTest(template = "chisel_test", timeoutTicks = 500)
    public static void ritualCoreIsProducedByNativeApparatusRecipe(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(id("enchanting_apparatus")).defaultBlockState());
        level.setBlockAndUpdate(pos.below(), BuiltInRegistries.BLOCK.get(id("arcane_core")).defaultBlockState());
        for (var pedestal : List.of(pos.east(2), pos.west(2), pos.north(2), pos.south(2))) {
            level.setBlockAndUpdate(pedestal, BuiltInRegistries.BLOCK.get(id("arcane_pedestal")).defaultBlockState());
        }
        level.setBlockAndUpdate(pos.above(2), BuiltInRegistries.BLOCK.get(id("source_jar")).defaultBlockState());
        var jar = level.getBlockEntity(pos.above(2));
        ArsRitualAccess.call(jar, "setSource", new Class<?>[]{int.class}, 1000);
        var input = List.of(stack(com.moakiee.ae2lt.packaged.registry.PPItems.BASIC_PACKAGED_CORE.get(), 1),
                item("ritual_brazier", 1), item("source_gem", 3));
        var output = stack(com.moakiee.ae2lt.packaged.registry.PPItems.ARS_RITUAL_ADAPTER.get(), 1);
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
            h.assertTrue(returns.equals(List.of(output)), "native apparatus produced exactly one ritual core");
            h.assertTrue(((Number) ArsRitualAccess.call(jar, "getSource")).intValue() == 0, "core recipe consumed exactly 1000 source");
        });
    }

    @GameTest(template = "chisel_test", timeoutTicks = 120)
    public static void relayReturnsToOwningBrazierButCannotAcceptTablets(GameTestHelper h) throws Exception {
        var tile = brazier(h);
        var pos = tile.getBlockPos().east(3);
        h.getLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(id("brazier_relay")).defaultBlockState());
        var relay = h.getLevel().getBlockEntity(pos);
        h.assertTrue(relay != null && !ADAPTER.recognizesMain(h.getLevel(), pos, relay), "relay subclass is not a dispatch target");
        tile.getClass().getField("relayPos").set(tile, pos);
        dispatch(h, tile, List.of(item("ritual_awakening", 1), stack(Items.WRITABLE_BOOK, 1)));
        h.succeedWhen(() -> {
            var result = ADAPTER.extractOutputs(h.getLevel(), tile.getBlockPos(), diamondFilter(), SOURCE);
            h.assertTrue(result.equals(List.of(item("bookwyrm_charm", 1))), "native relay output returns through original brazier");
        });
    }

    private static BlockEntity brazier(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(4, 2, 4));
        h.getLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(id("ritual_brazier")).defaultBlockState());
        return h.getLevel().getBlockEntity(pos);
    }
    private static BlockEntity reload(GameTestHelper h, BlockEntity tile) {
        var tag = tile.saveWithFullMetadata(h.getLevel().registryAccess());
        h.getLevel().removeBlockEntity(tile.getBlockPos());
        var restored = BlockEntity.loadStatic(tile.getBlockPos(), tile.getBlockState(), tag, h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(restored);
        return restored;
    }
    private static ItemEntity drop(GameTestHelper h, BlockEntity tile, ItemStack stack) {
        var pos = tile.getBlockPos().getCenter();
        var drop = new ItemEntity(h.getLevel(), pos.x, pos.y + 1, pos.z, stack);
        drop.setNoGravity(true); drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        h.getLevel().addFreshEntity(drop); return drop;
    }
    private static void dispatch(GameTestHelper h, BlockEntity tile, List<GenericStack> inputs) {
        var pattern = pattern(h, inputs);
        var binding = ADAPTER.bind(h.getLevel(), tile.getBlockPos(), pattern);
        h.assertTrue(binding != null, "native input binds regardless of declared diamond output");
        var plan = ADAPTER.planWithBinding(h.getLevel(), tile.getBlockPos(), pattern, counters(inputs), binding.handle(), SOURCE);
        h.assertTrue(plan != null, "native input plans");
        var returned = new ArrayList<GenericStack>();
        h.assertTrue(DispatchExecutor.execute(plan, SOURCE, returned::addAll).success(), "dispatch paid once");
        h.assertTrue(returned.isEmpty(), "no input refunds after native start");
        h.assertTrue(!ADAPTER.canDispatch(h.getLevel(), tile.getBlockPos(), binding.handle()), "running ritual blocks another tablet");
    }
    private static IPatternDetails pattern(GameTestHelper h, List<GenericStack> inputs) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(stack(Items.DIAMOND, 1))), h.getLevel());
    }
    private static KeyCounter[] counters(List<GenericStack> inputs) {
        var counter = new KeyCounter(); inputs.forEach(s -> counter.add(s.what(), s.amount())); return new KeyCounter[]{counter};
    }
    private static AllowedOutputFilter diamondFilter() { var filter = new AllowedOutputFilter(); filter.allowStrict(AEItemKey.of(Items.DIAMOND)); return filter; }
    private static long count(List<GenericStack> stacks, appeng.api.stacks.AEKey key) { return stacks.stream().filter(s -> s.what().equals(key)).mapToLong(GenericStack::amount).sum(); }
    private static GenericStack stack(net.minecraft.world.level.ItemLike item, int count) { return new GenericStack(AEItemKey.of(item.asItem()), count); }
    private static GenericStack item(String name, int count) { return stack(BuiltInRegistries.ITEM.get(id(name)), count); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("ars_nouveau", path); }
}
