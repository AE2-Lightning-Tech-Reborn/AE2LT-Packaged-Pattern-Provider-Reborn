package com.moakiee.ae2lt.packaged.gametest;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraAccess;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraCraftState;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraManagedMachine;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

@GameTestHolder("ae2ltpp")
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
            h.assertTrue(selected == first || selected == second, "Fixture did not exercise competing native recipes");
            var hidden = selected == first ? second : first;
            var start = ((Ingredient) NaturesAuraAccess.field(hidden, "startItem")).getItems()[0];
            var input = new GenericStack(AEItemKey.of(Items.MAGMA_CREAM), 1);
            var activation = new GenericStack(AEItemKey.of(start), 1);
            var output = hidden.getResultItem(h.getLevel().registryAccess());
            var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{input, activation},
                    new GenericStack[]{new GenericStack(AEItemKey.of(output), output.getCount())}), h.getLevel());
            var binding = f.adapter.bind(h.getLevel(), f.pos, pattern);
            h.assertTrue(binding != null, "Competing recipe fixture did not bind");
            var counter = new KeyCounter();
            counter.add(input.what(), input.amount());
            counter.add(activation.what(), activation.amount());
            h.assertTrue(f.adapter.planWithBinding(h.getLevel(), f.pos, pattern, new KeyCounter[]{counter},
                    binding.handle(), IActionSource.empty()) == null, "Adapter bypassed native recipe selection");
            h.assertTrue(f.inventory().getStackInSlot(0).isEmpty()
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
        h.assertTrue(f.harvest().isEmpty() && NaturesAuraCraftState.active(be), "Polling discarded unfinished altar");
        f.reload();
        h.runAfterDelay(160, () -> {
            h.assertTrue(f.harvest().equals(List.of(f.product)), "Altar did not return native recipe product");
            h.assertTrue(f.harvest().isEmpty(), "Altar returned product twice");
            h.assertTrue(!NaturesAuraCraftState.active(f.machine()), "Altar ownership was retained");
            h.assertTrue(f.inventory().getStackInSlot(0).isEmpty(), "Altar retained consumed input");
            h.succeed();
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 160)
    public static void offeringQueueSurvivesReloadAndReleasesAfterEarlyHarvest(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.OFFERING, "OFFERING_TABLE", "offering/sky_ingot");
        f.dispatch();
        h.assertTrue(NaturesAuraCraftState.offeringStart(f.machine()).isEmpty(), "Native offering did not consume paid start");
        f.reload();
        var collected = new ArrayList<GenericStack>();
        for (int i = 1; i <= 20; i++) {
            h.runAfterDelay(i * 5L, () -> collected.addAll(f.harvest()));
        }
        h.runAfterDelay(110, () -> {
            h.assertTrue(collected.equals(List.of(f.product)), "Offering result changed across reload: " + collected);
            h.assertTrue(!NaturesAuraCraftState.active(f.machine()), "Empty bonus queue retained ownership");
            h.assertTrue(f.inventory().getStackInSlot(0).isEmpty(), "Offering retained paid input");
            h.assertTrue(f.harvest().isEmpty(), "Offering returned product twice");
            h.succeed();
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 300)
    public static void treeRitualRunsNativeConsumptionAndResumesAfterReload(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.TREE, "TREE_RITUAL", "tree_ritual/ancient_sapling");
        f.dispatch();
        h.assertTrue(NaturesAuraAccess.recipe(f.machine()) != null, "Tree ritual was not installed");
        f.reload();
        h.runAfterDelay(240, () -> {
            h.assertTrue(f.harvest().equals(List.of(f.product)), "Tree ritual did not return native product");
            h.assertTrue(f.harvest().isEmpty(), "Tree ritual returned product twice");
            h.assertTrue(!f.machine().getPersistentData().contains(NaturesAuraCraftState.TREE_LINK), "Tree link was retained");
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
        h.assertTrue(before == after, "Cancellation refunded twice");
        h.assertTrue(!NaturesAuraCraftState.active(be) && NaturesAuraAccess.recipe(be) == null,
                "Cancellation left a running ritual");
        long copies = h.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).stream()
                .filter(item -> ItemStack.isSameItemSameTags(removed, item.getItem()))
                .mapToLong(item -> item.getItem().getCount()).sum();
        h.assertTrue(copies == 0, "Cancellation recreated manually removed input");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void cancelledBreakLeavesPaidAltarUntouched(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.ALTAR, "ALTAR", "altar/infused_iron");
        f.dispatch();
        var input = f.inventory().getStackInSlot(0).copy();
        Consumer<BlockEvent.BreakEvent> cancel = event -> {
            if (event.getLevel() == h.getLevel() && event.getPos().equals(f.pos)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, cancel);
        try {
            var event = new BlockEvent.BreakEvent(h.getLevel(), f.pos,
                    h.getLevel().getBlockState(f.pos), h.makeMockPlayer());
            h.assertTrue(MinecraftForge.EVENT_BUS.post(event), "Fixture did not cancel the break");
            h.assertTrue(ItemStack.matches(input, f.inventory().getStackInSlot(0)),
                    "Cancelled break removed the paid altar input");
            h.assertTrue(NaturesAuraCraftState.active(f.machine()), "Cancelled break discarded craft ownership");
            h.assertTrue(f.dropped(input) == 0, "Cancelled break refunded paid input");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 240)
    public static void destroyingAltarDropsCapturedProductExactlyOnce(GameTestHelper h) throws Exception {
        completedAltarRemoval(h, false);
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 240)
    public static void explodingAltarDropsCapturedProductExactlyOnce(GameTestHelper h) throws Exception {
        completedAltarRemoval(h, true);
    }

    private static void completedAltarRemoval(GameTestHelper h, boolean explode) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.ALTAR, "ALTAR", "altar/infused_iron");
        var be = f.machine();
        var container = NaturesAuraAccess.field(be, "container");
        container.getClass().getMethod("storeAura", int.class, boolean.class).invoke(container, 100000, false);
        f.dispatch();
        h.runAfterDelay(160, () -> {
            h.assertTrue(NaturesAuraCraftState.active(be) && f.inventory().getStackInSlot(0).isEmpty(),
                    "Fixture did not finish a paid altar craft");
            if (explode) {
                // Use the real explosion removal path without blasting the newly dropped item entities.
                var explosion = new Explosion(h.getLevel(), null, f.pos.getX() + .5, f.pos.getY() + .5,
                        f.pos.getZ() + .5, 1, false, Explosion.BlockInteraction.DESTROY);
                explosion.getToBlow().add(f.pos);
                explosion.finalizeExplosion(false);
            } else {
                h.assertTrue(h.getLevel().destroyBlock(f.pos, true), "Fixture did not destroy altar");
            }
            h.assertTrue(f.dropped(((AEItemKey) f.product.what()).toStack()) == f.product.amount(),
                    "Removing altar lost or duplicated its captured product");
            h.assertTrue(!NaturesAuraCraftState.active(be), "Removed altar retained paid craft state");
            h.succeed();
        });
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void treeReloadWithoutRecipeReleasesUnconsumedInputsOnce(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.TREE, "TREE_RITUAL", "tree_ritual/ancient_sapling");
        f.dispatch();
        var remaining = new KeyCounter();
        for (var stand : f.stands) {
            var inventory = (ItemStackHandler) NaturesAuraAccess.field(h.getLevel().getBlockEntity(stand), "items");
            var stack = inventory.getStackInSlot(0);
            if (!stack.isEmpty()) remaining.add(AEItemKey.of(stack), stack.getCount());
        }
        h.assertTrue(!remaining.isEmpty(), "Fixture did not retain ritual inputs");
        // A datapack update removed the recipe while the world was offline.
        f.reload(saved -> saved.putString("recipe", "ae2ltpp:removed_tree_ritual"));
        var restored = f.machine();
        var tick = restored.getClass().getMethod("tick");
        tick.invoke(restored);
        h.assertTrue(!NaturesAuraCraftState.active(restored) && NaturesAuraAccess.recipe(restored) == null,
                "Missing saved recipe left the ritual permanently owned");
        h.assertTrue(!restored.getPersistentData().contains(NaturesAuraCraftState.TREE_LINK),
                "Missing saved recipe retained the tree link");
        for (var entry : remaining) {
            h.assertTrue(f.dropped(((AEItemKey) entry.getKey()).toStack(1)) == entry.getLongValue(),
                    "Missing saved recipe did not refund exactly the unconsumed inputs");
        }
        for (var stand : f.stands) {
            var inventory = (ItemStackHandler) NaturesAuraAccess.field(h.getLevel().getBlockEntity(stand), "items");
            h.assertTrue(inventory.getStackInSlot(0).isEmpty(), "Refunded input remained on a stand");
        }
        tick.invoke(restored);
        for (var entry : remaining) {
            h.assertTrue(f.dropped(((AEItemKey) entry.getKey()).toStack(1)) == entry.getLongValue(),
                    "Missing saved recipe refunded an input twice");
        }
        h.assertTrue(f.harvest().isEmpty(), "Missing saved recipe produced an unpaid result");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void destroyingOfferingDropsPaidQueueExactlyOnce(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.OFFERING, "OFFERING_TABLE", "offering/sky_ingot");
        f.dispatch();
        var be = f.machine();
        h.assertTrue(h.getLevel().destroyBlock(f.pos, true), "Fixture did not destroy offering table");
        h.assertTrue(f.dropped(((AEItemKey) f.product.what()).toStack()) == f.product.amount(),
                "Removing offering table lost or duplicated its paid output queue");
        h.assertTrue(!NaturesAuraCraftState.active(be), "Removed offering table retained paid craft state");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void destroyingSecondaryStandRefundsRemainingRitualInputsOnce(GameTestHelper h) throws Exception {
        removeSecondaryStand(h, false);
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void playerBreakingSecondaryStandRefundsRemainingRitualInputsOnce(GameTestHelper h) throws Exception {
        removeSecondaryStand(h, true);
    }

    private static void removeSecondaryStand(GameTestHelper h, boolean playerBreak) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.TREE, "TREE_RITUAL", "tree_ritual/ancient_sapling");
        f.dispatch();
        var stands = f.stands;
        var expected = new KeyCounter();
        for (var stand : stands) {
            var inventory = (ItemStackHandler) NaturesAuraAccess.field(h.getLevel().getBlockEntity(stand), "items");
            for (int i = 0; i < inventory.getSlots(); i++) {
                var stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty()) expected.add(AEItemKey.of(stack), stack.getCount());
            }
        }
        var secondary = stands.stream().filter(pos -> !pos.equals(f.pos)).findFirst().orElseThrow();
        var be = f.machine();
        if (playerBreak) {
            var state = h.getLevel().getBlockState(secondary);
            state.getBlock().playerWillDestroy(h.getLevel(), secondary, state, h.makeMockPlayer());
        }
        h.assertTrue(h.getLevel().destroyBlock(secondary, true), "Fixture did not destroy secondary stand");
        h.assertTrue(!NaturesAuraCraftState.active(be) && NaturesAuraAccess.recipe(be) == null,
                "Removing secondary stand left a running paid ritual");
        for (var entry : expected) {
            h.assertTrue(f.dropped(((AEItemKey) entry.getKey()).toStack()) == entry.getLongValue(),
                    "Removing secondary stand lost or duplicated an unconsumed input");
        }
        NaturesAuraCraftState.dropPending(h.getLevel(), f.pos);
        for (var entry : expected) {
            h.assertTrue(f.dropped(((AEItemKey) entry.getKey()).toStack()) == entry.getLongValue(),
                    "Removed ritual refunded an input twice");
        }
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void playerBreakingAltarRefundsPaidInputExactlyOnce(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.ALTAR, "ALTAR", "altar/infused_iron");
        f.dispatch();
        var be = f.machine();
        var input = f.inventory().getStackInSlot(0).copy();
        var state = h.getLevel().getBlockState(f.pos);
        // Follow the committed player-break callbacks, including Nature's Aura's native inventory drop.
        state.getBlock().playerWillDestroy(h.getLevel(), f.pos, state, h.makeMockPlayer());
        h.assertTrue(h.getLevel().removeBlock(f.pos, false), "Fixture did not remove altar");
        h.assertTrue(f.dropped(input) == input.getCount(),
                "Player break lost or duplicated the paid altar input: " + f.dropped(input));
        h.assertTrue(!NaturesAuraCraftState.active(be), "Player break retained paid craft state");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void removingUnrelatedStandPreservesNearbyPaidRitual(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.TREE, "TREE_RITUAL", "tree_ritual/ancient_sapling");
        f.dispatch();
        BlockPos unrelated = null;
        for (var first : f.stands) {
            for (var second : f.stands) {
                var candidate = f.pos.offset(first.subtract(second));
                var relative = h.relativePos(candidate);
                if (!f.stands.contains(candidate) && h.getLevel().getBlockState(candidate).isAir()
                        && relative.getX() >= 0 && relative.getX() < 16
                        && relative.getZ() >= 0 && relative.getZ() < 16) {
                    unrelated = candidate;
                    break;
                }
            }
            if (unrelated != null) break;
        }
        h.assertTrue(unrelated != null, "Fixture did not find an unrelated nearby stand position");
        var input = f.inventory().getStackInSlot(0).copy();
        h.getLevel().setBlockAndUpdate(unrelated, f.machine().getBlockState());
        h.assertTrue(h.getLevel().destroyBlock(unrelated, false), "Fixture did not remove unrelated stand");
        h.assertTrue(NaturesAuraCraftState.active(f.machine()) && NaturesAuraAccess.recipe(f.machine()) != null,
                "Removing unrelated stand cancelled a nearby paid ritual");
        h.assertTrue(ItemStack.matches(input, f.inventory().getStackInSlot(0)) && f.dropped(input) == 0,
                "Removing unrelated stand refunded nearby ritual inputs");
        h.succeed();
    }

    @GameTest(template = "adapterconnectiontests.adapter_conn", timeoutTicks = 80)
    public static void waterloggingAltarPreservesPaidCraft(GameTestHelper h) throws Exception {
        var f = new Fixture(h, NaturesAuraAdapter.Kind.ALTAR, "ALTAR", "altar/infused_iron");
        f.dispatch();
        var be = f.machine();
        var input = f.inventory().getStackInSlot(0).copy();
        h.getLevel().setBlockAndUpdate(f.pos, be.getBlockState().cycle(BlockStateProperties.WATERLOGGED));
        h.assertTrue(f.machine() == be && NaturesAuraCraftState.active(be),
                "Same-block update discarded paid craft ownership");
        h.assertTrue(ItemStack.matches(input, f.inventory().getStackInSlot(0)) && f.dropped(input) == 0,
                "Same-block update refunded the paid input");
        h.succeed();
    }

    private static final class Fixture {
        final GameTestHelper h;
        final BlockPos center;
        final BlockPos pos;
        final NaturesAuraAdapter adapter;
        final List<GenericStack> inputs = new ArrayList<>();
        final List<BlockPos> stands = new ArrayList<>();
        final GenericStack product;
        final IPatternDetails pattern;

        Fixture(GameTestHelper h, NaturesAuraAdapter.Kind kind, String structureName, String recipeName) throws Exception {
            this.h = h;
            center = h.absolutePos(new BlockPos(7, 3, 7));
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                h.getLevel().setBlockAndUpdate(center.offset(x, -1, z), Blocks.DIRT.defaultBlockState());
            }
            var structure = Class.forName("de.ellpeck.naturesaura.blocks.multi.Multiblocks").getField(structureName).get(null);
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
                    inputs.add(new GenericStack(AEItemKey.of(BuiltInRegistries.ITEM.get(
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
            h.assertTrue(machine() instanceof NaturesAuraManagedMachine, "Nature's Aura Mixin did not apply");
            adapter = new NaturesAuraAdapter(kind);
            var result = recipe.getResultItem(h.getLevel().registryAccess());
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

        long dropped(ItemStack stack) {
            return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(8)).stream()
                    .filter(item -> ItemStack.isSameItemSameTags(stack, item.getItem()))
                    .mapToLong(item -> item.getItem().getCount()).sum();
        }

        void dispatch() {
            var binding = adapter.bind(h.getLevel(), pos, pattern);
            h.assertTrue(binding != null, "Native recipe failed to bind");
            var counter = new KeyCounter();
            for (var input : inputs) counter.add(input.what(), input.amount());
            var plan = adapter.planWithBinding(h.getLevel(), pos, pattern, new KeyCounter[]{counter},
                    binding.handle(), IActionSource.empty());
            h.assertTrue(plan != null, "Native recipe failed to plan");
            h.assertTrue(DispatchExecutor.execute(plan, IActionSource.empty(), new PatternProviderReturnInventory(() -> {}))
                    .success(), "Native recipe failed to dispatch");
        }

        List<GenericStack> harvest() {
            var filter = new AllowedOutputFilter();
            filter.allowStrict(product.what());
            return adapter.extractOutputs(h.getLevel(), pos, filter, IActionSource.empty());
        }

        void reload() {
            reload(saved -> {});
        }

        void reload(Consumer<CompoundTag> edit) {
            var original = machine();
            var saved = original.saveWithFullMetadata();
            edit.accept(saved);
            var replacement = original.getType().create(pos, original.getBlockState());
            h.getLevel().removeBlockEntity(pos);
            replacement.load(saved);
            h.getLevel().setBlockEntity(replacement);
        }
    }
}
