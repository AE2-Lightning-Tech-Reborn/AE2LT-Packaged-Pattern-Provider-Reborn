package com.moakiee.ae2lt.packaged.logic.multiblock.chisel;

import java.util.ArrayList;
import java.util.List;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BatchCopies;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.network.WirelessConnectorUsePacket;
import com.moakiee.ae2lt.logic.wireless.support.WirelessConnectorTargetHelper;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ChiselGameTests {
    @GameTest(template = "chisel_test")
    public static void oneCoreRegistrationAndNativeCrafting(GameTestHelper h) {
        String profile = System.getProperty("ae2ltpp.chiselTestProfile", "absent");
        boolean portable = profile.equals("reborn") || profile.equals("modern");
        h.assertTrue(loaded("chipped") == !profile.equals("absent"), "expected Chipped profile");
        h.assertTrue(loaded("rechiseled") == portable && loaded("chisel") == portable, "expected handheld profile");
        long active = MultiblockAdapterRegistry.activeAdapters().stream()
                .filter(a -> a instanceof HandheldChiselAdapter || a instanceof ChippedWorkbenchAdapter).count();
        h.assertTrue(active == (portable ? 3 : loaded("chipped") ? 1 : 0), "optional registrations");
        for (var mod : List.of("chipped", "rechiseled", "chisel")) {
            h.assertTrue(PackagedCoreDefinition.visibleWhen(mod::equals).stream()
                    .filter(c -> c.itemId().equals("chiseling_packaged_core")).count() == 1, "one visible core with " + mod);
        }
        h.assertTrue(PackagedCoreDefinition.visibleWhen(id -> false).stream()
                .noneMatch(c -> c.itemId().equals("chiseling_packaged_core")), "core hidden without targets");
        var recipe = h.getLevel().getRecipeManager().byKey(id("ae2ltpp:packaged_core/chiseling_packaged_core"));
        h.assertTrue(recipe.isPresent() == !profile.equals("absent"), "native OR condition on core recipe");
        for (var name : List.of("chipped:chisel", "rechiseled:chisel", "chisel:chisel", "chisel:iron_chisel",
                "chisel:diamond_chisel", "chisel:hitech_chisel")) {
            if (!BuiltInRegistries.ITEM.containsKey(id(name))) continue;
            var grid = CraftingInput.of(2, 1, List.of(PPItems.BASIC_PACKAGED_CORE.toStack(), item(name).getDefaultInstance()));
            var match = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel()).orElseThrow();
            var output = match.value().assemble(grid, h.getLevel().registryAccess());
            h.assertTrue(output.is(PPItems.CHISELING_ADAPTER.get()) && output.getCount() == 1, "shared core recipe for " + name);
        }
        System.out.println("PP_CHISEL_PROFILE_PASS " + profile + " adapters=" + active);
        h.succeed();
    }

    @GameTest(template = "chisel_test")
    public static void chippedAllBenchesRequireBothHalves(GameTestHelper h) {
        if (!loaded("chipped")) { h.succeed(); return; }
        var adapter = new ChippedWorkbenchAdapter();
        var pos = h.absolutePos(new BlockPos(4, 2, 4));
        var pattern = pattern(h, Items.STONE, 32, item("chipped:angry_stone"), 32);
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern) == null, "no physical bench means no binding");
        for (var bench : List.of("botanist_workbench", "glassblower", "carpenters_table", "loom_table",
                "mason_table", "alchemy_bench", "tinkering_table")) {
            placeBench(h, pos, bench);
            h.assertTrue(h.getLevel().getBlockEntity(pos) == null, "bench has no block entity");
            var bound = adapter.bind(h.getLevel(), pos, pattern);
            h.assertTrue(bound != null, "native shared recipes at " + bench);
            var output = adapter.planVirtualWithBinding(h.getLevel(), pos, pattern, counters(Items.STONE, 32), bound.handle(), null);
            h.assertTrue(output != null && output.outputs().getFirst().amount() == 32, "32:32 conversion at " + bench);
            h.assertTrue(!adapter.recognizesMain(h.getLevel(), pos.east(), null), "invisible half cannot create another lane");
            h.getLevel().setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
            h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(), pos, pattern, counters(Items.STONE, 32), bound.handle(), null) == null,
                    "cached conversion stops when side half removed");
            h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest(template = "chisel_test")
    public static void rejectForgedAmountsComponentsAndExtraInputs(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(4, 2, 4));
        if (loaded("chipped")) {
            placeBench(h, pos, "mason_table");
            rejectInvalid(h, new ChippedWorkbenchAdapter(), pos, Items.STONE, item("chipped:angry_stone"));
        }
        if (loaded("rechiseled")) rejectInvalid(h, new HandheldChiselAdapter(true), pos, Items.STONE, item("rechiseled:stone_big_tiles"));
        if (loaded("chisel")) rejectInvalid(h, new HandheldChiselAdapter(false), pos, chiselInput(), chiselOutput());
        h.succeed();
    }

    private static void rejectInvalid(GameTestHelper h, VirtualCraftingAdapter adapter, BlockPos pos, Item in, Item out) {
        var pattern = pattern(h, in, 8, out, 8);
        var bound = adapter.bind(h.getLevel(), pos, pattern);
        h.assertTrue(bound != null, "real group must bind for " + adapter.getClass().getSimpleName());
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern(h, in, 8, out, 9)) == null, "inflated output rejected");
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern(h, in, 8, Items.DIAMOND, 8)) == null, "unrelated output rejected");
        var forged = out.getDefaultInstance(); forged.set(DataComponents.CUSTOM_NAME, Component.literal("forged"));
        var fakePattern = decode(h, List.of(stack(in, 8)), List.of(new GenericStack(AEItemKey.of(forged), 8)));
        h.assertTrue(adapter.bind(h.getLevel(), pos, fakePattern) == null, "invented output components rejected");
        for (long amount : new long[] {0, 7, 9}) {
            h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(), pos, pattern, counters(in, amount), bound.handle(), null) == null,
                    "actual amount must equal pattern: " + amount);
        }
        var extra = counters(in, 8); extra[0].add(AEItemKey.of(Items.DIAMOND), 1);
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(), pos, pattern, extra, bound.handle(), null) == null, "extra item rejected");
        var fluid = counters(in, 8); fluid[0].add(AEFluidKey.of(Fluids.WATER), 1000);
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(), pos, pattern, fluid, bound.handle(), null) == null, "extra fluid rejected");
        var changed = in.getDefaultInstance(); changed.set(DataComponents.CUSTOM_NAME, Component.literal("different input"));
        var different = new KeyCounter(); different.add(AEItemKey.of(changed), 8);
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(), pos, pattern, new KeyCounter[] {different}, bound.handle(), null) == null,
                "runtime components must equal bound input");
        var overflow = new KeyCounter[] {counters(in, Long.MAX_VALUE)[0], counters(in, 1)[0]};
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(), pos, pattern, overflow, bound.handle(), null) == null, "input sum overflow rejected");
        var valid = counters(in, 8);
        var output = adapter.planVirtualWithBinding(h.getLevel(), pos, pattern, valid, bound.handle(), null);
        h.assertTrue(output != null && output.outputs().equals(List.of(stack(out, 8))) && valid[0].get(AEItemKey.of(in)) == 8,
                "valid input accepted without mutating borrowed counters");
    }

    @GameTest(template = "chisel_test")
    public static void rechiseledRatiosConnectedAndReverse(GameTestHelper h) {
        if (!loaded("rechiseled")) { h.succeed(); return; }
        var adapter = new HandheldChiselAdapter(true);
        var pos = h.absolutePos(new BlockPos(4, 2, 4));
        var connecting = item("rechiseled:stone_big_tiles_connecting");
        assertConversion(h, adapter, pos, Items.STONE, 8, connecting, 8);
        assertConversion(h, adapter, pos, connecting, 8, Items.STONE, 8);
        if (BuiltInRegistries.ITEM.containsKey(id("rechiseled:stone_big_tiles_slab"))) {
            var slab = item("rechiseled:stone_big_tiles_slab");
            assertConversion(h, adapter, pos, Items.STONE, 8, slab, 16);
            assertConversion(h, adapter, pos, slab, 16, Items.STONE, 8);
            h.assertTrue(adapter.bind(h.getLevel(), pos, pattern(h, slab, 3, Items.STONE, 1)) == null, "leftover slab rejected");
            h.assertTrue(adapter.bind(h.getLevel(), pos, pattern(h, Items.STONE, 8, slab, 24)) == null, "invalid worth ratio rejected");
        }
        h.succeed();
    }

    @GameTest(template = "chisel_test")
    public static void chiselNativeGroupsBothDirections(GameTestHelper h) {
        if (!loaded("chisel")) { h.succeed(); return; }
        var adapter = new HandheldChiselAdapter(false);
        var pos = h.absolutePos(new BlockPos(4, 2, 4));
        assertConversion(h, adapter, pos, chiselInput(), 64, chiselOutput(), 64);
        assertConversion(h, adapter, pos, chiselOutput(), 64, chiselInput(), 64);
        h.succeed();
    }

    @GameTest(template = "chisel_test", timeoutTicks = 120)
    public static void portableProviderNormalBatchAndPersistence(GameTestHelper h) { portableProvider(h, false); }

    @GameTest(template = "chisel_test", timeoutTicks = 120)
    public static void portableProviderWirelessWithoutTarget(GameTestHelper h) { portableProvider(h, true); }

    private static void portableProvider(GameTestHelper h, boolean wireless) {
        var be = provider(h, wireless);
        var logic = (PackagedPatternProviderLogic) be.getLogic();
        Item out = loaded("rechiseled") ? item("rechiseled:stone_big_tiles")
                : loaded("chipped") ? item("chipped:angry_stone") : Items.DIAMOND;
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(List.of(stack(Items.STONE, 1)), List.of(stack(out, 1))));
        h.runAfterDelay(45, () -> {
            logic.updatePatterns();
            var pattern = logic.getAvailablePatterns().getFirst();
            h.assertTrue(be.getConnections().isEmpty(), "portable conversion has no wireless targets");
            if (!loaded("rechiseled")) {
                h.assertTrue(logic.getBatchCapacity(pattern) == 0 && !logic.pushPattern(pattern, counters(Items.STONE, 1)),
                        "Chipped alone and absent mods never gain a portable lane");
                h.succeed(); return;
            }
            be.getAdapterInv().setItemDirect(0, ItemStack.EMPTY);
            h.assertTrue(logic.getBatchCapacity(pattern) == 0, "core still required without a target");
            be.getAdapterInv().setItemDirect(0, PPItems.CHISELING_ADAPTER.toStack());
            var input = counters(Items.STONE, 1);
            h.assertTrue(logic.getBatchCapacity(pattern) == 64, "one 64-copy internal lane");
            h.assertTrue(logic.pushBatch(pattern, input, 70) == 6, "accept 64, retain six at CPU");
            h.assertTrue(logic.getBatchCapacity(pattern) == 0 && !logic.pushPattern(pattern, BatchCopies.copy(input)), "shared single/batch lane limit");
            h.assertTrue(input[0].get(AEItemKey.of(Items.STONE)) == 1, "borrowed input unchanged");
            var saved = new CompoundTag(); logic.writeToNBT(saved, h.getLevel().registryAccess());
            var drops = new ArrayList<ItemStack>(); logic.addDrops(drops);
            h.assertTrue(count(drops, out) == 64, "all accepted products already owned");
            logic.clearContent(); logic.readFromNBT(saved, h.getLevel().registryAccess());
            drops.clear(); logic.addDrops(drops);
            h.assertTrue(count(drops, out) == 64, "pending products survive NBT reload exactly");
            logic.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
            h.assertTrue(logic.pushBatch(pattern, input, 8) == 8, "crafting lock blocks bulk path");
            logic.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
            logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
            h.assertTrue(logic.pushBatch(pattern, input, 8) == 8, "blocking blocks bulk path");
            System.out.println("PP_CHISEL_PORTABLE_PASS wireless=" + wireless + " copies=64 reload=exact");
            h.succeed();
        });
    }

    @GameTest(template = "chisel_test", timeoutTicks = 120)
    public static void chippedWirelessTargetRemovalStopsCachedCraft(GameTestHelper h) {
        if (!loaded("chipped")) { h.succeed(); return; }
        var be = provider(h, true);
        var logic = (PackagedPatternProviderLogic) be.getLogic();
        var target = h.absolutePos(new BlockPos(7, 2, 4));
        placeBench(h, target, "mason_table");
        h.assertTrue(be.addOrUpdateConnection(h.getLevel().dimension(), target, Direction.UP), "wireless target registered");
        var out = item("chipped:angry_stone");
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(List.of(stack(Items.STONE, 1)), List.of(stack(out, 1))));
        h.runAfterDelay(45, () -> {
            logic.updatePatterns();
            var pattern = logic.getAvailablePatterns().getFirst();
            h.assertTrue(logic.getBatchCapacity(pattern) == 64, "remote bench binds");
            h.getLevel().setBlockAndUpdate(target.east(), Blocks.AIR.defaultBlockState());
            h.assertTrue(logic.pushBatch(pattern, counters(Items.STONE, 1), 8) == 8, "removed bench half stops cached wireless plan");
            placeBench(h, target, "mason_table");
            h.assertTrue(logic.pushBatch(pattern, counters(Items.STONE, 1), 8) == 0, "restored bench accepts exactly eight");
            var drops = new ArrayList<ItemStack>(); logic.addDrops(drops);
            h.assertTrue(count(drops, out) == 8, "failed dispatch created no products");
            h.succeed();
        });
    }

    @GameTest(template = "chisel_test")
    public static void connectorRequiresMatchingCoreForEveryTarget(GameTestHelper h) throws Exception {
        if (!loaded("chipped")) { h.succeed(); return; }
        var be = provider(h, true);
        var target = h.absolutePos(new BlockPos(7, 2, 4));
        placeBench(h, target, "mason_table");
        be.getAdapterInv().setItemDirect(0, ItemStack.EMPTY);
        h.assertTrue(!be.addOrUpdateConnection(h.getLevel().dimension(), target, Direction.UP), "direct API requires installed core");
        connectorClick(h, be, target, false);
        h.assertTrue(be.getConnections().isEmpty(), "server connector rejects absent core on BE-less bench");
        be.getAdapterInv().setItemDirect(0, PPItems.OCCULTISM_SPIRIT_FIRE_ADAPTER.toStack());
        connectorClick(h, be, target, false);
        h.assertTrue(be.getConnections().isEmpty(), "server connector rejects mismatched core");
        be.getAdapterInv().setItemDirect(0, PPItems.CHISELING_ADAPTER.toStack());
        connectorClick(h, be, target, true);
        h.assertTrue(be.getConnections().size() == 1 && be.getConnections().getFirst().pos().equals(target), "native connector links main half only");
        h.assertTrue(be.clearInvalidConnections() == 0, "block-only target survives cleanup");
        be.getAdapterInv().setItemDirect(0, ItemStack.EMPTY);
        h.assertTrue(be.clearInvalidConnections() == 0 && be.getConnections().size() == 1, "temporary core removal preserves saved targets");
        be.getAdapterInv().setItemDirect(0, PPItems.CHISELING_ADAPTER.toStack());
        connectorClick(h, be, target, false);
        h.assertTrue(be.getConnections().isEmpty(), "native connector can toggle block-only link off");
        var chest = target.north(2);
        h.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        connectorClick(h, be, chest, false);
        h.assertTrue(be.getConnections().isEmpty(), "block entity alone is insufficient for packaged targets");
        h.assertTrue(WirelessConnectorTargetHelper.collectTargets(h.getLevel(), target, false).isEmpty(), "legacy scanner still requires block entity");
        h.assertTrue(WirelessConnectorTargetHelper.collectTargets(h.getLevel(), chest, false).contains(chest), "legacy ordinary machine scanner unchanged");
        System.out.println("PP_ADAPTER_CONNECTOR_PASS missing=denied mismatch=denied bench=linked chest=denied cleanup=retained");
        h.succeed();
    }

    @GameTest(template = "chisel_test", timeoutTicks = 130)
    public static void occultismSpiritFireLinksCraftsAndPrunes(GameTestHelper h) throws Exception {
        if (!loaded("occultism")) { h.succeed(); return; }
        var be = provider(h, true);
        var target = h.absolutePos(new BlockPos(7, 2, 4));
        h.getLevel().setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(target, BuiltInRegistries.BLOCK.get(id("occultism:spirit_fire")).defaultBlockState());
        h.assertTrue(h.getLevel().getBlockEntity(target) == null, "actual spirit fire has no block entity");
        connectorClick(h, be, target, false);
        h.assertTrue(be.getConnections().isEmpty(), "chiseling core cannot bind spirit fire");
        be.getAdapterInv().setItemDirect(0, ItemStack.EMPTY);
        connectorClick(h, be, target, false);
        h.assertTrue(be.getConnections().isEmpty(), "spirit fire also requires a core first");
        be.getAdapterInv().setItemDirect(0, PPItems.OCCULTISM_SPIRIT_FIRE_ADAPTER.toStack());
        connectorClick(h, be, target, false);
        h.assertTrue(be.getConnections().size() == 1 && be.clearInvalidConnections() == 0, "native spirit fire link survives cleanup");
        var logic = (PackagedPatternProviderLogic) be.getLogic();
        var out = item("occultism:otherstone");
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(List.of(stack(Items.ANDESITE, 1)), List.of(stack(out, 1))));
        h.runAfterDelay(45, () -> {
            logic.updatePatterns();
            var pattern = logic.getAvailablePatterns().getFirst();
            h.assertTrue(logic.pushBatch(pattern, counters(Items.ANDESITE, 1), 8) == 0, "native spirit fire recipe dispatches wirelessly");
            var drops = new ArrayList<ItemStack>(); logic.addDrops(drops);
            h.assertTrue(count(drops, out) == 8, "exact spirit fire output");
            h.getLevel().setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            h.assertTrue(be.clearInvalidConnections() == 1, "removed spirit fire is pruned");
            h.assertTrue(!logic.pushPattern(pattern, counters(Items.ANDESITE, 1)), "removed fire cannot craft via stale binding");
            System.out.println("PP_SPIRIT_FIRE_WIRELESS_PASS copies=8 removed=pruned");
            h.succeed();
        });
    }

    private static void connectorClick(GameTestHelper h, PackagedPatternProviderBlockEntity be, BlockPos target, boolean contiguous) throws Exception {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(java.util.UUID.randomUUID(), "PPConnectorTest"));
        player.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 1.5);
        var tool = item("ae2lt:overloaded_wireless_connect_tool").getDefaultInstance();
        OverloadedWirelessConnectorItem.selectHost(tool, h.getLevel(), be.getBlockPos(), OverloadedWirelessConnectorItem.HOST_PROVIDER);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        var packet = new WirelessConnectorUsePacket(InteractionHand.MAIN_HAND, target, Direction.UP, contiguous);
        var handle = WirelessConnectorUsePacket.class.getDeclaredMethod("handleOnServer", net.minecraft.server.level.ServerPlayer.class);
        handle.setAccessible(true);
        handle.invoke(packet, player);
    }

    private static PackagedPatternProviderBlockEntity provider(GameTestHelper h, boolean wireless) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        net.minecraft.world.level.block.Block block = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get() : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var be = (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(pos);
        be.getAdapterInv().setItemDirect(0, PPItems.CHISELING_ADAPTER.toStack());
        return be;
    }

    private static void placeBench(GameTestHelper h, BlockPos pos, String name) {
        var block = BuiltInRegistries.BLOCK.get(id("chipped:" + name));
        var main = block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        h.getLevel().setBlockAndUpdate(pos, withModel(main, "main"));
        h.getLevel().setBlockAndUpdate(pos.east(), withModel(main, "side"));
    }
    private static BlockState withModel(BlockState state, String name) {
        for (var property : state.getProperties()) if (property.getName().equals("model")) return withValue(state, property, name);
        throw new AssertionError("No workbench model property");
    }
    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String name) {
        return state.setValue(property, property.getValue(name).orElseThrow());
    }
    private static void assertConversion(GameTestHelper h, VirtualCraftingAdapter adapter, BlockPos pos,
            Item in, long inAmount, Item out, long outAmount) {
        var pattern = pattern(h, in, inAmount, out, outAmount);
        var bound = adapter.bind(h.getLevel(), pos, pattern);
        h.assertTrue(bound != null, "native conversion binds " + BuiltInRegistries.ITEM.getKey(in) + " -> " + BuiltInRegistries.ITEM.getKey(out));
        var result = adapter.planVirtualWithBinding(h.getLevel(), pos, pattern, counters(in, inAmount), bound.handle(), null);
        h.assertTrue(result != null && result.outputs().equals(List.of(stack(out, outAmount))), "native output and amount");
    }
    private static IPatternDetails pattern(GameTestHelper h, Item in, long inAmount, Item out, long outAmount) {
        return decode(h, List.of(stack(in, inAmount)), List.of(stack(out, outAmount)));
    }
    private static IPatternDetails decode(GameTestHelper h, List<GenericStack> in, List<GenericStack> out) {
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(in, out), h.getLevel());
        h.assertTrue(pattern != null, "native AE2 processing pattern decoded");
        return pattern;
    }
    private static GenericStack stack(Item item, long amount) { return new GenericStack(AEItemKey.of(item), amount); }
    private static KeyCounter[] counters(Item item, long amount) {
        var counter = new KeyCounter(); counter.add(AEItemKey.of(item), amount); return new KeyCounter[] {counter};
    }
    private static Item item(String name) {
        return BuiltInRegistries.ITEM.getOptional(id(name)).orElseThrow(() -> new AssertionError("Missing item " + name));
    }
    private static Item chiselInput() {
        return BuiltInRegistries.ITEM.containsKey(id("chisel:chisel")) ? Items.STONE : item("chisel:stone_bricks/cracked");
    }
    private static Item chiselOutput() {
        return item(BuiltInRegistries.ITEM.containsKey(id("chisel:chisel")) ? "chisel:array/stone" : "chisel:stone_bricks/array");
    }
    private static boolean loaded(String mod) { return ModList.get().isLoaded(mod); }
    private static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    private static long count(List<ItemStack> stacks, Item item) { return stacks.stream().filter(s -> s.is(item)).mapToLong(ItemStack::getCount).sum(); }
}
