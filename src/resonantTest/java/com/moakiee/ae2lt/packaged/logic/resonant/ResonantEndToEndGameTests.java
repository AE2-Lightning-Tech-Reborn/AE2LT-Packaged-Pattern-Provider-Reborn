package com.moakiee.ae2lt.packaged.logic.resonant;

import java.util.List;
import java.util.Optional;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ResonantEndToEndGameTests {
    private static final BlockPos SOURCE = new BlockPos(-1000, 80, -1000);
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void furnaceNormalOrdinary(GameTestHelper h) { run(h, false, false, false, false); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void furnaceWirelessOrdinary(GameTestHelper h) { run(h, true, false, false, false); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void furnaceExtendedNormalOrdinary(GameTestHelper h) { run(h, false, true, false, false); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void furnaceExtendedWirelessOrdinary(GameTestHelper h) { run(h, true, true, false, false); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void furnaceNormalNative(GameTestHelper h) { run(h, false, false, true, false); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void furnaceWirelessNative(GameTestHelper h) { run(h, true, false, true, false); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void furnaceExtendedNormalNative(GameTestHelper h) { run(h, false, true, true, false); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void furnaceExtendedWirelessNative(GameTestHelper h) { run(h, true, true, true, false); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void aggregatorNormalOrdinary(GameTestHelper h) { run(h, false, false, false, true); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void aggregatorWirelessNative(GameTestHelper h) { run(h, true, false, true, true); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void aggregatorExtendedNormalNative(GameTestHelper h) { run(h, false, true, true, true); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void aggregatorExtendedWirelessOrdinary(GameTestHelper h) { run(h, true, true, false, true); }

    @GameTest(template="resonant_test", timeoutTicks=1400) public static void dualFurnaceNormalOrdinary(GameTestHelper h) { run(h,false,false,false,false,true); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void dualFurnaceWirelessNative(GameTestHelper h) { run(h,true,false,true,false,true); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void dualFurnaceExtendedNormalNative(GameTestHelper h) { run(h,false,true,true,false,true); }
    @GameTest(template="resonant_test", timeoutTicks=1400) public static void dualFurnaceExtendedWirelessOrdinary(GameTestHelper h) { run(h,true,true,false,false,true); }

    private static void run(GameTestHelper h, boolean wireless, boolean extended, boolean nativePattern, boolean aggregator) {
        run(h,wireless,extended,nativePattern,aggregator,false);
    }
    private static void run(GameTestHelper h, boolean wireless, boolean extended, boolean nativePattern, boolean aggregator, boolean dual) {
        if ((nativePattern || aggregator) && !ModList.get().isLoaded("ae2cs")) { h.succeed(); return; }
        var level = h.getLevel();
        var providerPos = h.absolutePos(new BlockPos(3,2,5));
        net.minecraft.world.level.block.Block providerBlock = extended ? PPBlocks.EXTENDED_PACKAGED_PATTERN_PROVIDER.get()
                : wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get() : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        level.setBlockAndUpdate(providerPos, providerBlock.defaultBlockState());
        level.setBlockAndUpdate(providerPos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(providerPos.north(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(providerPos.south(), AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        var drive = (DriveBlockEntity)level.getBlockEntity(providerPos.north());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
        var provider = (PackagedPatternProviderBlockEntity)level.getBlockEntity(providerPos);
        if (extended) provider.setProviderMode(wireless ? ProviderMode.WIRELESS : ProviderMode.NORMAL);
        provider.setReturnMode(ReturnMode.AUTO);
        var logic = (PackagedPatternProviderLogic)provider.getLogic();
        var machinePos = providerPos.east(wireless ? 5 : 1);
        var machineId = id(aggregator ? "ae2cs:crystal_aggregator" : "minecraft:furnace");
        var machineBlock = BuiltInRegistries.BLOCK.get(machineId);
        h.assertTrue(machineBlock != Blocks.AIR, "machine registered " + machineId);
        var state = machineBlock.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, wireless ? Direction.EAST : Direction.NORTH);
        level.setBlockAndUpdate(machinePos, state);
        if (aggregator) level.setBlockAndUpdate(machinePos.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        else ((FurnaceBlockEntity)level.getBlockEntity(machinePos)).setItem(1, new ItemStack(Items.COAL, 4));
        var secondPos = machinePos.offset(wireless ? new BlockPos(0,0,2) : new BlockPos(2,0,0));
        if (dual) {
            level.setBlockAndUpdate(secondPos, Blocks.FURNACE.defaultBlockState());
            ((FurnaceBlockEntity)level.getBlockEntity(secondPos)).setItem(1,new ItemStack(Items.COAL,4));
        }
        var layout = new ResonantLayout(GlobalPos.of(level.dimension(), SOURCE), machineId, Direction.NORTH, Direction.UP, 0, List.of())
                .withTarget(0, target(BlockPos.ZERO, Direction.UP, machineId))
                .withTarget(144, target(BlockPos.ZERO, Direction.DOWN, machineId));
        if (aggregator) layout = layout.withTarget(1, target(BlockPos.ZERO, Direction.UP, machineId))
                .withTarget(2, target(BlockPos.ZERO, Direction.UP, machineId));
        if (dual) layout = layout.withTarget(1,target(nativePattern ? BlockPos.ZERO : new BlockPos(2,0,0),Direction.UP,machineId))
                .withTarget(145,target(new BlockPos(2,0,0),Direction.DOWN,machineId));
        var core = PPItems.RESONANT_ADAPTER.toStack();
        core.set(PPDataComponents.RESONANT_LAYOUT.get(), layout);
        provider.getAdapterInv().setItemDirect(0, core);
        if (wireless) h.assertTrue(provider.addOrUpdateConnection(level.dimension(), machinePos, Direction.NORTH), "wireless bound");
        var inputs = aggregator ? List.of(stack(AEItems.CERTUS_QUARTZ_CRYSTAL.asItem(),8), stack(Items.REDSTONE,8), stack(Items.QUARTZ,8)) : dual ? List.of(stack(Items.SAND,1),stack(Items.SAND,2)) : List.of(stack(Items.SAND,1));
        var out = aggregator ? AEItems.FLUIX_CRYSTAL.asItem() : Items.GLASS;
        long perCraft = aggregator ? 32 : dual ? 3 : 1;
        long crafts = aggregator || dual ? 2 : 3;
        var outputs = List.of(stack(out,perCraft));
        var encoded = nativePattern ? nativePattern(h, inputs, outputs, dual) : PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
        logic.getPatternInv().setItemDirect(extended ? 143 : 0, encoded);
        var source = IActionSource.empty();
        var label = "machine=" + machineId + " wireless=" + wireless + " extended=" + extended + " nativePattern=" + nativePattern + " dual=" + dual;
        h.runAfterDelay(80, () -> {
            logic.updatePatterns();
            var grid = logic.getGrid();
            h.assertTrue(grid != null && provider.getMainNode().isActive(), "powered active grid " + label);
            var service = grid.getCraftingService();
            var stock = grid.getStorageService().getInventory();
            h.assertTrue(service.getCpus().size() == 1, "real CPU registered " + service.getCpus().size());
            h.assertTrue(service.isCraftable(AEItemKey.of(out)), "pattern registered in crafting service");
            h.assertTrue(stock.getAvailableStacks().get(AEItemKey.of(out)) == 0, "no preexisting outputs");
            for (var input : inputs) h.assertTrue(stock.insert(input.what(), input.amount()*crafts, Actionable.MODULATE, source) == input.amount()*crafts, "seed raw stock");
            var requester = new ICraftingSimulationRequester() {
                public IActionSource getActionSource() { return source; }
                public IGridNode getGridNode() { return provider.getMainNode().getNode(); }
            };
            var future = service.beginCraftingCalculation(level, requester, AEItemKey.of(out), perCraft*crafts, CalculationStrategy.REPORT_MISSING_ITEMS);
            var submitted = new boolean[1];
            var cpu = service.getCpus().iterator().next();
            var observedProcessing = new boolean[1];
            var peakInputs = new int[2];
            var submittedAt = new long[1];
            h.succeedWhen(() -> {
                h.assertTrue(future.isDone(), "waiting for actual planner");
                if (!submitted[0]) {
                    try {
                        var plan = future.get();
                        h.assertTrue(!plan.simulation() && plan.missingItems().isEmpty(), "executable plan " + label);
                        var result = service.submitJob(plan, null, null, false, source);
                        h.assertTrue(result.successful(), "submit rejected " + result.errorCode() + " " + result.errorDetail());
                        submitted[0] = true;
                        submittedAt[0] = level.getGameTime();
                        System.out.println("RESONANT_E2E_SUBMITTED " + label + " crafts=" + crafts + " output=" + perCraft*crafts);
                    } catch (Exception e) { throw new AssertionError(e); }
                }
                if (cpu.isBusy() && service.isRequesting(AEItemKey.of(out))) observedProcessing[0] = true;
                if (dual) {
                    peakInputs[0] = Math.max(peakInputs[0],((FurnaceBlockEntity)level.getBlockEntity(machinePos)).getItem(0).getCount());
                    peakInputs[1] = Math.max(peakInputs[1],((FurnaceBlockEntity)level.getBlockEntity(secondPos)).getItem(0).getCount());
                }
                h.assertTrue(!cpu.isBusy() && !service.isRequestingAny(), "waiting for real machine -> core output pickup -> CPU completion " + label);
                if (dual) h.assertTrue(peakInputs[0] == 2 && peakInputs[1] == 4,
                        "duplicate keys split into original slots on two real machines " + java.util.Arrays.toString(peakInputs));
                h.assertTrue(observedProcessing[0], "observed asynchronous processing");
                h.assertTrue(stock.getAvailableStacks().get(AEItemKey.of(out)) == perCraft*crafts, "exact final output stock " + stock.getAvailableStacks().get(AEItemKey.of(out)));
                for (var input : inputs) h.assertTrue(stock.getAvailableStacks().get(input.what()) == 0, "all inputs consumed exactly");
                h.assertTrue(!service.isRequestingAny() && !logic.isBusy(), "no pending request or input remainder");
                h.assertTrue(!level.isLoaded(SOURCE), "old absolute source is not loaded");
                System.out.println("RESONANT_E2E_PASS " + label + " output=" + perCraft*crafts + " ticks=" + (level.getGameTime()-submittedAt[0]) + " CPU=done stock=exact noManualDispatch=true nativeMachineTicker=true autoReturn=true");
            });
        });
    }
    private static ResonantLayout.Target target(BlockPos pos, Direction face, ResourceLocation id) { return new ResonantLayout.Target(pos, face, Optional.of(id)); }
    private static GenericStack stack(Item item, long count) { return new GenericStack(AEItemKey.of(item), count); }
    private static ResourceLocation id(String s) { return ResourceLocation.parse(s); }
    private static ItemStack nativePattern(GameTestHelper h, List<GenericStack> inputs, List<GenericStack> outputs, boolean dual) {
        try {
            var item = BuiltInRegistries.ITEM.get(id("ae2cs:resonating_pattern")).getDefaultInstance();
            Class.forName("io.github.lounode.ae2cs.common.me.crafting.ResonatingPatternDetails")
                    .getMethod("encode", ItemStack.class, List.class, List.class).invoke(null,item,inputs,outputs);
            @SuppressWarnings("unchecked") var type = (DataComponentType<Object>)BuiltInRegistries.DATA_COMPONENT_TYPE.get(id("ae2cs:encoded_resonating_pattern"));
            var encoded = item.get(type);
            var targetClass = Class.forName("io.github.lounode.ae2cs.common.me.crafting.EncodedResonatingPattern$Target");
            var target = targetClass.getConstructor(GlobalPos.class,Direction.class).newInstance(GlobalPos.of(h.getLevel().dimension(),SOURCE),Direction.UP);
            item.set(type,encoded.getClass().getMethod("withTarget",int.class,targetClass).invoke(encoded,0,target));
            if (dual) {
                var second = targetClass.getConstructor(GlobalPos.class,Direction.class).newInstance(GlobalPos.of(h.getLevel().dimension(),SOURCE.east(2)),Direction.UP);
                item.set(type,item.get(type).getClass().getMethod("withTarget",int.class,targetClass).invoke(item.get(type),1,second));
            }
            return item;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
