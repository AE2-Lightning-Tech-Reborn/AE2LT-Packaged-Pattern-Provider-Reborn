package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.*;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

class ExtendedCraftingCombinationAdapterTest {
    private static Block coreBlock, pedestalBlock;
    @BeforeAll static void boot() {
        com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap.initialize();
        var registry = (net.minecraftforge.registries.ForgeRegistry<Block>)
                net.minecraftforge.registries.ForgeRegistries.BLOCKS;
        ((net.minecraft.core.MappedRegistry<Block>) BuiltInRegistries.BLOCK).unfreeze();
        registry.unfreeze();
        try {
            coreBlock = Registry.register(BuiltInRegistries.BLOCK,
                    new ResourceLocation("extendedcrafting", "crafting_core"),
                    new Block(BlockBehaviour.Properties.of()));
            pedestalBlock = Registry.register(BuiltInRegistries.BLOCK,
                    new ResourceLocation("extendedcrafting", "pedestal"),
                    new Block(BlockBehaviour.Properties.of()));
        } finally {
            BuiltInRegistries.BLOCK.freeze();
            registry.freeze();
        }
    }


    @Test void sixteenOrdersOnlyDispatchOneRecipeInTheSameTick() throws Exception {
        try (var f = new Fixture(16, Items.DIAMOND)) {
            // Even if another pattern outputs the center ingredient, the live
            // recipe must protect it before the machine has advanced a tick.
            f.filter.allowStrict(AEItemKey.of(Items.DIAMOND));
            int accepted = 0;
            for (int i = 0; i < 16; i++) {
                assertTrue(f.harvest().isEmpty());
                if (f.adapter.canDispatch(f.level, BlockPos.ZERO, f.handle, f.scope)) {
                    int checksBeforeCommit = f.recipeChecks.get();
                    f.dispatch();
                    assertEquals(checksBeforeCommit + 1, f.recipeChecks.get(),
                            "commit must immediately refresh the real optional-mod recipe API");
                    accepted++;
                }
            }
            assertEquals(1, accepted);
            assertEquals(4, f.pedestalIngots());
            assertEquals(Items.DIAMOND, f.coreInventory.getStackInSlot(0).getItem());
        }
    }

    @Test void noPowerRemainsProtectedAcrossTicks() throws Exception {
        try (var f = new Fixture(16, Items.DIAMOND)) {
            f.dispatch();
            // An input may also appear as another pattern's output.
            f.filter.allowStrict(AEItemKey.of(Items.DIAMOND));
            for (int tick = 0; tick <= 40; tick++) {
                f.tick.set(tick);
                assertTrue(f.harvest().isEmpty());
                assertFalse(f.adapter.canDispatch(f.level, BlockPos.ZERO, f.handle, f.scope));
            }
            assertEquals(4, f.pedestalIngots());
            assertEquals(1, f.coreInventory.getStackInSlot(0).getCount());
        }
    }

    @Test void completedRecipeCanReturnInSameTickWithoutObservingPositiveProgress() throws Exception {
        try (var f = new Fixture(16)) {
            f.dispatch();
            f.complete();
            assertEquals(List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)), f.harvest());
            assertTrue(f.adapter.canDispatch(f.level, BlockPos.ZERO, f.handle, f.scope));
            assertEquals(0, f.level.getGameTime());
        }
    }

    @Test void sixteenSequentialRecipesCanCompleteInTheSameTick() throws Exception {
        try (var f = new Fixture(16)) {
            int completed = 0;
            for (int i = 0; i < 16; i++) {
                f.dispatch();
                f.complete();
                completed += f.harvest().stream().mapToInt(s -> (int) s.amount()).sum();
            }
            assertEquals(16, completed);
            assertEquals(0, f.pedestalIngots());
            assertTrue(f.coreInventory.getStackInSlot(0).isEmpty());
            assertEquals(0, f.level.getGameTime());
        }
    }

    @Test void providerAdaptersShareLiveMachineStateWithinTheSameTick() throws Exception {
        try (var f = new Fixture(16)) {
            f.dispatch();
            f.filter.allowStrict(AEItemKey.of(Items.DIAMOND));
            var other = new ExtendedCraftingCombinationAdapter();
            var otherScope = new MemoryScope();
            assertFalse(other.canDispatch(f.level, BlockPos.ZERO, f.handle, otherScope));
            assertNull(other.planWithBinding(f.level, BlockPos.ZERO, null, f.inputs(), f.handle, null, otherScope));
            assertTrue(other.extractOutputs(f.level, BlockPos.ZERO, f.filter, null, otherScope).isEmpty());

            f.complete();
            assertTrue(other.extractOutputs(f.level, BlockPos.ZERO, f.filter, null, otherScope).isEmpty(),
                    "another provider cannot claim this output");
            assertEquals(List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)), f.harvest());
            assertTrue(other.canDispatch(f.level, BlockPos.ZERO, f.handle, otherScope));
            var plan = other.planWithBinding(f.level, BlockPos.ZERO, null, f.inputs(), f.handle, null, otherScope);
            assertNotNull(plan);
            assertTrue(DispatchExecutor.execute(plan, null,
                    new appeng.helpers.patternprovider.PatternProviderReturnInventory(() -> {}),
                    ignored -> fail("unexpected retained inputs")).success());
            assertFalse(f.adapter.canDispatch(f.level, BlockPos.ZERO, f.handle, f.scope));
            assertTrue(f.harvest().isEmpty());
            assertEquals(4, f.pedestalIngots());
            assertEquals(Items.DIAMOND, f.coreInventory.getStackInSlot(0).getItem());
            assertEquals(0, f.level.getGameTime());
        }
    }

    @Test void replacingTheBlockEntityUsesItsCurrentInventory() throws Exception {
        try (var f = new Fixture(16)) {
            f.dispatch();
            f.clearInventories();
            var replacement = mock(Class.forName("com.blakebr0.extendedcrafting.tileentity.CraftingCoreTileEntity")
                    .asSubclass(BlockEntity.class));
            when(replacement.getBlockState()).thenReturn(coreBlock.defaultBlockState());
            f.blocks.put(BlockPos.ZERO, replacement);
            f.inventories.put(replacement, new ItemStackHandler(1));
            assertTrue(f.adapter.canDispatch(f.level, BlockPos.ZERO, f.handle, new MemoryScope()));
        }
    }

    @Test void existingPedestalInputsBlockDispatchEvenWithEmptyCore() throws Exception {
        try (var f = new Fixture(16)) {
            f.inventories.entrySet().stream().filter(e -> e.getKey() != f.core).findFirst().orElseThrow()
                    .getValue().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            assertFalse(f.adapter.canDispatch(f.level, BlockPos.ZERO, f.handle, f.scope));
            assertNull(f.adapter.planWithBinding(f.level, BlockPos.ZERO, null, f.inputs(), f.handle, null, f.scope));
        }
    }

    @Test void unknownProgressPreventsBothDispatchAndHarvest() throws Exception {
        try (var f = new Fixture(4)) {
            f.ownOutput();
            f.progress.set(-1);
            assertFalse(f.adapter.canDispatch(f.level, BlockPos.ZERO, f.handle, f.scope));
            f.coreInventory.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
            assertTrue(f.harvest().isEmpty());
        }
    }

    @Test void positiveProgressDoesNotHarvestEvenWhenCurrentRecipeCannotMatch() throws Exception {
        try (var f = new Fixture(4)) {
            f.ownOutput();
            f.progress.set(1);
            f.coreInventory.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
            assertTrue(f.harvest().isEmpty());
        }
    }

    @Test void idleUnrelatedCenterItemIsNotAnOutput() throws Exception {
        try (var f = new Fixture(4)) {
            f.ownOutput();
            f.coreInventory.setStackInSlot(0, new ItemStack(Items.DIAMOND));
            assertTrue(f.harvest().isEmpty());
            assertEquals(Items.DIAMOND, f.coreInventory.getStackInSlot(0).getItem());
        }
    }

    @Test void unreadableRecipeStateFailsClosed() throws Exception {
        try (var f = new Fixture(4)) {
            f.ownOutput();
            f.coreInventory.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
            when(f.core.getClass().getMethod("getActiveRecipe").invoke(f.core))
                    .thenThrow(new IllegalStateException("optional mod unavailable"));
            assertTrue(f.harvest().isEmpty());
            assertEquals(1, f.coreInventory.getStackInSlot(0).getCount());
        }
    }

    @Test void recipeRefreshFailureDoesNotRefundOrRepeatCommittedInputs() throws Exception {
        try (var f = new Fixture(16, Items.DIAMOND)) {
            when(f.core.getClass().getMethod("getActiveRecipe").invoke(f.core))
                    .thenThrow(new IllegalStateException("optional mod unavailable"));
            f.dispatch();
            f.filter.allowStrict(AEItemKey.of(Items.DIAMOND));
            assertFalse(f.adapter.canDispatch(f.level, BlockPos.ZERO, f.handle, f.scope));
            assertTrue(f.harvest().isEmpty());
            f.tick.incrementAndGet();
            assertTrue(f.harvest().isEmpty());
            assertEquals(4, f.pedestalIngots());
        }
    }

    @Test void incompletePedestalAreaCannotAcceptACraft() throws Exception {
        try (var f = new Fixture(16)) {
            when(f.level.isLoaded(new BlockPos(-3, 0, -3))).thenReturn(false);
            assertFalse(f.adapter.canDispatch(f.level, BlockPos.ZERO, f.handle, f.scope));
        }
    }

    static class MemoryScope implements AdapterPersistentScope {
        final Map<String, String> states = new HashMap<>();
        final Set<String> flags = new HashSet<>();
        public void setFlag(BlockPos pos, String key) { flags.add(key); }
        public boolean hasFlag(BlockPos pos, String key) { return flags.contains(key); }
        public void clearFlag(BlockPos pos, String key) { flags.remove(key); }
        public void setState(BlockPos pos, String key, String value) { states.put(key, value); }
        public String getState(BlockPos pos, String key) { return states.get(key); }
        public void clearState(BlockPos pos, String key) { states.remove(key); }
    }

    static class Fixture implements AutoCloseable {
        final ExtendedCraftingCombinationAdapter adapter = new ExtendedCraftingCombinationAdapter();
        final ServerLevel level = mock(ServerLevel.class);
        final BlockEntity core;
        final ItemStackHandler coreInventory = new ItemStackHandler(1);
        final Map<BlockPos, BlockEntity> blocks = new HashMap<>();
        final Map<BlockEntity, ItemStackHandler> inventories = new IdentityHashMap<>();
        final MemoryScope scope = new MemoryScope();
        final AllowedOutputFilter filter = new AllowedOutputFilter();
        final MockedStatic<ModList> modList = mockStatic(ModList.class);
        final MockedStatic<CucumberInventoryAccess> cucumber = mockStatic(CucumberInventoryAccess.class);
        final java.util.concurrent.atomic.AtomicLong tick = new java.util.concurrent.atomic.AtomicLong();
        final java.util.concurrent.atomic.AtomicInteger progress = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger recipeChecks = new java.util.concurrent.atomic.AtomicInteger();
        final net.minecraft.world.item.Item outputItem;
        final Object recipe;
        final Object handle;
        Fixture(int pedestalCount) throws Exception { this(pedestalCount, Items.GOLD_INGOT); }
        Fixture(int pedestalCount, net.minecraft.world.item.Item outputItem) throws Exception {
            this.outputItem = outputItem;
            core = mock(Class.forName("com.blakebr0.extendedcrafting.tileentity.CraftingCoreTileEntity")
                    .asSubclass(BlockEntity.class));
            var recipeClass = Class.forName("com.blakebr0.extendedcrafting.crafting.recipe.CombinationRecipe");
            var ingredients = net.minecraft.core.NonNullList.withSize(5, Ingredient.of(Items.IRON_INGOT));
            ingredients.set(0, Ingredient.of(Items.DIAMOND));
            recipe = recipeClass.getConstructor(ResourceLocation.class, net.minecraft.core.NonNullList.class,
                    ItemStack.class, int.class, int.class).newInstance(new ResourceLocation("test", "combination"),
                    ingredients, new ItemStack(outputItem), 1000, 100);
            var mods = mock(ModList.class);
            modList.when(ModList::get).thenReturn(mods);
            when(mods.isLoaded("extendedcrafting")).thenReturn(true);
            when(core.getBlockState()).thenReturn(coreBlock.defaultBlockState());
            when(core.getClass().getMethod("getProgress").invoke(core)).thenAnswer(a -> progress.get());
            when(core.getClass().getMethod("getActiveRecipe").invoke(core)).thenAnswer(a -> {
                recipeChecks.incrementAndGet();
                return coreInventory.getStackInSlot(0).is(Items.DIAMOND) && pedestalIngots() == 4
                        ? recipe : null;
            });
            blocks.put(BlockPos.ZERO, core);
            inventories.put(core, coreInventory);
            int added = 0;
            for (int x = -3; x <= 3 && added < pedestalCount; x++) {
                for (int z = -3; z <= 3 && added < pedestalCount; z++) {
                    if (x == 0 && z == 0) continue;
                    var pos = new BlockPos(x, 0, z);
                    var be = mock(BlockEntity.class);
                    when(be.getBlockState()).thenReturn(pedestalBlock.defaultBlockState());
                    blocks.put(pos, be);
                    inventories.put(be, new ItemStackHandler(1));
                    added++;
                }
            }
            when(level.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
            when(level.getGameTime()).thenAnswer(a -> tick.get());
            when(level.getBlockEntity(any(BlockPos.class))).thenAnswer(a -> blocks.get(a.getArgument(0)));
            when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
            when(level.getBlockState(any(BlockPos.class))).thenAnswer(a -> {
                var be = blocks.get(a.getArgument(0));
                return be == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : be.getBlockState();
            });
            cucumber.when(() -> CucumberInventoryAccess.get(any(BlockEntity.class), any()))
                    .thenAnswer(a -> inventories.get(a.getArgument(0)));
            var c = Class.forName(ExtendedCraftingCombinationAdapter.class.getName() + "$CombinationBindHandle")
                    .getDeclaredConstructor(Object.class);
            c.setAccessible(true);
            handle = c.newInstance(recipe);
            filter.allowStrict(AEItemKey.of(outputItem));
        }
        KeyCounter[] inputs() {
            var iron = new KeyCounter(); iron.add(AEItemKey.of(Items.IRON_INGOT), 4);
            var center = new KeyCounter(); center.add(AEItemKey.of(Items.DIAMOND), 1);
            return new KeyCounter[]{iron, center};
        }
        int pedestalIngots() {
            return inventories.entrySet().stream().filter(e -> e.getKey() != core)
                    .mapToInt(e -> e.getValue().getStackInSlot(0).getCount()).sum();
        }
        void dispatch() {
            assertTrue(adapter.canDispatch(level, BlockPos.ZERO, handle, scope));
            var plan = adapter.planWithBinding(level, BlockPos.ZERO, null, inputs(), handle, null, scope);
            assertNotNull(plan);
            assertTrue(DispatchExecutor.execute(plan, null,
                    new appeng.helpers.patternprovider.PatternProviderReturnInventory(() -> {}),
                    ignored -> fail("unexpected retained inputs")).success());
        }
        List<GenericStack> harvest() { return adapter.extractOutputs(level, BlockPos.ZERO, filter, null, scope); }
        void clearInventories() { inventories.values().forEach(inv -> inv.setStackInSlot(0, ItemStack.EMPTY)); }
        void complete() { clearInventories(); coreInventory.setStackInSlot(0, new ItemStack(outputItem)); }
        void ownOutput() {
            SlotHarvestOwnership.store(level, BlockPos.ZERO, scope, "ec_combination:harvest",
                    new ItemStack(outputItem), new ResourceLocation("test", "combination"));
        }
        public void close() { cucumber.close(); modList.close(); }
    }
}
