package com.moakiee.ae2lt.packaged.core;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraAccess;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Requires the optional Nature's Aura integration runtime. */
@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class CoreNativeHarvestTests {
    @GameTest(batch="coreNativeHarvest", template="adapterconnectiontests.adapter_conn", timeoutTicks=500)
    public static void resonantSlotHarvestsNativeCoreNormally(GameTestHelper h) throws Exception {
        order(h, false);
    }

    @GameTest(batch="coreNativeHarvest", template="adapterconnectiontests.adapter_conn", timeoutTicks=500)
    public static void resonantSlotHarvestsNativeCoreWirelessly(GameTestHelper h) throws Exception {
        order(h, true);
    }

    private static void order(GameTestHelper h, boolean wireless) throws Exception {
        var level = h.getLevel();
        var altar = h.absolutePos(new BlockPos(7, 3, 7));
        var structure = Class.forName("de.ellpeck.naturesaura.blocks.multi.Multiblocks").getField("ALTAR").get(null);
        BiFunction<BlockPos, Object, Boolean> place = (pos, matcher) -> {
            try {
                var state = (BlockState) matcher.getClass().getMethod("defaultState").invoke(matcher);
                if (state != null) level.setBlockAndUpdate(pos, state);
                return true;
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        };
        structure.getClass().getMethod("forEach", BlockPos.class, char.class, BiFunction.class)
                .invoke(structure, altar, '\0', place);
        var container = NaturesAuraAccess.field(level.getBlockEntity(altar), "container");
        container.getClass().getMethod("storeAura", int.class, boolean.class).invoke(container, 100000, false);
        var pos = altar.west(wireless ? 6 : 1);
        net.minecraft.world.level.block.Block block = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get()
                : PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        level.setBlockAndUpdate(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(pos.north(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(pos.south(), AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        ((DriveBlockEntity) level.getBlockEntity(pos.north())).getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
        var provider = (PackagedPatternProviderBlockEntity) level.getBlockEntity(pos);
        provider.setReturnMode(com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode.AUTO);
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.RESONANT_ADAPTER.get()));
        var logic = (PackagedPatternProviderLogic) provider.getLogic();
        var input = AEItemKey.of(PPItems.BASIC_PACKAGED_CORE.get());
        var output = AEItemKey.of(PPItems.NATURES_AURA_ALTAR_ADAPTER.get());
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(input, 1)}, new GenericStack[]{new GenericStack(output, 1)}));
        h.assertTrue((boolean) structure.getClass().getMethod("isComplete", net.minecraft.world.level.Level.class, BlockPos.class)
                .invoke(structure, level, altar), "fixture preserves the complete native altar structure");
        h.runAfterDelay(60, () -> {
            logic.updatePatterns();
            if (wireless) h.assertTrue(provider.addOrUpdateConnection(level.dimension(), altar, Direction.WEST), "native core target admission");
            var grid = provider.getMainNode().getGrid();
            h.assertTrue(grid != null && provider.getMainNode().isActive(), "active grid");
            var storage = grid.getStorageService().getInventory();
            h.assertTrue(storage.insert(input, 1, Actionable.MODULATE, IActionSource.empty()) == 1, "seed one paid input");
            provider.getCoreOrder().request(PPItems.NATURES_AURA_ALTAR_ADAPTER.get());
            h.runAfterDelay(400, () -> System.out.println("CORE_NATIVE_STATE wireless=" + wireless
                    + " order=" + provider.getCoreOrder().save() + " altar=" + level.getBlockEntity(altar).saveWithFullMetadata()
                    + " mode=" + provider.getReturnMode() + " connections=" + provider.getConnections()
                    + " stock=" + storage.getAvailableStacks()));
            h.succeedWhen(() -> {
                h.assertTrue(provider.getCoreOrder().status().equals("returned"), "native order status=" + provider.getCoreOrder().status());
                h.assertTrue(provider.getInstalledAdapterStack().is(PPItems.RESONANT_ADAPTER.get()), "resonant core retained");
                h.assertTrue(storage.getAvailableStacks().get(output) == 1 && storage.getAvailableStacks().get(input) == 0, "exact native product and input consumption");
                h.assertTrue(grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()), "CPU finished");
                System.out.println("CORE_NATIVE_HARVEST_PASS wireless=" + wireless);
            });
        });
    }
}
