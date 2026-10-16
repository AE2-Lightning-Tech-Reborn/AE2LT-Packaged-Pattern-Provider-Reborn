package com.moakiee.ae2lt.packaged.gametest;

import java.util.List;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.logic.multiblock.chisel.ChippedWorkbenchAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.chisel.HandheldChiselAdapter;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class ChiselingBackportTests {
    private ChiselingBackportTests() {}

    private static GenericStack stack(String id, long amount) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null) throw new IllegalStateException("Missing chiseling test item: " + id);
        return new GenericStack(AEItemKey.of(item), amount);
    }

    private static ItemStack encoded(GenericStack input, GenericStack output) {
        return PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{input}, new GenericStack[]{output});
    }

    private static IPatternDetails pattern(GameTestHelper h, GenericStack input, GenericStack output) {
        return PatternDetailsHelper.decodePattern(encoded(input, output), h.getLevel());
    }

    private static KeyCounter[] counters(GenericStack input) {
        var counter = new KeyCounter();
        counter.add(input.what(), input.amount());
        return new KeyCounter[]{counter};
    }

    private static <T extends Comparable<T>> BlockState model(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow());
    }

    private static BlockPos bench(GameTestHelper h, BlockPos relative, String name) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("chipped", name));
        h.assertTrue(block != null, "Missing native Chipped bench");
        var state = block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        var property = state.getProperties().stream().filter(p -> p.getName().equals("model")).findFirst().orElseThrow();
        var main = h.absolutePos(relative);
        // Install both halves before neighbor updates can remove an incomplete bench.
        h.getLevel().setBlock(main, model(state, property, "main"), 2);
        h.getLevel().setBlock(main.east(), model(state, property, "side"), 2);
        return main;
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 60)
    public static void chiselingCoreFollowsOptionalModAvailability(GameTestHelper h) {
        boolean installed = ModList.get().isLoaded("chipped") || ModList.get().isLoaded("rechiseled")
                || ModList.get().isLoaded("chisel");
        boolean recipe = h.getLevel().getRecipeManager()
                .byKey(new ResourceLocation("ae2ltpp", "packaged_core/chiseling_packaged_core")).isPresent();
        h.assertTrue(recipe == installed, "Chiseling core recipe ignored optional-mod conditions");
        if (ModList.get().isLoaded("chipped")) {
            var coreRecipe = h.getLevel().getRecipeManager()
                    .byKey(new ResourceLocation("ae2ltpp", "packaged_core/chiseling_packaged_core")).orElseThrow();
            var bench = ForgeRegistries.ITEMS.getValue(new ResourceLocation("chipped", "mason_table"));
            h.assertTrue(coreRecipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(bench))),
                    "Chipped 3.x has no chisel item: a native workbench must be usable to craft the core");
        }
        boolean registered = MultiblockAdapterRegistry.activeAdapters().stream()
                .anyMatch(a -> a instanceof ChippedWorkbenchAdapter || a instanceof HandheldChiselAdapter);
        h.assertTrue(registered == installed, "Optional chiseling adapters loaded incorrectly");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 60)
    public static void chippedUsesNativeWorkbenchTypesAndBothHalves(GameTestHelper h) {
        if (!ModList.get().isLoaded("chipped")) { h.succeed(); return; }
        var mason = bench(h, new BlockPos(5, 2, 5), "mason_table");
        var carpenter = bench(h, new BlockPos(10, 2, 5), "carpenters_table");
        var input = stack("minecraft:stone", 3);
        var output = stack("chipped:cracked_disordered_stone_bricks", 3);
        var pattern = pattern(h, input, output);
        var adapter = new ChippedWorkbenchAdapter();
        var binding = adapter.bind(h.getLevel(), mason, pattern);
        h.assertTrue(binding != null, "Native mason conversion did not bind");
        var result = adapter.planVirtualWithBinding(h.getLevel(), mason, pattern, counters(input),
                binding.handle(), IActionSource.empty());
        h.assertTrue(result != null && result.outputs().equals(List.of(output)), "Chipped conversion changed quantities");
        h.assertTrue(adapter.bind(h.getLevel(), carpenter, pattern) == null, "Wrong workbench recipe type accepted stone");
        h.assertTrue(!adapter.recognizesMain(h.getLevel(), mason.east(), null), "Side half became an independent target");
        h.getLevel().setBlockAndUpdate(mason.east(), Blocks.AIR.defaultBlockState());
        h.assertTrue(!adapter.canDispatch(h.getLevel(), mason, binding.handle()), "Incomplete bench retained dispatch capability");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 60)
    public static void chippedWirelessAdmissionRequiresCoreAndMainHalf(GameTestHelper h) {
        if (!ModList.get().isLoaded("chipped")) { h.succeed(); return; }
        var main = bench(h, new BlockPos(7, 2, 7), "mason_table");
        var providerPos = h.absolutePos(new BlockPos(3, 2, 3));
        h.getLevel().setBlockAndUpdate(providerPos, PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        var provider = (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(providerPos);
        h.assertTrue(!provider.acceptsWirelessTarget(h.getLevel(), main), "Missing core accepted a Chipped bench");
        provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.CHISELING_ADAPTER.get()));
        h.assertTrue(provider.addOrUpdateConnection(h.getLevel().dimension(), main, Direction.UP),
                "Chiseling core did not admit the physical main half without a block entity");
        h.assertTrue(!provider.acceptsWirelessTarget(h.getLevel(), main.east()), "Wireless side-half admission bypassed validation");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 60)
    public static void rechiseledRejectsInflationRemaindersAndOutputNbt(GameTestHelper h) {
        if (!ModList.get().isLoaded("rechiseled")) { h.succeed(); return; }
        var adapter = new HandheldChiselAdapter(true);
        var pos = h.absolutePos(new BlockPos(4, 2, 4));
        var input = stack("minecraft:acacia_planks", 3);
        var output = stack("rechiseled:acacia_planks_beams_slab", 6);
        var valid = pattern(h, input, output);
        var binding = adapter.bind(h.getLevel(), pos, valid);
        h.assertTrue(binding != null, "Native block-to-slab worth was not recognized");
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(), pos, valid, counters(input), binding.handle(),
                IActionSource.empty()).outputs().equals(List.of(output)), "Native worth conversion produced wrong amount");
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern(h, input, stack("rechiseled:acacia_planks_beams_slab", 7))) == null,
                "Inflated output was accepted");
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern(h, stack("minecraft:acacia_slab", 3),
                stack("rechiseled:acacia_planks_beams", 1))) == null, "Partial conversion silently consumed leftover input");
        var tagged = ((AEItemKey) output.what()).toStack(1);
        tagged.getOrCreateTag().putString("test", "arbitrary");
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern(h, input,
                new GenericStack(AEItemKey.of(tagged), output.amount()))) == null, "Conversion invented output NBT");
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(), pos, valid, counters(stack("minecraft:acacia_planks", 4)),
                binding.handle(), IActionSource.empty()) == null, "Surplus delivered inputs were lost");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 150)
    public static void rechiseledInternalLaneWorksForBothProviderVariants(GameTestHelper h) {
        if (!ModList.get().isLoaded("rechiseled")) { h.succeed(); return; }
        var input = stack("minecraft:acacia_planks", 3);
        var output = stack("rechiseled:acacia_planks_beams_slab", 6);
        var providers = new java.util.ArrayList<PackagedPatternProviderBlockEntity>();
        for (boolean wireless : new boolean[]{false, true}) {
            var relative = new BlockPos(wireless ? 10 : 4, 2, 4);
            var state = wireless ? PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState()
                    : PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState();
            h.setBlock(relative, state);
            h.setBlock(relative.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            var provider = (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(relative));
            provider.getLogic().getPatternInv().setItemDirect(0, encoded(input, output));
            provider.getLogic().updatePatterns();
            providers.add(provider);
        }
        h.runAfterDelay(40, () -> {
            for (var provider : providers) {
                var logic = (PackagedPatternProviderLogic) provider.getLogic();
                var pattern = logic.getAvailablePatterns().get(0);
                h.assertTrue(!logic.pushPattern(pattern, counters(input)), "Missing core unlocked internal conversion");
                provider.getAdapterInv().setItemDirect(0, new ItemStack(PPItems.CHISELING_ADAPTER.get()));
                h.assertTrue(provider.getConnections().isEmpty(), "Internal test unexpectedly has a wireless target");
                h.assertTrue(logic.pushPattern(pattern, counters(input)), "Internal lane requires a neighboring machine or wireless target");
                h.assertTrue(logic.drainVirtualBatch().equals(List.of(output)), "Internal output was missing or duplicated");
                provider.getAdapterInv().setItemDirect(0, ItemStack.EMPTY);
                h.assertTrue(!logic.pushPattern(pattern, counters(input)), "Removing core retained an internal binding");
            }
            h.succeed();
        });
    }
}
