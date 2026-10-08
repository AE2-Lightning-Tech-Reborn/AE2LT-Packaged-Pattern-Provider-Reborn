package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ArsNouveauImbuementChamberAdapterTest {
    private static Block chamberBlock, pedestalBlock;

    @BeforeAll static void boot() {
        SharedConstants.tryDetectVersion();
        try (var loading = mockStatic(net.neoforged.fml.loading.LoadingModList.class)) {
            var list = mock(net.neoforged.fml.loading.LoadingModList.class);
            loading.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(list);
            when(list.getModFiles()).thenReturn(List.of());
            Bootstrap.bootStrap();
        }
        ((net.minecraft.core.MappedRegistry<Block>) BuiltInRegistries.BLOCK).unfreeze();
        chamberBlock = Registry.register(BuiltInRegistries.BLOCK, id("imbuement_chamber"),
                new Block(BlockBehaviour.Properties.of()));
        pedestalBlock = Registry.register(BuiltInRegistries.BLOCK, id("arcane_pedestal"),
                new Block(BlockBehaviour.Properties.of()));
    }

    @Test void oneCatalystSetSupports64SequentialDispatchesAndReturns() {
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT), List.of(
                Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND)))) {
            var inputs = List.of(stack(Items.IRON_INGOT, 1), stack(Items.DIAMOND, 2));
            var outputs = List.of(stack(Items.GOLD_INGOT, 1), stack(Items.DIAMOND, 2));
            var pattern = pattern(inputs, outputs);
            var binding = f.adapter.bind(f.level, BlockPos.ZERO, pattern);
            assertNotNull(binding);
            var stock = new KeyCounter();
            inputs.forEach(s -> stock.add(s.what(), s.amount()));
            stock.add(AEItemKey.of(Items.IRON_INGOT), 63);
            long completed = 0;
            for (int i = 0; i < 64; i++) {
                assertTrue(f.adapter.canDispatch(f.level, BlockPos.ZERO, binding.handle()));
                var counters = new KeyCounter[]{new KeyCounter()};
                inputs.forEach(s -> {
                    assertTrue(stock.get(s.what()) >= s.amount());
                    stock.remove(s.what(), s.amount());
                    counters[0].add(s.what(), s.amount());
                });
                var plan = f.adapter.planWithBinding(f.level, BlockPos.ZERO, pattern, counters, binding.handle(), null);
                assertNotNull(plan);
                assertTrue(DispatchExecutor.execute(plan, null, ignored -> fail("unexpected retained inputs")).success());
                assertFalse(f.adapter.canDispatch(f.level, BlockPos.ZERO, binding.handle()));
                assertTrue(f.adapter.extractOutputs(f.level, BlockPos.ZERO, f.filter, null).isEmpty());
                f.chamber.setItem(0, new ItemStack(Items.GOLD_INGOT));
                var returned = f.adapter.extractOutputs(f.level, BlockPos.ZERO, f.filter, null);
                for (var s : returned) {
                    if (s.what().equals(AEItemKey.of(Items.GOLD_INGOT))) completed += s.amount();
                    else stock.add(s.what(), s.amount());
                }
                assertTrue(f.inventories.values().stream().allMatch(Container::isEmpty));
            }
            assertEquals(64, completed);
            assertEquals(2, stock.get(AEItemKey.of(Items.DIAMOND)));
            assertEquals(0, stock.get(AEItemKey.of(Items.IRON_INGOT)));
        }
    }

    @Test void residentCatalystsSupport64ReagentOnlyDispatchesWithoutPedestalIo() {
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT), List.of(
                Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND)))) {
            var catalyst = new ItemStack(Items.DIAMOND, 3);
            catalyst.set(DataComponents.CUSTOM_NAME, Component.literal("resident catalyst"));
            var pedestals = f.inventories.entrySet().stream()
                    .filter(e -> !e.getKey().equals(BlockPos.ZERO)).toList();
            pedestals.forEach(e -> e.getValue().setItem(0, catalyst.copy()));
            var pattern = pattern(List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1)));
            var binding = f.adapter.bind(f.level, BlockPos.ZERO, pattern);
            assertNotNull(binding, "preplaced catalysts are not pattern inputs");
            for (int i = 0; i < 64; i++) {
                assertTrue(f.adapter.canDispatch(f.level, BlockPos.ZERO, binding.handle()));
                var plan = f.adapter.planWithBinding(f.level, BlockPos.ZERO, pattern,
                        reagentInputs(), binding.handle(), null);
                assertNotNull(plan);
                assertEquals(1, plan.targets().size(), "only the chamber receives inputs");
                assertTrue(DispatchExecutor.execute(plan, null, ignored -> fail("unexpected retained input")).success());
                assertTrue(f.adapter.extractOutputs(f.level, BlockPos.ZERO, f.filter, null).isEmpty());
                f.chamber.setItem(0, new ItemStack(Items.GOLD_INGOT));
                // Harvest through a new adapter, as after a provider reload.
                assertEquals(List.of(stack(Items.GOLD_INGOT, 1)), new ArsNouveauImbuementChamberAdapter()
                        .extractOutputs(f.level, BlockPos.ZERO, f.filter, null));
                assertTrue(f.chamber.isEmpty());
                pedestals.forEach(e -> assertTrue(ItemStack.matches(catalyst, e.getValue().getItem(0)),
                        "resident stack count and components are preserved"));
            }
            for (var pedestal : pedestals) {
                verify(f.level, never()).getCapability(eq(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK),
                        eq(pedestal.getKey()), isNull());
            }
        }
    }

    @Test void residentBindingAndDispatchRequireEveryPedestalIngredient() {
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT), List.of(
                Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND)))) {
            var pattern = pattern(List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1)));
            assertNull(f.adapter.bind(f.level, BlockPos.ZERO, pattern));
            var left = f.inventories.get(new BlockPos(-1, 0, 1));
            var right = f.inventories.get(new BlockPos(0, 0, 1));
            left.setItem(0, new ItemStack(Items.DIAMOND, 2));
            assertNull(f.adapter.bind(f.level, BlockPos.ZERO, pattern), "a stack cannot replace two occupied pedestals");
            right.setItem(0, new ItemStack(Items.DIAMOND));
            var binding = f.adapter.bind(f.level, BlockPos.ZERO, pattern);
            assertNotNull(binding);
            var plan = f.adapter.planWithBinding(f.level, BlockPos.ZERO, pattern,
                    reagentInputs(), binding.handle(), null);
            assertNotNull(plan);
            right.setItem(0, new ItemStack(Items.EMERALD));
            assertFalse(f.adapter.canDispatch(f.level, BlockPos.ZERO, binding.handle()));
            assertNull(f.adapter.planWithBinding(f.level, BlockPos.ZERO, pattern,
                    reagentInputs(), binding.handle(), null));
            assertTrue(DispatchExecutor.execute(plan, null, ignored -> fail("no input should be accepted")).failure(),
                    "insertion rechecks catalysts after planning");
            assertTrue(f.chamber.isEmpty());
            right.setItem(0, new ItemStack(Items.DIAMOND));
            assertTrue(f.adapter.canDispatch(f.level, BlockPos.ZERO, binding.handle()));
            var extra = new BlockPos(1, 0, 1);
            f.addBlock(extra, new SimpleContainer(new ItemStack(Items.EMERALD)), pedestalBlock);
            assertFalse(f.adapter.canDispatch(f.level, BlockPos.ZERO, binding.handle()));
            assertNull(f.adapter.bind(f.level, BlockPos.ZERO, pattern));
        }
    }

    @Test void residentPatternsCannotAdvertiseCatalystReturnsOrSupplyExtraInputs() {
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT), List.of(Ingredient.of(Items.DIAMOND)))) {
            f.inventories.get(new BlockPos(-1, 0, 1)).setItem(0, new ItemStack(Items.DIAMOND));
            var pattern = pattern(List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1)));
            var binding = f.adapter.bind(f.level, BlockPos.ZERO, pattern);
            assertNotNull(binding);
            assertNull(f.adapter.bind(f.level, BlockPos.ZERO, pattern(List.of(stack(Items.IRON_INGOT, 1)),
                    List.of(stack(Items.GOLD_INGOT, 1), stack(Items.DIAMOND, 1)))));
            var extraInputs = reagentInputs();
            extraInputs[0].add(AEItemKey.of(Items.DIAMOND), 1);
            assertNull(f.adapter.planWithBinding(f.level, BlockPos.ZERO, pattern, extraInputs, binding.handle(), null));
            var borrowed = f.adapter.bind(f.level, BlockPos.ZERO, pattern(
                    List.of(stack(Items.IRON_INGOT, 1), stack(Items.DIAMOND, 1)),
                    List.of(stack(Items.GOLD_INGOT, 1), stack(Items.DIAMOND, 1))));
            assertNotNull(borrowed);
            assertFalse(f.adapter.canDispatch(f.level, BlockPos.ZERO, borrowed.handle()),
                    "borrowed mode must not overwrite preplaced catalysts");
        }
    }

    @Test void residentHarvestPreservesProductLikeCatalystsAndModeCanSwitchBackToBorrowed() {
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT), List.of(Ingredient.of(Items.GOLD_INGOT)))) {
            var pedestal = f.inventories.get(new BlockPos(-1, 0, 1));
            pedestal.setItem(0, new ItemStack(Items.GOLD_INGOT));
            var resident = pattern(List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1)));
            var binding = f.adapter.bind(f.level, BlockPos.ZERO, resident);
            assertNotNull(binding);
            var plan = f.adapter.planWithBinding(f.level, BlockPos.ZERO, resident,
                    reagentInputs(), binding.handle(), null);
            assertNotNull(plan);
            assertTrue(DispatchExecutor.execute(plan, null, ignored -> fail()).success());
            f.chamber.setItem(0, new ItemStack(Items.GOLD_INGOT));
            assertEquals(List.of(stack(Items.GOLD_INGOT, 1)),
                    f.adapter.extractOutputs(f.level, BlockPos.ZERO, f.filter, null));
            assertTrue(pedestal.getItem(0).is(Items.GOLD_INGOT), "even an allowed output remains on its pedestal");

            pedestal.clearContent();
            var borrowed = pattern(List.of(stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 1)),
                    List.of(stack(Items.GOLD_INGOT, 2)));
            binding = f.adapter.bind(f.level, BlockPos.ZERO, borrowed);
            assertNotNull(binding);
            var inputs = reagentInputs();
            inputs[0].add(AEItemKey.of(Items.GOLD_INGOT), 1);
            plan = f.adapter.planWithBinding(f.level, BlockPos.ZERO, borrowed, inputs, binding.handle(), null);
            assertNotNull(plan);
            assertTrue(DispatchExecutor.execute(plan, null, ignored -> fail()).success());
            f.chamber.setItem(0, new ItemStack(Items.GOLD_INGOT));
            var returned = f.adapter.extractOutputs(f.level, BlockPos.ZERO, f.filter, null);
            assertEquals(2, returned.stream().mapToLong(GenericStack::amount).sum());
            assertTrue(pedestal.isEmpty(), "a later borrowed dispatch clears resident mode");
        }
    }

    @Test void residentHarvestCanCollectFinishedProductAfterCatalystRemoval() {
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT), List.of(Ingredient.of(Items.DIAMOND)))) {
            var pedestalPos = new BlockPos(-1, 0, 1);
            f.inventories.get(pedestalPos).setItem(0, new ItemStack(Items.DIAMOND));
            var pattern = pattern(List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1)));
            var binding = f.adapter.bind(f.level, BlockPos.ZERO, pattern);
            assertNotNull(binding);
            var plan = f.adapter.planWithBinding(f.level, BlockPos.ZERO, pattern,
                    reagentInputs(), binding.handle(), null);
            assertNotNull(plan);
            assertTrue(DispatchExecutor.execute(plan, null, ignored -> fail()).success());
            f.chamber.setItem(0, new ItemStack(Items.GOLD_INGOT));
            f.inventories.get(pedestalPos).clearContent();
            when(f.level.isLoaded(pedestalPos)).thenReturn(false);
            assertEquals(List.of(stack(Items.GOLD_INGOT, 1)),
                    f.adapter.extractOutputs(f.level, BlockPos.ZERO, f.filter, null));
            assertFalse(f.adapter.canDispatch(f.level, BlockPos.ZERO, binding.handle()));
        }
    }

    private static KeyCounter[] reagentInputs() {
        var inputs = new KeyCounter[]{new KeyCounter()};
        inputs[0].add(AEItemKey.of(Items.IRON_INGOT), 1);
        return inputs;
    }

    @Test void bindingAndDispatchRejectMissingExtraAndWrongComponentReturns() {
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT), List.of(Ingredient.of(Items.DIAMOND)))) {
            var catalyst = new ItemStack(Items.DIAMOND);
            catalyst.set(DataComponents.CUSTOM_NAME, Component.literal("exact catalyst"));
            var exact = new GenericStack(AEItemKey.of(catalyst), 1);
            var inputs = List.of(stack(Items.IRON_INGOT, 1), exact);
            var valid = pattern(inputs, List.of(stack(Items.GOLD_INGOT, 1), exact));
            var binding = f.adapter.bind(f.level, BlockPos.ZERO, valid);
            assertNotNull(binding);
            for (var outputs : List.of(
                    List.of(stack(Items.GOLD_INGOT, 1), stack(Items.DIAMOND, 1)),
                    List.of(stack(Items.GOLD_INGOT, 1), new GenericStack(exact.what(), 2)),
                    List.of(stack(Items.GOLD_INGOT, 1), exact, stack(Items.EMERALD, 1)),
                    List.of(stack(Items.GOLD_INGOT, 2), exact))) {
                assertNull(f.adapter.bind(f.level, BlockPos.ZERO, pattern(inputs, outputs)));
            }
            var counters = new KeyCounter[]{new KeyCounter()};
            counters[0].add(AEItemKey.of(Items.IRON_INGOT), 1);
            counters[0].add(AEItemKey.of(Items.DIAMOND), 1);
            assertNull(f.adapter.planWithBinding(f.level, BlockPos.ZERO, valid, counters, binding.handle(), null),
                    "actual substituted catalyst must agree with the declared exact return");
        }
        assertFalse(matches(List.of(stack(Items.GOLD_INGOT, 1), stack(Items.DIAMOND, 1)),
                List.of(AEItemKey.of(Items.DIAMOND), AEItemKey.of(Items.DIAMOND))));
    }

    @Test void legacyProductOnlyAndNoCatalystPatternsStillBind() {
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT), List.of(Ingredient.of(Items.DIAMOND)))) {
            assertNotNull(f.adapter.bind(f.level, BlockPos.ZERO, pattern(
                    List.of(stack(Items.IRON_INGOT, 1), stack(Items.DIAMOND, 1)), List.of(stack(Items.GOLD_INGOT, 1)))));
        }
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT), List.of())) {
            assertNotNull(f.adapter.bind(f.level, BlockPos.ZERO, pattern(
                    List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1)))));
        }
    }

    @Test void repeatedAndProductOverlappingCatalystsCanBeMergedOrSplit() {
        var diamond = AEItemKey.of(Items.DIAMOND);
        assertTrue(matches(List.of(stack(Items.GOLD_INGOT, 1), stack(Items.DIAMOND, 2)), List.of(diamond, diamond)));
        assertTrue(matches(List.of(stack(Items.GOLD_INGOT, 1), stack(Items.DIAMOND, 1), stack(Items.DIAMOND, 1)),
                List.of(diamond, diamond)));
        assertTrue(matches(List.of(stack(Items.GOLD_INGOT, 3)),
                List.of(AEItemKey.of(Items.GOLD_INGOT), AEItemKey.of(Items.GOLD_INGOT))));
        assertFalse(matches(List.of(stack(Items.DIAMOND, 2), stack(Items.GOLD_INGOT, 1)), List.of(diamond, diamond)));
    }

    @Test void ambiguousReagentUsesTheAssignmentWhoseCatalystReturnsMatch() {
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT, Items.DIAMOND),
                List.of(Ingredient.of(Items.IRON_INGOT, Items.DIAMOND)))) {
            var inputs = List.of(stack(Items.IRON_INGOT, 1), stack(Items.DIAMOND, 1));
            assertNotNull(f.adapter.bind(f.level, BlockPos.ZERO, pattern(inputs,
                    List.of(stack(Items.GOLD_INGOT, 1), stack(Items.IRON_INGOT, 1)))));
            assertNotNull(f.adapter.bind(f.level, BlockPos.ZERO, pattern(inputs,
                    List.of(stack(Items.GOLD_INGOT, 1), stack(Items.DIAMOND, 1)))));
        }
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT), List.of(Ingredient.of(Items.IRON_INGOT)))) {
            assertNotNull(f.adapter.bind(f.level, BlockPos.ZERO, pattern(List.of(stack(Items.IRON_INGOT, 2)),
                    List.of(stack(Items.GOLD_INGOT, 1), stack(Items.IRON_INGOT, 1)))));
        }
    }

    @Test void idOnlyProductDoesNotRelaxCatalystComponentValidation() {
        var product = new ItemStack(Items.GOLD_INGOT);
        product.set(DataComponents.CUSTOM_NAME, Component.literal("product template"));
        var catalyst = new ItemStack(Items.DIAMOND);
        catalyst.set(DataComponents.CUSTOM_NAME, Component.literal("catalyst template"));
        var pattern = mock(IPatternDetails.class, withSettings().extraInterfaces(OverloadedPatternDetails.class));
        when(((OverloadedPatternDetails) pattern).isFuzzyOutput(0)).thenReturn(true);
        when(((OverloadedPatternDetails) pattern).isFuzzyOutput(1)).thenReturn(true);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(AEItemKey.of(product), 1),
                new GenericStack(AEItemKey.of(catalyst), 1)));
        assertTrue(ArsNouveauImbuementChamberAdapter.outputsMatch(pattern, new ItemStack(Items.GOLD_INGOT),
                List.of(AEItemKey.of(catalyst))));
        assertFalse(ArsNouveauImbuementChamberAdapter.outputsMatch(pattern, new ItemStack(Items.GOLD_INGOT),
                List.of(AEItemKey.of(Items.DIAMOND))));
    }

    @Test void oversizedInputsCannotWrapTheUnitLimit() {
        try (var f = new Fixture(Ingredient.of(Items.IRON_INGOT), List.of())) {
            var normal = pattern(List.of(stack(Items.IRON_INGOT, 1)), List.of(stack(Items.GOLD_INGOT, 1)));
            var binding = f.adapter.bind(f.level, BlockPos.ZERO, normal);
            assertNotNull(binding);
            var excessive = List.of(stack(Items.DIAMOND, 1), stack(Items.IRON_INGOT, Long.MAX_VALUE));
            assertNull(f.adapter.bind(f.level, BlockPos.ZERO, pattern(excessive, normal.getOutputs())));
            var counters = new KeyCounter[]{new KeyCounter(), new KeyCounter()};
            counters[0].add(AEItemKey.of(Items.DIAMOND), 1);
            counters[1].add(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE);
            assertNull(f.adapter.planWithBinding(f.level, BlockPos.ZERO, normal, counters, binding.handle(), null));
        }
    }

    private static boolean matches(List<GenericStack> outputs, List<AEItemKey> catalysts) {
        return ArsNouveauImbuementChamberAdapter.outputsMatch(pattern(List.of(), outputs),
                new ItemStack(Items.GOLD_INGOT), catalysts);
    }

    private static IPatternDetails pattern(List<GenericStack> inputs, List<GenericStack> outputs) {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getOutputs()).thenReturn(outputs);
        var slots = new ArrayList<IPatternDetails.IInput>();
        for (var stack : inputs) {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[]{stack});
            when(input.getMultiplier()).thenReturn(1L);
            slots.add(input);
        }
        when(pattern.getInputs()).thenReturn(slots.toArray(IPatternDetails.IInput[]::new));
        return pattern;
    }

    private static GenericStack stack(net.minecraft.world.item.Item item, long amount) {
        return new GenericStack(AEItemKey.of(item), amount);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ars_nouveau", path);
    }

    /** Mock only the optional-mod boundary; dispatch and extraction use real Minecraft inventories. */
    private static final class Fixture implements AutoCloseable {
        final ArsNouveauImbuementChamberAdapter adapter = new ArsNouveauImbuementChamberAdapter();
        final ServerLevel level = mock(ServerLevel.class);
        final SimpleContainer chamber = new SimpleContainer(1);
        final Map<BlockPos, SimpleContainer> inventories = new HashMap<>();
        final Map<BlockPos, BlockEntity> blocks = new HashMap<>();
        final AllowedOutputFilter filter = new AllowedOutputFilter();
        final MockedStatic<ModList> mods = mockStatic(ModList.class);
        final MockedStatic<ArsNouveauImbuementChamberAccess> access = mockStatic(ArsNouveauImbuementChamberAccess.class);

        @SuppressWarnings({"rawtypes", "unchecked"})
        Fixture(Ingredient reagent, List<Ingredient> catalysts) {
            var modList = mock(ModList.class);
            mods.when(ModList::get).thenReturn(modList);
            when(modList.isLoaded("ars_nouveau")).thenReturn(true);
            var recipe = mock(Recipe.class);
            when(recipe.getResultItem(any())).thenReturn(new ItemStack(Items.GOLD_INGOT));
            access.when(ArsNouveauImbuementChamberAccess::getImbuementRecipes)
                    .thenReturn(List.of(new RecipeHolder<>(id("catalyst_test"), recipe)));
            access.when(() -> ArsNouveauImbuementChamberAccess.getInput(recipe)).thenReturn(reagent);
            access.when(() -> ArsNouveauImbuementChamberAccess.getPedestalItems(recipe)).thenReturn(catalysts);
            access.when(() -> ArsNouveauImbuementChamberAccess.isImbuementTile(any())).thenAnswer(i -> i.getArgument(0) == blocks.get(BlockPos.ZERO));
            access.when(() -> ArsNouveauImbuementChamberAccess.isArcanePedestal(any())).thenReturn(true);
            addBlock(BlockPos.ZERO, chamber, chamberBlock);
            for (int i = 0; i < catalysts.size(); i++) addBlock(new BlockPos(i - 1, 0, 1), new SimpleContainer(1), pedestalBlock);
            when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
            when(level.getBlockEntity(any(BlockPos.class))).thenAnswer(i -> blocks.get(i.getArgument(0)));
            when(level.getCapability(eq(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK), any(BlockPos.class), isNull()))
                    .thenAnswer(i -> new InvWrapper(inventories.get(i.getArgument(1))));
            filter.allowStrict(AEItemKey.of(Items.GOLD_INGOT));
        }

        private void addBlock(BlockPos pos, SimpleContainer inventory, Block block) {
            var be = mock(BlockEntity.class, withSettings().extraInterfaces(Container.class));
            when(be.getBlockState()).thenReturn(block.defaultBlockState());
            when(be.getPersistentData()).thenReturn(new CompoundTag());
            var container = (Container) be;
            when(container.getContainerSize()).thenReturn(1);
            when(container.getItem(anyInt())).thenAnswer(i -> inventory.getItem(i.getArgument(0)));
            doAnswer(i -> { inventory.setItem(i.getArgument(0), i.getArgument(1)); return null; })
                    .when(container).setItem(anyInt(), any(ItemStack.class));
            blocks.put(pos, be);
            inventories.put(pos, inventory);
        }

        @Override public void close() { access.close(); mods.close(); }
    }
}
