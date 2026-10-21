package com.moakiee.ae2lt.packaged.gametest;

import java.util.ArrayList;
import java.util.List;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingCombinationAdapter;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

/** Native EC 6.0.x lifecycle checks, based on upstream 13e61ab. */
@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class CombinationCraftingBackportTests {
    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void completedCraftWithoutObservedProgressReturnsOnce(GameTestHelper h) throws Exception {
        var f = new Fixture(h);
        f.dispatch();
        // Model a native completion between provider polls, using real EC inventories/API.
        for (var pedestal : f.pedestals) f.inventory(pedestal).setStackInSlot(0, ItemStack.EMPTY);
        f.inventory(f.core).setStackInSlot(0, f.output.copy());
        var result = f.adapter.extractOutputs(h.getLevel(), f.core, f.filter, null, f.provider);
        h.assertTrue(result.equals(List.of(new GenericStack(AEItemKey.of(f.output), f.output.getCount()))),
                "Completed combination craft was stranded because positive progress was not observed");
        h.assertTrue(f.adapter.extractOutputs(h.getLevel(), f.core, f.filter, null, f.provider).isEmpty(),
                "Completed combination output was returned twice");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void occupiedExtraPedestalBlocksNewCraft(GameTestHelper h) throws Exception {
        var f = new Fixture(h);
        f.inventory(f.pedestals.get(f.pedestals.size() - 1)).setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
        h.assertTrue(!f.adapter.canDispatch(h.getLevel(), f.core, f.handle, f.provider),
                "Existing pedestal materials were ignored when admitting another combination craft");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void unpoweredRecipeKeepsInputsAcrossPolling(GameTestHelper h) throws Exception {
        var f = new Fixture(h);
        f.dispatch();
        var before = f.inventory(f.core).getStackInSlot(0).copy();
        h.runAfterDelay(20, () -> {
            h.assertTrue(f.adapter.extractOutputs(h.getLevel(), f.core, f.filter, null, f.provider).isEmpty(),
                    "Unpowered combination recipe was harvested as a product");
            h.assertTrue(ItemStack.matches(before, f.inventory(f.core).getStackInSlot(0)),
                    "Polling removed the unpowered recipe's center input");
            h.assertTrue(!f.adapter.canDispatch(h.getLevel(), f.core, f.handle, f.provider),
                    "Unpowered combination recipe admitted a second craft");
            h.succeed();
        });
    }

    private static final class Fixture {
        final GameTestHelper h;
        final BlockPos core;
        final List<BlockPos> pedestals = new ArrayList<>();
        final ExtendedCraftingCombinationAdapter adapter = new ExtendedCraftingCombinationAdapter();
        final AllowedOutputFilter filter = new AllowedOutputFilter();
        final PackagedPatternProviderBlockEntity provider;
        final ItemStack output;
        final appeng.api.crafting.IPatternDetails pattern;
        final KeyCounter[] inputs;
        final Object handle;

        Fixture(GameTestHelper h) {
            this.h = h;
            core = h.absolutePos(new BlockPos(8, 2, 8));
            var recipe = h.getLevel().getRecipeManager().getRecipes().stream()
                    .filter(r -> "extendedcrafting:combination".equals(r.getType().toString())
                            && r.getIngredients().size() > 1 && r.getIngredients().size() <= 48
                            && r.getIngredients().stream().allMatch(i -> i.getItems().length > 0))
                    .findFirst().orElseThrow();
            output = recipe.getResultItem(h.getLevel().registryAccess()).copy();
            place(core, "extendedcrafting:crafting_core");
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                if (x == 0 && z == 0) continue;
                var pos = core.offset(x, 0, z);
                place(pos, "extendedcrafting:pedestal");
                pedestals.add(pos);
            }
            var providerPos = h.absolutePos(new BlockPos(1, 2, 1));
            h.getLevel().setBlockAndUpdate(providerPos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
            provider = (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(providerPos);
            var declared = recipe.getIngredients().stream()
                    .map(i -> new GenericStack(AEItemKey.of(i.getItems()[0]), 1)).toArray(GenericStack[]::new);
            pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(declared,
                    new GenericStack[]{new GenericStack(AEItemKey.of(output), output.getCount())}), h.getLevel());
            inputs = new KeyCounter[declared.length];
            for (int i = 0; i < declared.length; i++) {
                inputs[i] = new KeyCounter();
                inputs[i].add(declared[i].what(), declared[i].amount());
            }
            filter.allowStrict(AEItemKey.of(output));
            var binding = adapter.bind(h.getLevel(), core, pattern);
            h.assertTrue(binding != null, "Native combination recipe failed to bind");
            handle = binding.handle();
        }

        void place(BlockPos pos, String id) {
            var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            h.assertTrue(block != null, "Missing EC fixture block: " + id);
            h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        }

        IItemHandlerModifiable inventory(BlockPos pos) {
            try {
                var be = h.getLevel().getBlockEntity(pos);
                return (IItemHandlerModifiable) be.getClass().getMethod("getInventory").invoke(be);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }

        void dispatch() {
            var plan = adapter.planWithBinding(h.getLevel(), core, pattern, inputs, handle, null, provider);
            h.assertTrue(plan != null, "Native combination recipe did not plan");
            h.assertTrue(DispatchExecutor.execute(plan, null,
                    new appeng.helpers.patternprovider.PatternProviderReturnInventory(() -> {})).success(),
                    "Native combination dispatch failed");
        }
    }
}
