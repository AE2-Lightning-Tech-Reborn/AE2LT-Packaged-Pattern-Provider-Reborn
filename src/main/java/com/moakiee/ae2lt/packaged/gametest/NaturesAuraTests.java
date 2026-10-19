package com.moakiee.ae2lt.packaged.gametest;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.iface.PatternProviderReturnInventory;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraAccess;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraCraftState;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraManagedMachine;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

@GameTestHolder("ae2ltpp_naturesaura")
@PrefixGameTestTemplate(false)
public final class NaturesAuraTests {
    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void offeringCannotBypassTheNativeFirstInputMatch(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.OFFERING, "OFFERING_TABLE", "offering/sky_ingot");
        var manager = h.getLevel().getRecipeManager();
        var original = new ArrayList<>(manager.getRecipes());
        var constructor = Class.forName("de.ellpeck.naturesaura.recipes.OfferingRecipe")
                .getConstructor(ResourceLocation.class, Ingredient.class, Ingredient.class, ItemStack.class);
        var first = (Recipe<?>) constructor.newInstance(new ResourceLocation("ae2ltpp", "offering_first"),
                Ingredient.of(Items.MAGMA_CREAM), Ingredient.of(Items.DIAMOND), new ItemStack(Items.IRON_INGOT));
        var second = (Recipe<?>) constructor.newInstance(new ResourceLocation("ae2ltpp", "offering_second"),
                Ingredient.of(Items.MAGMA_CREAM), Ingredient.of(Items.GOLD_INGOT), new ItemStack(Items.GOLD_INGOT));
        var extended = new ArrayList<>(original);
        extended.add(first);
        extended.add(second);
        try {
            manager.replaceRecipes(extended);
            var selector = f.machine().getClass().getDeclaredMethod("getRecipe", ItemStack.class);
            selector.setAccessible(true);
            var selected = selector.invoke(f.machine(), new ItemStack(Items.MAGMA_CREAM));
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(selected == first || selected == second, "Fixture did not exercise competing native recipes");
            var hidden = selected == first ? second : first;
            var start = ((Ingredient) NaturesAuraAccess.field(hidden, "startItem")).getItems()[0];
            var input = new GenericStack(AEItemKey.of(Items.MAGMA_CREAM), 1);
            var activation = new GenericStack(AEItemKey.of(start), 1);
            var output = hidden.getResultItem();
            var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{input, activation},
                    new GenericStack[]{new GenericStack(AEItemKey.of(output), output.getCount())}), h.getLevel());
            var binding = f.adapter.bind(h.getLevel(), f.pos, pattern);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(binding != null, "Competing recipe fixture did not bind");
            var counter = new KeyCounter();
            counter.add(input.what(), input.amount());
            counter.add(activation.what(), activation.amount());
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(f.adapter.planWithBinding(h.getLevel(), f.pos, pattern, new KeyCounter[]{counter},
                    binding.handle(), IActionSource.empty()) == null, "Adapter bypassed native recipe selection");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(f.inventory().getStackInSlot(0).isEmpty()
                    && !NaturesAuraCraftState.active(f.machine()), "Rejected recipe mutated offering table");
        } finally {
            manager.replaceRecipes(original);
        }
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 240)
    public static void altarConsumesAuraAndReturnsExactlyOneNativeProduct(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.ALTAR, "ALTAR", "altar/infused_iron");
        var be = f.machine();
        var container = NaturesAuraAccess.field(be, "container");
        container.getClass().getMethod("storeAura", int.class, boolean.class).invoke(container, 100000, false);
        f.dispatch();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(f.harvest().isEmpty() && NaturesAuraCraftState.active(be), "Polling discarded unfinished altar");
        f.reload();
        h.runAfterDelay(160, () -> {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(f.harvest().equals(List.of(f.product)), "Altar did not return native recipe product");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(f.harvest().isEmpty(), "Altar returned product twice");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!NaturesAuraCraftState.active(f.machine()), "Altar ownership was retained");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(f.inventory().getStackInSlot(0).isEmpty(), "Altar retained consumed input");
            h.succeed();
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 160)
    public static void offeringQueueSurvivesReloadAndReleasesAfterEarlyHarvest(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.OFFERING, "OFFERING_TABLE", "offering/sky_ingot");
        f.dispatch();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(NaturesAuraCraftState.offeringStart(f.machine()).isEmpty(), "Native offering did not consume paid start");
        f.reload();
        var collected = new ArrayList<GenericStack>();
        for (int i = 1; i <= 20; i++) {
            h.runAfterDelay(i * 5L, () -> collected.addAll(f.harvest()));
        }
        h.runAfterDelay(110, () -> {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(collected.equals(List.of(f.product)), "Offering result changed across reload: " + collected);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!NaturesAuraCraftState.active(f.machine()), "Empty bonus queue retained ownership");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(f.inventory().getStackInSlot(0).isEmpty(), "Offering retained paid input");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(f.harvest().isEmpty(), "Offering returned product twice");
            h.succeed();
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 300)
    public static void treeRitualRunsNativeConsumptionAndResumesAfterReload(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.TREE, "TREE_RITUAL", "tree_ritual/ancient_sapling");
        f.dispatch();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(NaturesAuraAccess.recipe(f.machine()) != null, "Tree ritual was not installed");
        f.reload();
        h.runAfterDelay(240, () -> {
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(f.harvest().equals(List.of(f.product)), "Tree ritual did not return native product");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(f.harvest().isEmpty(), "Tree ritual returned product twice");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!f.machine().getPersistentData().contains(NaturesAuraCraftState.TREE_LINK), "Tree link was retained");
            h.succeed();
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void cancelledTreeDoesNotRefundManuallyRemovedInputsOrRefundTwice(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.TREE, "TREE_RITUAL", "tree_ritual/ancient_sapling");
        f.dispatch();
        var be = f.machine();
        var removed = f.inventory().getStackInSlot(0).copy();
        f.inventory().setStackInSlot(0, ItemStack.EMPTY);
        var bounds = new AABB(f.center).inflate(8);
        NaturesAuraCraftState.dropPending(h.getLevel(), f.pos);
        int before = h.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).stream()
                .mapToInt(item -> item.getItem().getCount()).sum();
        NaturesAuraCraftState.dropPending(h.getLevel(), f.pos);
        int after = h.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).stream()
                .mapToInt(item -> item.getItem().getCount()).sum();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(before == after, "Cancellation refunded twice");
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(!NaturesAuraCraftState.active(be) && NaturesAuraAccess.recipe(be) == null,
                "Cancellation left a running ritual");
        long copies = h.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).stream()
                .filter(item -> ItemStack.isSameItemSameTags(removed, item.getItem()))
                .mapToLong(item -> item.getItem().getCount()).sum();
        com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(copies == 0, "Cancellation recreated manually removed input");
        h.succeed();
    }

    private static final class Fixture {
        final GameTestHelper h;
        final BlockPos center;
        final BlockPos pos;
        final NaturesAuraAdapter adapter;
        final List<GenericStack> inputs = new ArrayList<>();
        final GenericStack product;
        final IPatternDetails pattern;

        Fixture(GameTestHelper h, NaturesAuraAdapter.Kind kind, String structureName, String recipeName) throws Exception {
            this.h = h;
            center = h.absolutePos(new BlockPos(7, 3, 7));
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                h.getLevel().setBlockAndUpdate(center.offset(x, -1, z), Blocks.DIRT.defaultBlockState());
            }
            var structure = Class.forName("de.ellpeck.naturesaura.blocks.multi.Multiblocks").getField(structureName).get(null);
            var stands = new ArrayList<BlockPos>();
            BiFunction<BlockPos, Object, Boolean> place = (position, matcher) -> {
                try {
                    var state = (BlockState) matcher.getClass().getMethod("defaultState").invoke(matcher);
                    if (state != null) h.getLevel().setBlockAndUpdate(position, state);
                    return true;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            };
            structure.getClass().getMethod("forEach", BlockPos.class, char.class, BiFunction.class)
                    .invoke(structure, center, '\0', place);
            var recipe = h.getLevel().getRecipeManager().byKey(new ResourceLocation("naturesaura", recipeName)).orElseThrow();
            if (kind == NaturesAuraAdapter.Kind.TREE) {
                BiFunction<BlockPos, Object, Boolean> stand = (position, matcher) -> { stands.add(position); return true; };
                structure.getClass().getMethod("forEach", BlockPos.class, char.class, BiFunction.class)
                        .invoke(structure, center, 'W', stand);
                for (var ingredient : (Ingredient[]) NaturesAuraAccess.field(recipe, "ingredients")) add(ingredient);
                add((Ingredient) NaturesAuraAccess.field(recipe, "saplingType"));
                h.getLevel().setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
                BiFunction<BlockPos, Object, Boolean> powder = (position, matcher) -> {
                    h.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                    inputs.add(new GenericStack(AEItemKey.of(Registry.ITEM.get(
                            new ResourceLocation("naturesaura", "gold_powder"))), 1));
                    return true;
                };
                structure.getClass().getMethod("forEach", BlockPos.class, char.class, BiFunction.class)
                        .invoke(structure, center, 'G', powder);
                pos = stands.get(0);
            } else {
                pos = center;
                add((Ingredient) NaturesAuraAccess.field(recipe, "input"));
                if (kind == NaturesAuraAdapter.Kind.OFFERING) add((Ingredient) NaturesAuraAccess.field(recipe, "startItem"));
            }
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(machine() instanceof NaturesAuraManagedMachine, "Nature's Aura Mixin did not apply");
            adapter = new NaturesAuraAdapter(kind);
            var result = recipe.getResultItem();
            product = new GenericStack(AEItemKey.of(result), result.getCount());
            pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    inputs.toArray(GenericStack[]::new), new GenericStack[]{product}), h.getLevel());
        }

        private void add(Ingredient ingredient) {
            var input = ingredient.getItems()[0];
            inputs.add(new GenericStack(AEItemKey.of(input), input.getCount()));
        }

        BlockEntity machine() { return h.getLevel().getBlockEntity(pos); }
        ItemStackHandler inventory() { return (ItemStackHandler) NaturesAuraAccess.field(machine(), "items"); }

        void dispatch() {
            var binding = adapter.bind(h.getLevel(), pos, pattern);
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(binding != null, "Native recipe failed to bind");
            var counter = new KeyCounter();
            for (var input : inputs) counter.add(input.what(), input.amount());
            var plan = adapter.planWithBinding(h.getLevel(), pos, pattern, new KeyCounter[]{counter},
                    binding.handle(), IActionSource.empty());
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(plan != null, "Native recipe failed to plan");
            com.moakiee.ae2lt.packaged.compat.GameTestAssertions.assertTrue(DispatchExecutor.execute(plan, IActionSource.empty(), new PatternProviderReturnInventory(() -> {}))
                    .success(), "Native recipe failed to dispatch");
        }

        List<GenericStack> harvest() {
            var filter = new AllowedOutputFilter();
            filter.allowStrict(product.what());
            return adapter.extractOutputs(h.getLevel(), pos, filter, IActionSource.empty());
        }

        void reload() {
            var original = machine();
            var saved = original.saveWithFullMetadata();
            var replacement = original.getType().create(pos, original.getBlockState());
            h.getLevel().removeBlockEntity(pos);
            replacement.load(saved);
            h.getLevel().setBlockEntity(replacement);
        }
    }
}
