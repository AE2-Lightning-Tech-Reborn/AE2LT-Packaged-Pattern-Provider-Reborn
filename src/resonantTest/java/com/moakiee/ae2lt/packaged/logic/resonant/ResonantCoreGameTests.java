package com.moakiee.ae2lt.packaged.logic.resonant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import com.moakiee.ae2lt.packaged.blockentity.ExtendedPackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ProviderMode;
import appeng.capabilities.Capabilities;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import com.moakiee.ae2lt.packaged.item.ResonantPackagedCoreItem;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

@GameTestHolder("ae2ltpp_resonant")
@PrefixGameTestTemplate(false)
public final class ResonantCoreGameTests {
    private static final BlockPos SOURCE = new BlockPos(-800, 70, -800);
    private static final BlockPos FIRST = new BlockPos(2, 0, 0);
    private static final BlockPos SECOND = new BlockPos(0, 0, -2);

    @GameTest(template = "resonant_test")
    public static void registrationRecipeAndPortableComponentRoundTrip(GameTestHelper h) {
        boolean loaded = ModList.get().isLoaded("ae2cs");
        h.assertTrue(PackagedCoreDefinition.visibleWhen(id -> loaded && id.equals("ae2cs")).stream()
                .anyMatch(c -> c.itemId().equals("resonant_packaged_core")) == loaded, "optional core visibility");
        var recipe = h.getLevel().getRecipeManager().byKey(id("ae2ltpp:packaged_core/resonant_packaged_core"));
        h.assertTrue(recipe.isPresent() == loaded, "conditional recipe follows real AE2CS presence");
        if (loaded) {
            var grid = new TransientCraftingContainer(new AbstractContainerMenu(null, -1) {
                @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
                @Override public boolean stillValid(Player player) { return true; }
            }, 2, 1);
            grid.setItem(0, new ItemStack(PPItems.BASIC_PACKAGED_CORE.get()));
            grid.setItem(1, BuiltInRegistries.ITEM.get(id("ae2cs:resonating_pattern_provider")).getDefaultInstance());
            var match = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel()).orElseThrow();
            h.assertTrue(match.assemble(grid, h.getLevel().registryAccess()).is(PPItems.RESONANT_ADAPTER.get()), "native core crafting");
        }
        var layout = layout(h).withTarget(143, target(FIRST, Direction.DOWN, "minecraft:chest"))
                .withTarget(179, target(SECOND, Direction.UP, "minecraft:chest")).select(179);
        var stack = core(layout);
        var loadedStack = ItemStack.of(stack.save(new CompoundTag()));
        h.assertTrue(layout.equals(ResonantPackagedCoreItem.layout(loadedStack)), "layout NBT survives inventory save/reload");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeItem(stack);
        var clientStack = buffer.readItem();
        buffer.release();
        h.assertTrue(layout.equals(ResonantPackagedCoreItem.layout(clientStack)), "client sees the same portable layout");
        h.succeed();
    }

    @GameTest(template = "resonant_test")
    public static void bindSelectToggleCopyAndResetThroughItemUse(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ResonantBind"));
        var held = new ItemStack(PPItems.RESONANT_ADAPTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        var anchor = h.absolutePos(new BlockPos(5, 2, 5));
        furnace(h, anchor, Direction.NORTH);
        var port = chest(h, anchor.offset(FIRST));
        click(player, anchor, Direction.WEST);
        var initial = ResonantPackagedCoreItem.layout(held);
        h.assertTrue(initial != null && initial.source().pos().equals(anchor), "first click records source");
        click(player, port.getBlockPos(), Direction.UP);
        h.assertTrue(ResonantPackagedCoreItem.layout(held).target(0).orElseThrow().offset().equals(FIRST), "relative input captured");
        click(player, port.getBlockPos(), Direction.UP);
        h.assertTrue(ResonantPackagedCoreItem.layout(held).target(0).isEmpty(), "same port toggles off");
        player.setShiftKeyDown(true);
        ResonantPackagedCoreItem.cycle(player, true);
        player.setShiftKeyDown(false);
        click(player, port.getBlockPos(), Direction.DOWN);
        h.assertTrue(ResonantPackagedCoreItem.layout(held).target(1).isPresent(), "server scroll selects original input slot");
        held.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        click(player, port.getBlockPos(), Direction.UP);
        h.assertTrue(ResonantPackagedCoreItem.layout(held).target(ResonantLayout.INPUTS).isPresent(), "air use changes to output bindings");
        var provider = provider(h, true);
        provider.getAdapterInv().setItemDirect(0, held.copy());
        var copy = new ItemStack(PPItems.RESONANT_ADAPTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, copy);
        click(player, provider.getBlockPos(), Direction.NORTH);
        h.assertTrue(ResonantPackagedCoreItem.layout(copy).equals(ResonantPackagedCoreItem.layout(held)), "copy includes original source anchor");
        player.setShiftKeyDown(true);
        click(player, port.getBlockPos(), Direction.NORTH);
        var reset = ResonantPackagedCoreItem.layout(copy);
        h.assertTrue(reset.bindings().isEmpty() && reset.source().pos().equals(port.getBlockPos()), "explicit rebind clears old layout");
        h.succeed();
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void ordinarySparseDuplicateInputsUseDifferentPorts(GameTestHelper h) {
        var be = provider(h, false);
        var anchor = be.getBlockPos().east();
        furnace(h, anchor, Direction.NORTH);
        var first = chest(h, anchor.offset(FIRST));
        var second = chest(h, anchor.offset(SECOND));
        var config = layout(h).withTarget(0, target(FIRST, Direction.UP, "minecraft:chest"))
                .withTarget(2, target(SECOND, Direction.UP, "minecraft:chest"));
        be.getAdapterInv().setItemDirect(0, core(config));
        var sparse = Arrays.asList(stack(Items.IRON_INGOT, 2), null, stack(Items.IRON_INGOT, 3));
        arm(h, be, ordinary(sparse), () -> {
            var logic = logic(be);
            h.assertTrue(logic.getBatchCapacity(pattern(be)) == 1, "routed craft has one physical admission at a time");
            var supplied = counters(sparse);
            h.assertTrue(logic.pushBatch(pattern(be), supplied, 7) == 6, "batch acknowledges exactly one complete layout");
            h.assertTrue(count(first, Items.IRON_INGOT) == 2 && count(second, Items.IRON_INGOT) == 3, "duplicate key keeps sparse routing");
            h.assertTrue(supplied[0].get(AEItemKey.of(Items.IRON_INGOT)) == 5, "borrowed CPU inputs unchanged");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void wirelessChoosesOneCompleteLayoutAndRotatesFaces(GameTestHelper h) {
        var be = provider(h, true);
        var a = h.absolutePos(new BlockPos(5, 2, 5));
        var b = h.absolutePos(new BlockPos(11, 2, 5));
        furnace(h, a, Direction.NORTH);
        furnace(h, b, Direction.EAST);
        var aFirst = itemPort(h, a.offset(FIRST), Direction.WEST, new ItemStackHandler(1));
        var aSecond = new ItemStackHandler(1);
        aSecond.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
        itemPort(h, a.offset(SECOND), Direction.UP, aSecond);
        var bFirst = itemPort(h, b.offset(0, 0, 2), Direction.NORTH, new ItemStackHandler(1));
        var bSecond = itemPort(h, b.offset(2, 0, 0), Direction.UP, new ItemStackHandler(1));
        var config = layout(h).withTarget(0, target(FIRST, Direction.WEST, "minecraft:beacon"))
                .withTarget(1, target(SECOND, Direction.UP, "minecraft:beacon"));
        be.getAdapterInv().setItemDirect(0, core(config));
        h.assertTrue(be.addOrUpdateConnection(h.getLevel().dimension(), a, Direction.NORTH), "first anchor links");
        h.assertTrue(be.addOrUpdateConnection(h.getLevel().dimension(), b, Direction.NORTH), "rotated anchor links");
        var inputs = List.of(stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 2));
        arm(h, be, ordinary(inputs), () -> {
            h.assertTrue(logic(be).pushPattern(pattern(be), counters(inputs)), "second complete layout accepts");
            h.assertTrue(aFirst.getStackInSlot(0).isEmpty(), "blocked layout gets none of the ingredients");
            h.assertTrue(bFirst.getStackInSlot(0).is(Items.IRON_INGOT) && bSecond.getStackInSlot(0).getCount() == 2,
                    "offsets and sided capabilities rotate together into a single layout");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void partialReceiptReloadAndCoreSwapKeepOriginalDestination(GameTestHelper h) {
        var be = provider(h, true);
        var anchor = h.absolutePos(new BlockPos(5, 2, 5));
        furnace(h, anchor, Direction.NORTH);
        var partial = new ResonantTestPorts.PartialHandler(3);
        itemPort(h, anchor.offset(FIRST), Direction.UP, partial);
        var second = itemPort(h, anchor.offset(SECOND), Direction.UP, new ItemStackHandler(1));
        var layout = layout(h).withTarget(0, target(FIRST, Direction.UP, "minecraft:beacon"))
                .withTarget(1, target(SECOND, Direction.UP, "minecraft:beacon"));
        be.getAdapterInv().setItemDirect(0, core(layout));
        be.addOrUpdateConnection(h.getLevel().dimension(), anchor, Direction.NORTH);
        var inputs = List.of(stack(Items.IRON_INGOT, 8), stack(Items.GOLD_INGOT, 2));
        arm(h, be, ordinary(inputs), () -> {
            var logic = logic(be);
            h.assertTrue(logic.pushPattern(pattern(be), counters(inputs)), "partial native receipt still owns one CPU craft");
            h.assertTrue(partial.getStackInSlot(0).getCount() == 3 && second.getStackInSlot(0).getCount() == 2, "real accepted receipts");
            h.assertTrue(logic.isBusy() && !logic.pushPattern(pattern(be), counters(inputs)), "pending tail blocks new craft");
            var saved = new CompoundTag(); logic.writeToNBT(saved);
            var drops = new ArrayList<ItemStack>(); be.addAdditionalDrops(h.getLevel(), be.getBlockPos(), drops);
            h.assertTrue(count(drops, Items.IRON_INGOT) == 5 && count(drops, Items.GOLD_INGOT) == 0, "break drops exact unpaid tail only");
            var repeatedDrops = new ArrayList<ItemStack>(); logic.addDrops(repeatedDrops);
            h.assertTrue(!logic.isBusy() && count(repeatedDrops, Items.IRON_INGOT) == 0,
                    "block drop hands off pending ownership exactly once");
            logic.clearContent(); logic.readFromNBT(saved);
            be.getAdapterInv().setItemDirect(0, core(layout.withTarget(0, target(SECOND, Direction.UP, "minecraft:beacon"))));
            h.getLevel().setBlockAndUpdate(anchor.offset(FIRST), Blocks.STONE.defaultBlockState());
            partial.budget = 20;
            logic.tickAutoReturn();
            h.assertTrue(partial.getStackInSlot(0).getCount() == 3 && logic.isBusy(), "different block pauses old delivery");
            h.getLevel().setBlockAndUpdate(anchor.offset(FIRST), Blocks.BEACON.defaultBlockState());
            logic.tickAutoReturn();
            h.assertTrue(partial.getStackInSlot(0).getCount() == 8 && second.getStackInSlot(0).getCount() == 2,
                    "reload and changed core do not redirect or repeat accepted inputs");
            drops.clear(); logic.addDrops(drops);
            h.assertTrue(count(drops, Items.IRON_INGOT) == 0 && !logic.isBusy(), "completed input tail is removed");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void throwingInsertionNeverDuplicatesConfirmedInputs(GameTestHelper h) {
        var be = provider(h, true);
        var anchor = h.absolutePos(new BlockPos(5, 2, 5));
        furnace(h, anchor, Direction.NORTH);
        var broken = new ResonantTestPorts.PartialHandler(1); broken.throwAfterInsert = true;
        itemPort(h, anchor.offset(FIRST), Direction.UP, broken);
        var later = itemPort(h, anchor.offset(SECOND), Direction.UP, new ItemStackHandler(1));
        be.getAdapterInv().setItemDirect(0, core(layout(h).withTarget(0, target(FIRST, Direction.UP, "minecraft:beacon"))
                .withTarget(1, target(SECOND, Direction.UP, "minecraft:beacon"))));
        be.addOrUpdateConnection(h.getLevel().dimension(), anchor, Direction.NORTH);
        var inputs = List.of(stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 2));
        arm(h, be, ordinary(inputs), () -> {
            var logic = logic(be);
            h.assertTrue(logic.pushPattern(pattern(be), counters(inputs)), "exception after acceptance cannot refund the CPU");
            h.assertTrue(broken.getStackInSlot(0).getCount() == 1 && later.getStackInSlot(0).isEmpty(), "injected failure leaves unvisited tail");
            logic.tickAutoReturn();
            var drops = new ArrayList<ItemStack>(); logic.addDrops(drops);
            h.assertTrue(broken.getStackInSlot(0).getCount() == 1 && later.getStackInSlot(0).getCount() == 2
                    && count(drops, Items.IRON_INGOT) == 0, "unknown receipt is not retried or refunded; untouched tail resumes");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void sharedEmptySlotCannotBeReservedByTwoIngredients(GameTestHelper h) {
        var be = provider(h, false);
        var anchor = be.getBlockPos().east(); furnace(h, anchor, Direction.NORTH);
        var chest = chest(h, anchor.offset(FIRST));
        for (int slot = 0; slot < 26; slot++) chest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        var config = layout(h).withTarget(0, target(FIRST, Direction.UP, "minecraft:chest"))
                .withTarget(1, target(FIRST, Direction.UP, "minecraft:chest"));
        be.getAdapterInv().setItemDirect(0, core(config));
        var inputs = List.of(stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 1));
        arm(h, be, ordinary(inputs), () -> {
            h.assertTrue(!logic(be).pushPattern(pattern(be), counters(inputs)), "joint preflight refuses overlapping slot reservations");
            h.assertTrue(count(chest, Items.IRON_INGOT) == 0 && count(chest, Items.GOLD_INGOT) == 0 && !logic(be).isBusy(),
                    "preflight has no world or provider ownership effects");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void mixedFluidInputsAndExplicitOutputReturn(GameTestHelper h) {
        var be = provider(h, true);
        var anchor = h.absolutePos(new BlockPos(5, 2, 5)); furnace(h, anchor, Direction.NORTH);
        var input = itemPort(h, anchor.offset(FIRST), Direction.UP, new ItemStackHandler(1));
        var fluid = new FluidTank(4000); fluidPort(h, anchor.offset(SECOND), Direction.DOWN, fluid);
        var output = itemPort(h, anchor.above(2), Direction.UP, new ItemStackHandler(2));
        var fluidOutput = new FluidTank(4000); fluidPort(h, anchor.below(), Direction.DOWN, fluidOutput);
        var config = layout(h).withTarget(0, target(FIRST, Direction.UP, "minecraft:beacon"))
                .withTarget(1, target(SECOND, Direction.DOWN, "minecraft:comparator"))
                .withTarget(ResonantLayout.INPUTS, target(new BlockPos(0, 2, 0), Direction.UP, "minecraft:beacon"))
                .withTarget(ResonantLayout.INPUTS + 1, target(new BlockPos(0, -1, 0), Direction.DOWN, "minecraft:comparator"));
        be.getAdapterInv().setItemDirect(0, core(config));
        be.addOrUpdateConnection(h.getLevel().dimension(), anchor, Direction.NORTH);
        var inputs = List.of(stack(Items.IRON_INGOT, 2), new GenericStack(AEFluidKey.of(Fluids.WATER), 1000));
        var outputs = List.of(stack(Items.DIAMOND, 1), new GenericStack(AEFluidKey.of(Fluids.LAVA), 500));
        arm(h, be, PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new), outputs.toArray(GenericStack[]::new)), () -> {
            var logic = logic(be);
            h.assertTrue(logic.pushPattern(pattern(be), counters(inputs)), "native item and fluid routing accepts");
            h.assertTrue(input.getStackInSlot(0).getCount() == 2 && fluid.getFluidAmount() == 1000, "exact physical material receipts");
            output.setStackInSlot(0, new ItemStack(Items.DIAMOND, 1));
            output.setStackInSlot(1, new ItemStack(Items.DIRT, 7));
            fluidOutput.setFluid(AEFluidKey.of(Fluids.LAVA).toStack(500));
            logic.tickAutoReturn();
            h.assertTrue(output.getStackInSlot(0).getCount() == 1 && fluidOutput.getFluidAmount() == 500, "OFF does not pull outputs");
            be.setReturnMode(ReturnMode.AUTO);
            logic.tickAutoReturn();
            h.assertTrue(output.getStackInSlot(0).isEmpty() && output.getStackInSlot(1).getCount() == 7
                    && fluidOutput.isEmpty(), "AUTO extracts only declared outputs from explicit ports");
            var saved = new CompoundTag(); logic.writeToNBT(saved);
            var drops = new ArrayList<ItemStack>(); logic.addDrops(drops);
            h.assertTrue(count(drops, Items.DIAMOND) == 1 && saved.toString().contains("lava"),
                    "collected item and fluid are owned before network delivery");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void sharedFluidTankIsReservedAcrossDifferentFluids(GameTestHelper h) {
        var be = provider(h, false);
        var anchor = be.getBlockPos().east(); furnace(h, anchor, Direction.NORTH);
        var tank = new FluidTank(4000); fluidPort(h, anchor.offset(FIRST), Direction.UP, tank);
        be.getAdapterInv().setItemDirect(0, core(layout(h)
                .withTarget(0, target(FIRST, Direction.UP, "minecraft:comparator"))
                .withTarget(1, target(FIRST, Direction.UP, "minecraft:comparator"))));
        var inputs = List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000), new GenericStack(AEFluidKey.of(Fluids.LAVA), 1000));
        arm(h, be, ordinary(inputs), () -> {
            h.assertTrue(!logic(be).pushPattern(pattern(be), counters(inputs)) && tank.isEmpty(), "different fluids cannot reserve the same empty tank");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void craftingLocksAndBlockingApplyToRoutedInputs(GameTestHelper h) {
        var be = provider(h, false);
        var anchor = be.getBlockPos().east(); furnace(h, anchor, Direction.NORTH);
        var chest = chest(h, anchor.offset(FIRST));
        be.getAdapterInv().setItemDirect(0, core(layout(h).withTarget(0, target(FIRST, Direction.UP, "minecraft:chest"))));
        var inputs = List.of(stack(Items.IRON_INGOT, 1));
        arm(h, be, ordinary(inputs), () -> {
            var logic = logic(be);
            logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
            chest.setItem(0, new ItemStack(Items.IRON_INGOT, 1));
            h.assertTrue(!logic.pushPattern(pattern(be), counters(inputs)), "blocking inspects actual routed target");
            chest.clearContent();
            logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.NO);
            logic.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
            h.assertTrue(logic.pushPattern(pattern(be), counters(inputs)), "first craft locks only after acceptance");
            h.assertTrue(!logic.pushPattern(pattern(be), counters(inputs)) && logic.getBatchCapacity(pattern(be)) == 0, "lock applies to normal and batch path");
            logic.resetCraftingLock();
            h.assertTrue(logic.pushPattern(pattern(be), counters(inputs)), "manual unlock allows another routed craft");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test")
    public static void wirelessLinksSurviveCoreRemovalAndPersistence(GameTestHelper h) {
        var be = provider(h, true);
        var anchor = h.absolutePos(new BlockPos(5, 2, 5)); furnace(h, anchor, Direction.NORTH);
        h.assertTrue(!be.addOrUpdateConnection(h.getLevel().dimension(), anchor, Direction.NORTH), "core required");
        be.getAdapterInv().setItemDirect(0, core(layout(h)));
        h.assertTrue(be.addOrUpdateConnection(h.getLevel().dimension(), anchor, Direction.NORTH), "configured anchor links");
        be.getAdapterInv().setItemDirect(0, ItemStack.EMPTY);
        h.assertTrue(be.clearInvalidConnections() == 0 && be.getConnections().size() == 1, "removing core preserves original link");
        var saved = new CompoundTag(); saved = be.saveWithoutMetadata();
        be.removeConnection(h.getLevel().dimension(), anchor);
        be.load(saved);
        h.assertTrue(be.getConnections().size() == 1 && be.clearInvalidConnections() == 0, "recorded anchor type survives reload without core");
        h.getLevel().setBlockAndUpdate(anchor, Blocks.STONE.defaultBlockState());
        h.assertTrue(be.clearInvalidConnections() == 1, "replaced anchor invalidates link");
        h.succeed();
    }

    @GameTest(template = "resonant_test")
    public static void unavailableTargetsAndMalformedInputsNeverAcquireOwnership(GameTestHelper h) {
        var anchorPos = h.absolutePos(new BlockPos(5, 2, 5)); furnace(h, anchorPos, Direction.NORTH);
        chest(h, anchorPos.offset(FIRST));
        var anchor = new ResonantDispatch.Anchor(h.getLevel(), anchorPos, Direction.NORTH);
        var layout = layout(h).withTarget(0, target(FIRST, Direction.UP, "minecraft:chest"));
        var inputs = List.of(stack(Items.IRON_INGOT, 2));
        var pattern = PatternDetailsHelper.decodePattern(ordinary(inputs), h.getLevel());
        var data = ResonantPatternAccess.read(pattern);
        var providerPos = anchorPos.west();
        h.assertTrue(ResonantDispatch.plan(layout, data, counters(inputs), anchor, providerPos) != null, "valid plan available");
        h.assertTrue(ResonantDispatch.plan(layout, data, counters(List.of(stack(Items.IRON_INGOT, 3))), anchor, providerPos) == null, "extra input rejected");
        h.assertTrue(ResonantDispatch.plan(layout, data, counters(List.of(stack(Items.IRON_INGOT, 1))), anchor, providerPos) == null, "missing input rejected");
        var named = new ItemStack(Items.IRON_INGOT); named.setHoverName(Component.literal("different"));
        h.assertTrue(ResonantDispatch.plan(layout, data, counters(List.of(new GenericStack(AEItemKey.of(named), 2))), anchor, providerPos) == null,
                "input NBT preserved");
        var distant = layout.withTarget(0, target(new BlockPos(200000, 0, 200000), Direction.UP, "minecraft:chest"));
        var far = anchorPos.offset(200000, 0, 200000);
        h.assertTrue(!h.getLevel().isLoaded(far), "far destination begins unloaded");
        h.assertTrue(ResonantDispatch.plan(distant, data, counters(inputs), anchor, providerPos) == null && !h.getLevel().isLoaded(far),
                "planning never loads destination chunks");
        h.getLevel().setBlockAndUpdate(anchorPos.offset(FIRST), Blocks.BARREL.defaultBlockState());
        h.assertTrue(ResonantDispatch.plan(layout, data, counters(inputs), anchor, providerPos) == null, "wrong destination type refused");
        h.succeed();
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void nativeCrystalScienceMarksOverrideDefaultsAndRebase(GameTestHelper h) {
        if (!ModList.get().isLoaded("ae2cs")) { h.succeed(); return; }
        var be = provider(h, true);
        var anchor = h.absolutePos(new BlockPos(7, 2, 7)); furnace(h, anchor, Direction.NORTH);
        var explicit = chest(h, anchor.offset(FIRST));
        var defaultPort = chest(h, anchor.offset(SECOND));
        // Slot 0's core default deliberately disagrees with the explicit CS mark.
        be.getAdapterInv().setItemDirect(0, core(layout(h)
                .withTarget(0, target(SECOND, Direction.UP, "minecraft:chest"))
                .withTarget(2, target(SECOND, Direction.UP, "minecraft:chest"))));
        be.addOrUpdateConnection(h.getLevel().dimension(), anchor, Direction.NORTH);
        var inputs = Arrays.asList(stack(Items.IRON_INGOT, 2), null, stack(Items.IRON_INGOT, 3));
        var encoded = nativeResonant(h, inputs, 0, GlobalPos.of(h.getLevel().dimension(), SOURCE.offset(FIRST)), Direction.UP);
        arm(h, be, encoded, () -> {
            h.assertTrue(logic(be).pushPattern(pattern(be), counters(inputs)), "real AE2CS pattern decoded and dispatched");
            h.assertTrue(count(explicit, Items.IRON_INGOT) == 2 && count(defaultPort, Items.IRON_INGOT) == 3,
                    "explicit targets rebase, blank targets retain core defaults, duplicate quantities remain separate");
            h.assertTrue(!h.getLevel().isLoaded(SOURCE), "no dependency on old source chunk");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test")
    public static void nativeCrystalScienceCrossDimensionMarksAreRejected(GameTestHelper h) {
        if (!ModList.get().isLoaded("ae2cs")) { h.succeed(); return; }
        var anchorPos = h.absolutePos(new BlockPos(5, 2, 5)); furnace(h, anchorPos, Direction.NORTH);
        chest(h, anchorPos.offset(FIRST));
        var inputs = List.of(stack(Items.IRON_INGOT, 1));
        var encoded = nativeResonant(h, inputs, 0, GlobalPos.of(Level.NETHER, SOURCE.offset(FIRST)), Direction.UP);
        var pattern = PatternDetailsHelper.decodePattern(encoded, h.getLevel());
        h.assertTrue(pattern != null && ResonantPatternAccess.read(pattern) != null, "native CS payload read");
        h.assertTrue(ResonantDispatch.plan(layout(h), ResonantPatternAccess.read(pattern), counters(inputs),
                new ResonantDispatch.Anchor(h.getLevel(), anchorPos, Direction.NORTH), anchorPos.west()) == null,
                "cross-dimension absolute mark never silently becomes an overworld relative target");
        h.succeed();
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void extendedCapacityPagesPersistenceAndRecipe(GameTestHelper h) {
        var be = extendedProvider(h);
        var inv = logic(be).getPatternInv();
        h.assertTrue(inv.size() == 144, "extended inventory has 144 physical slots");
        for (int i = 0; i < 144; i++) inv.setItemDirect(i, ordinary(List.of(stack(Items.IRON_INGOT, i + 1))));
        var saved = new CompoundTag();
        saved = be.saveWithoutMetadata();
        inv.clear();
        be.load(saved);
        for (int i = 0; i < 144; i++) {
            h.assertTrue(ItemStack.isSameItemSameTags(inv.getStackInSlot(i),
                    ordinary(List.of(stack(Items.IRON_INGOT, i + 1)))), "all 144 slots survive NBT reload: " + i);
        }
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("ae2ltpp:lightning_assembly/extended_packaged_pattern_provider"))
                .isPresent(), "upgrade recipe loads against real ingredient registries");
        h.assertTrue(appeng.api.networking.GridHelper.getNodeHost(h.getLevel(), be.getBlockPos()) == be,
                "extended provider exposes its AE grid node");
        h.assertTrue(be.getCapability(Capabilities.GENERIC_INTERNAL_INV).isPresent(),
                "extended provider exposes the insert-only return buffer");
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ExtendedPages"));
        var menu = new PackagedPatternProviderMenu(1, player.getInventory(), be);
        var otherMenu = new PackagedPatternProviderMenu(2, player.getInventory(), be);
        var slots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        h.assertTrue(slots.size() == 144 && menu.getTotalPages() == 4, "menu exposes four complete pages");
        for (int page = 0; page < 4; page++) {
            h.assertTrue(menu.currentPage == page, "server page changes through native menu action");
            for (int i = 0; i < 144; i++) h.assertTrue(slots.get(i).isActive() == (i / 36 == page), "visible page " + page);
            for (int i = 0; i < 144; i++) if (i / 36 != page) {
                h.assertTrue(!slots.get(i).mayPickup(player) && !slots.get(i).mayPlace(ordinary(List.of(stack(Items.IRON_INGOT, 1)))),
                        "hidden slots reject server pickup and insertion");
                var original = inv.getStackInSlot(i).copy();
                h.assertTrue(menu.quickMoveStack(player, slots.get(i).index).isEmpty(),
                        "AE2 region quick move rejects hidden slots: " + i);
                menu.clicked(slots.get(i).index, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
                h.assertTrue(menu.getCarried().isEmpty() && ItemStack.isSameItemSameTags(inv.getStackInSlot(i), original),
                        "direct clicks and quick moves leave hidden patterns unchanged: " + i);
                var incoming = ordinary(List.of(stack(Items.GOLD_INGOT, i + 1)));
                menu.setItem(slots.get(i).index, menu.getStateId(), incoming);
                h.assertTrue(ItemStack.isSameItemSameTags(inv.getStackInSlot(i), incoming),
                        "hidden slots accept inventory synchronization before page visibility changes: " + i);
                slots.get(i).set(ordinary(List.of(stack(Items.IRON_INGOT, i + 1))));
            }
            action(menu, "nextPage");
        }
        h.assertTrue(menu.currentPage == 3 && otherMenu.currentPage == 0, "last page clamps and other viewers keep their page");
        slots.get(143).set(ordinary(List.of(stack(Items.GOLD_INGOT, 144))));
        h.assertTrue(ItemStack.isSameItemSameTags(inv.getStackInSlot(143), slots.get(143).getItem()), "last visible slot addresses slot 144");
        for (int i = 0; i < 5; i++) action(menu, "prevPage");
        h.assertTrue(menu.currentPage == 0, "previous page clamps at zero");
        h.runAfterDelay(40, () -> {
            logic(be).updatePatterns();
            h.assertTrue(logic(be).getAvailablePatterns().size() == 144, "patterns on all pages remain advertised");
            var drops = new ArrayList<ItemStack>();
            logic(be).addDrops(drops);
            h.assertTrue(drops.size() == 144, "breaking drops patterns from every page");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void extendedModeSwitchChangesTargetsAndPersistsBindings(GameTestHelper h) {
        var be = extendedProvider(h);
        var localAnchor = be.getBlockPos().east();
        var remoteAnchor = h.absolutePos(new BlockPos(7, 2, 2));
        furnace(h, localAnchor, Direction.NORTH);
        furnace(h, remoteAnchor, Direction.NORTH);
        var localOutput = chest(h, localAnchor.offset(FIRST));
        var remoteOutput = chest(h, remoteAnchor.offset(FIRST));
        be.getAdapterInv().setItemDirect(0, core(layout(h).withTarget(0, target(FIRST, Direction.UP, "minecraft:chest"))));
        h.assertTrue(be.getProviderMode() == ProviderMode.NORMAL && be.getTargets().size() == 6, "new extended provider starts adjacent");
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ExtendedMode"));
        var menu = new PackagedPatternProviderMenu(1, player.getInventory(), be);
        action(menu, "toggleMode");
        h.assertTrue(be.isWirelessProvider() && be.getTargets().isEmpty(), "menu switches to wireless targets");
        be.addOrUpdateConnection(h.getLevel().dimension(), remoteAnchor, Direction.NORTH);
        h.assertTrue(be.getConnections().size() == 1, "wireless connector accepts the configured machine");
        var inputs = List.of(stack(Items.IRON_INGOT, 2));
        logic(be).getPatternInv().setItemDirect(143, ordinary(inputs));
        h.runAfterDelay(40, () -> {
            logic(be).updatePatterns();
            h.assertTrue(logic(be).pushPattern(pattern(be), counters(inputs)), "last-page pattern dispatches wirelessly");
            h.assertTrue(count(remoteOutput, Items.IRON_INGOT) == 2 && count(localOutput, Items.IRON_INGOT) == 0,
                    "wireless mode only uses bound machine");
            action(menu, "toggleMode");
            h.assertTrue(be.getTargets().size() == 6 && be.getConnections().size() == 1, "normal mode retains remote bindings");
            h.assertTrue(logic(be).pushPattern(pattern(be), counters(inputs)), "same pattern dispatches adjacent after switch");
            h.assertTrue(count(localOutput, Items.IRON_INGOT) == 2 && count(remoteOutput, Items.IRON_INGOT) == 2,
                    "normal mode uses only adjacent machine");
            action(menu, "toggleMode");
            var saved = new CompoundTag(); saved = be.saveWithoutMetadata();
            be.setProviderMode(ProviderMode.NORMAL);
            be.load(saved);
            h.assertTrue(be.isWirelessProvider() && be.getConnections().size() == 1, "wireless mode and bindings survive reload");
            h.assertTrue(!be.getInstalledAdapterStack().isEmpty() && !logic(be).getPatternInv().getStackInSlot(143).isEmpty(),
                    "switch and reload retain core and last-page pattern");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test", timeoutTicks = 100)
    public static void extendedModeSwitchKeepsAcceptedTailDestination(GameTestHelper h) {
        var be = extendedProvider(h);
        be.setProviderMode(ProviderMode.WIRELESS);
        var remoteAnchor = h.absolutePos(new BlockPos(7, 2, 2));
        var adjacentAnchor = be.getBlockPos().east();
        furnace(h, remoteAnchor, Direction.NORTH);
        furnace(h, adjacentAnchor, Direction.NORTH);
        var partial = new ResonantTestPorts.PartialHandler(3);
        itemPort(h, remoteAnchor.offset(FIRST), Direction.UP, partial);
        var adjacent = itemPort(h, adjacentAnchor.offset(FIRST), Direction.UP, new ItemStackHandler(1));
        be.getAdapterInv().setItemDirect(0, core(layout(h).withTarget(0, target(FIRST, Direction.UP, "minecraft:beacon"))));
        be.addOrUpdateConnection(h.getLevel().dimension(), remoteAnchor, Direction.NORTH);
        var inputs = List.of(stack(Items.IRON_INGOT, 8));
        arm(h, be, ordinary(inputs), () -> {
            var logic = logic(be);
            h.assertTrue(logic.pushPattern(pattern(be), counters(inputs)) && logic.isBusy(), "provider owns undelivered tail");
            be.setProviderMode(ProviderMode.NORMAL);
            var saved = new CompoundTag(); saved = be.saveWithoutMetadata();
            logic.clearContent(); be.load(saved);
            h.assertTrue(!be.isWirelessProvider() && logic.isBusy(), "normal mode and pending tail reload together");
            partial.budget = 20;
            logic.tickAutoReturn();
            h.assertTrue(partial.getStackInSlot(0).getCount() == 8 && adjacent.getStackInSlot(0).isEmpty(),
                    "accepted materials finish at original remote port without redirect or duplication");
            h.assertTrue(!logic.isBusy() && be.getConnections().size() == 1, "completed tail clears while binding stays");
            h.succeed();
        });
    }

    @GameTest(template = "resonant_test")
    public static void originalProvidersKeepFixedModesAndCapacity(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "FixedModes"));
        for (boolean wireless : new boolean[] {false, true}) {
            var be = provider(h, wireless);
            var mode = wireless ? ProviderMode.WIRELESS : ProviderMode.NORMAL;
            be.setProviderMode(wireless ? ProviderMode.NORMAL : ProviderMode.WIRELESS);
            var menu = new PackagedPatternProviderMenu(1, player.getInventory(), be);
            action(menu, "toggleMode");
            action(menu, "nextPage");
            h.assertTrue(be.getProviderMode() == mode && !menu.isModeSwitchVisible(), "fixed mode rejects hidden menu actions");
            h.assertTrue(logic(be).getPatternInv().size() == 36 && menu.getTotalPages() == 1 && menu.currentPage == 0,
                    "original models remain 36 slots and one page");
        }
        h.succeed();
    }

    private static ExtendedPackagedPatternProviderBlockEntity extendedProvider(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 8));
        h.getLevel().setBlockAndUpdate(pos, PPBlocks.EXTENDED_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        return (ExtendedPackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(pos);
    }

    private static ResonantLayout layout(GameTestHelper h) {
        return new ResonantLayout(GlobalPos.of(h.getLevel().dimension(), SOURCE), id("minecraft:furnace"),
                Direction.NORTH, Direction.UP, 0, List.of());
    }
    private static ResonantLayout.Target target(BlockPos offset, Direction face, String block) {
        return new ResonantLayout.Target(offset, face, Optional.of(id(block)));
    }
    private static ItemStack core(ResonantLayout layout) {
        var core = new ItemStack(PPItems.RESONANT_ADAPTER.get()); ResonantLayoutData.set(core, layout); return core;
    }
    private static PackagedPatternProviderBlockEntity provider(GameTestHelper h, boolean wireless) {
        var pos = h.absolutePos(new BlockPos(2, 2, 8));
        net.minecraft.world.level.block.Block block = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get() : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        return (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(pos);
    }
    private static void furnace(GameTestHelper h, BlockPos pos, Direction facing) {
        h.getLevel().setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
    }
    private static ChestBlockEntity chest(GameTestHelper h, BlockPos pos) {
        h.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        return (ChestBlockEntity) h.getLevel().getBlockEntity(pos);
    }
    private static ItemStackHandler itemPort(GameTestHelper h, BlockPos pos, Direction face, ItemStackHandler handler) {
        h.getLevel().setBlockAndUpdate(pos, Blocks.BEACON.defaultBlockState());
        ResonantTestPorts.ITEMS.put(new ResonantTestPorts.Port(GlobalPos.of(h.getLevel().dimension(), pos), face), handler);
        return handler;
    }
    private static void fluidPort(GameTestHelper h, BlockPos pos, Direction face, FluidTank handler) {
        h.getLevel().setBlockAndUpdate(pos, Blocks.COMPARATOR.defaultBlockState());
        ResonantTestPorts.FLUIDS.put(new ResonantTestPorts.Port(GlobalPos.of(h.getLevel().dimension(), pos), face), handler);
    }
    private static void arm(GameTestHelper h, PackagedPatternProviderBlockEntity be, ItemStack encoded, Runnable action) {
        logic(be).getPatternInv().setItemDirect(0, encoded);
        h.runAfterDelay(40, () -> { logic(be).updatePatterns(); h.assertTrue(!logic(be).getAvailablePatterns().isEmpty(), "pattern registered"); action.run(); });
    }
    private static PackagedPatternProviderLogic logic(PackagedPatternProviderBlockEntity be) { return (PackagedPatternProviderLogic) be.getLogic(); }
    private static IPatternDetails pattern(PackagedPatternProviderBlockEntity be) { return logic(be).getAvailablePatterns().get(0); }
    private static ItemStack ordinary(List<GenericStack> inputs) { return PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new), new GenericStack[] {stack(Items.DIAMOND, 1)}); }
    private static GenericStack stack(Item item, long count) { return new GenericStack(AEItemKey.of(item), count); }
    private static KeyCounter[] counters(List<GenericStack> inputs) {
        var counter = new KeyCounter(); for (var stack : inputs) if (stack != null) counter.add(stack.what(), stack.amount()); return new KeyCounter[] {counter};
    }
    private static long count(ChestBlockEntity chest, Item item) {
        long total = 0; for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(item)) total += chest.getItem(i).getCount(); return total;
    }
    private static long count(List<ItemStack> stacks, Item item) { return stacks.stream().filter(s -> s.is(item)).mapToLong(ItemStack::getCount).sum(); }
    private static void action(PackagedPatternProviderMenu menu, String name) {
        var data = new FriendlyByteBuf(Unpooled.buffer());
        data.writeVarInt(menu.containerId); data.writeUtf(name);
        var packet = new appeng.core.sync.packets.GuiDataSyncPacket(data);
        try { menu.receiveClientAction(packet); }
        finally { packet.getData().release(); data.release(); }
    }
    private static ResourceLocation id(String id) { return new ResourceLocation(id); }
    private static void click(net.minecraft.server.level.ServerPlayer player, BlockPos pos, Direction face) {
        var stack = player.getMainHandItem();
        stack.getItem().onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)));
    }

    /** Build the upstream item and NBT using its actual runtime API, without a compile dependency. */
    private static ItemStack nativeResonant(GameTestHelper h, List<GenericStack> inputs, int markedSlot, GlobalPos pos, Direction face) {
        try {
            var stack = BuiltInRegistries.ITEM.get(id("ae2cs:resonating_pattern")).getDefaultInstance();
            Class.forName("io.github.lounode.ae2cs.common.me.crafting.ResonatingPatternDetails")
                    .getMethod("encode", ItemStack.class, List.class, List.class).invoke(null, stack, inputs, List.of(stack(Items.DIAMOND, 1)));
            var components = Class.forName("io.github.lounode.ae2cs.common.init.AECSDataComponents");
            var encoded = components.getMethod("getEncodedResonatingPattern", ItemStack.class).invoke(null, stack);
            var targetClass = Class.forName("io.github.lounode.ae2cs.common.me.crafting.EncodedResonatingPattern$Target");
            var target = targetClass.getConstructor(GlobalPos.class, Direction.class).newInstance(pos, face);
            var updated = encoded.getClass().getMethod("withTarget", int.class, targetClass).invoke(encoded, markedSlot, target);
            components.getMethod("setEncodedResonatingPattern", ItemStack.class, encoded.getClass()).invoke(null, stack, updated);
            return stack;
        } catch (ReflectiveOperationException e) { throw new AssertionError("Real Crystal Science pattern API unavailable", e); }
    }
}
