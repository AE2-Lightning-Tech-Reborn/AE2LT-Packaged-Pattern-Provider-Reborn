package com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import de.ellpeck.naturesaura.api.multiblock.IMultiblock;
import de.ellpeck.naturesaura.blocks.ModBlocks;
import de.ellpeck.naturesaura.blocks.multi.Multiblocks;
import de.ellpeck.naturesaura.blocks.tiles.BlockEntityNatureAltar;
import de.ellpeck.naturesaura.blocks.tiles.BlockEntityWoodStand;
import de.ellpeck.naturesaura.items.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class NaturesAuraGameTests {
    @GameTest(template = "natures_aura_test", timeoutTicks = 240)
    public static void wirelessProviderBindsAnyStandAndOwnsTheReturn(GameTestHelper h) {
        var center = forest(h, 0);
        var stands = NaturesAuraAccess.positions("TREE_RITUAL", center, 'W');
        var anchor = stands.getLast();
        var providerPos = h.absolutePos(new BlockPos(2, 4, 2));
        var level = h.getLevel();
        level.setBlockAndUpdate(providerPos, com.moakiee.ae2lt.packaged.registry.PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        level.setBlockAndUpdate(providerPos.west(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity) level.getBlockEntity(providerPos);
        provider.setReturnMode(com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode.AUTO);
        provider.getAdapterInv().setItemDirect(0, com.moakiee.ae2lt.packaged.registry.PPItems.NATURES_AURA_ALTAR_ADAPTER.toStack());
        h.assertTrue(!provider.acceptsWirelessTarget(level, anchor), "wrong core cannot bind a wood stand");
        provider.getAdapterInv().setItemDirect(0, com.moakiee.ae2lt.packaged.registry.PPItems.NATURES_AURA_TREE_ADAPTER.toStack());
        for (var stand : stands) h.assertTrue(provider.acceptsWirelessTarget(level, stand), "wireless validation accepts every ritual stand");
        h.assertTrue(provider.addOrUpdateConnection(level.dimension(), anchor, net.minecraft.core.Direction.UP), "nonleader stand connected");
        var inputs = treeInputs(16);
        var encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(item(Items.EMERALD, 2)));
        var logic = (com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic) provider.getLogic();
        logic.getPatternInv().setItemDirect(0, encoded);
        h.runAfterDelay(50, () -> {
            logic.updatePatterns();
            h.assertTrue(logic.getAvailablePatterns().size() == 1, "real provider advertises the native pattern");
            var pattern = logic.getAvailablePatterns().getFirst();
            h.assertTrue(logic.pushPattern(pattern, counters(inputs)), "real wireless lane accepts paid inputs");
            grow(h, center);
            h.runAfterDelay(120, () -> {
                var returned = new ArrayList<ItemStack>(); logic.addDrops(returned);
                h.assertTrue(returned.stream().filter(s -> s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum() == 2,
                        "physical native output reached provider return inventory: " + returned);
                var saved = new net.minecraft.nbt.CompoundTag(); logic.writeToNBT(saved, level.registryAccess());
                logic.clearContent(); logic.readFromNBT(saved, level.registryAccess());
                returned.clear(); logic.addDrops(returned);
                h.assertTrue(returned.stream().filter(s -> s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum() == 2,
                        "provider restart preserves exactly one craft");
                h.succeed();
            });
        });
    }

    @GameTest(template = "natures_aura_test", timeoutTicks = 80)
    public static void breakingCompletedMachineDropsItsBufferedProduct(GameTestHelper h) {
        var pos = altar(h); var adapter = new NaturesAuraAdapter(NaturesAuraAdapter.Kind.ALTAR);
        var inputs = List.of(item(Items.AMETHYST_SHARD, 1));
        dispatch(h, adapter, pos, pattern(h, inputs, item(Items.DIAMOND, 1)), inputs);
        ((BlockEntityNatureAltar) h.getLevel().getBlockEntity(pos)).container.storeAura(300000, false);
        h.runAfterDelay(20, () -> {
            h.getLevel().destroyBlock(pos, true);
            var dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
            h.assertTrue(dropped.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum() == 1,
                    "completed product survives machine break without duplication");
            h.succeed();
        });
    }

    @GameTest(template = "natures_aura_test")
    public static void breakingOfferingPreservesTheNativeEmissionQueue(GameTestHelper h) {
        var pos = offering(h, true); var adapter = new NaturesAuraAdapter(NaturesAuraAdapter.Kind.OFFERING);
        var input = List.of(item(ModItems.INFUSED_IRON, 1), item(ModItems.CALLING_SPIRIT, 1));
        dispatch(h, adapter, pos, pattern(h, input, item(ModItems.SKY_INGOT, 1)), input);
        h.getLevel().destroyBlock(pos, true);
        var dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        h.assertTrue(dropped.stream().filter(e -> e.getItem().is(ModItems.SKY_INGOT)).mapToInt(e -> e.getItem().getCount()).sum() == 1,
                "not-yet-emitted native product survives immediate table break");
        h.assertTrue(dropped.stream().filter(e -> e.getItem().is(Items.BLACK_DYE)).mapToInt(e -> e.getItem().getCount()).sum() >= 4,
                "native bonuses are preserved too");
        h.assertTrue(dropped.stream().noneMatch(e -> e.getItem().is(ModItems.CALLING_SPIRIT) || e.getItem().is(ModItems.INFUSED_IRON)),
                "consumed inputs are not refunded beside their outputs");
        h.succeed();
    }

    @GameTest(template = "natures_aura_test")
    public static void threeCoresUseNativeRecipes(GameTestHelper h) {
        h.assertTrue(MultiblockAdapterRegistry.activeAdapters().stream().filter(NaturesAuraAdapter.class::isInstance).count() == 3,
                "all three registrations are independent");
        for (var path : List.of("altar", "tree_ritual", "offering")) {
            var recipe = h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("ae2ltpp",
                    "packaged_core/naturesaura_" + path + "_packaged_core"));
            h.assertTrue(recipe.isPresent() && recipe.get().value().getClass().getName().startsWith("de.ellpeck.naturesaura.recipes."),
                    "native core recipe loaded: " + path);
        }
        h.succeed();
    }

    @GameTest(template = "natures_aura_test", timeoutTicks = 160)
    public static void altarNeedsRealAuraAndPersistsFinishedOutput(GameTestHelper h) {
        var pos = altar(h);
        var adapter = new NaturesAuraAdapter(NaturesAuraAdapter.Kind.ALTAR);
        var inputs = List.of(item(Items.AMETHYST_SHARD, 1));
        var pattern = pattern(h, inputs, item(Items.DIAMOND, 1));
        dispatch(h, adapter, pos, pattern, inputs);
        h.runAfterDelay(25, () -> {
            h.assertTrue(harvest(h, adapter, pos).isEmpty(), "ambient Aura alone cannot pay 20,000 per tick");
            var be = (BlockEntityNatureAltar) h.getLevel().getBlockEntity(pos);
            h.assertTrue(be.items.getStackInSlot(0).is(Items.AMETHYST_SHARD), "input remains while starved");
            be.container.storeAura(300000, false);
            h.runAfterDelay(20, () -> {
                reload(h, pos);
                var result = harvest(h, adapter, pos);
                h.assertTrue(count(result, Items.DIAMOND) == 1, "native result persisted across reload: " + result);
                h.assertTrue(harvest(h, adapter, pos).isEmpty(), "result removed exactly once");
                h.assertTrue(((BlockEntityNatureAltar) h.getLevel().getBlockEntity(pos)).items.getStackInSlot(0).isEmpty(), "native input consumed");
                dispatch(h, adapter, pos, pattern, inputs);
                ((BlockEntityNatureAltar) h.getLevel().getBlockEntity(pos)).container.storeAura(300000, false);
                h.runAfterDelay(20, () -> {
                    h.assertTrue(count(harvest(h, adapter, pos), Items.DIAMOND) == 1, "same binding repeats");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "natures_aura_test", timeoutTicks = 140)
    public static void altarCatalystAndSameKeyOutputAreNative(GameTestHelper h) {
        var pos = altar(h);
        var adapter = new NaturesAuraAdapter(NaturesAuraAdapter.Kind.ALTAR);
        var inputs = List.of(item(Items.PRISMARINE_SHARD, 1));
        var pattern = pattern(h, inputs, item(Items.PRISMARINE_SHARD, 1));
        var binding = adapter.bind(h.getLevel(), pos, pattern);
        h.assertTrue(binding != null, "same-key recipe can bind without early harvesting");
        h.assertTrue(adapter.planWithBinding(h.getLevel(), pos, pattern, counters(inputs), binding.handle(), null) == null,
                "missing catalyst blocks dispatch");
        h.getLevel().setBlockAndUpdate(pos.offset(2, 1, 2), Blocks.DIAMOND_BLOCK.defaultBlockState());
        h.runAfterDelay(41, () -> {
            ((BlockEntityNatureAltar) h.getLevel().getBlockEntity(pos)).container.storeAura(100000, false);
            dispatch(h, adapter, pos, pattern, inputs);
            h.assertTrue(harvest(h, adapter, pos).isEmpty(), "identical input/output key is not completion");
            h.runAfterDelay(45, () -> {
                h.assertTrue(count(harvest(h, adapter, pos), Items.PRISMARINE_SHARD) == 1, "native completion captured before a second recipe can reuse it");
                h.assertTrue(h.getLevel().getBlockState(pos.offset(2, 1, 2)).is(Blocks.DIAMOND_BLOCK), "resident catalyst retained");
                h.succeed();
            });
        });
    }

    @GameTest(template = "natures_aura_test")
    public static void invalidPatternsAndCommitRacesDoNotSpendInputs(GameTestHelper h) {
        var pos = altar(h); var adapter = new NaturesAuraAdapter(NaturesAuraAdapter.Kind.ALTAR);
        var inputs = List.of(item(Items.AMETHYST_SHARD, 1));
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern(h, inputs, item(Items.DIAMOND, 2))) == null, "wrong output count rejected");
        var pattern = pattern(h, inputs, item(Items.DIAMOND, 1));
        var binding = adapter.bind(h.getLevel(), pos, pattern);
        h.assertTrue(adapter.planWithBinding(h.getLevel(), pos, pattern, counters(List.of(item(Items.DIRT, 1))), binding.handle(), null) == null,
                "live input revalidated");
        var plan = adapter.planWithBinding(h.getLevel(), pos, pattern, counters(inputs), binding.handle(), null);
        var be = (BlockEntityNatureAltar) h.getLevel().getBlockEntity(pos);
        be.items.setStackInSlot(0, Items.STICK.getDefaultInstance());
        var retained = new ArrayList<GenericStack>();
        h.assertTrue(DispatchExecutor.execute(plan, null, retained::addAll).failure() && retained.isEmpty(),
                "failed commit rolls back escrow and reports no paid input");
        h.assertTrue(be.items.getStackInSlot(0).is(Items.STICK) && !NaturesAuraCraftState.active(be), "manual contents unchanged");
        h.succeed();
    }

    @GameTest(template = "natures_aura_test", timeoutTicks = 100)
    public static void offeringConsumesStartItemAndReturnsNativeBonuses(GameTestHelper h) {
        var pos = offering(h, true); var adapter = new NaturesAuraAdapter(NaturesAuraAdapter.Kind.OFFERING);
        var inputs = List.of(item(ModItems.INFUSED_IRON, 1), item(ModItems.CALLING_SPIRIT, 1));
        var pattern = pattern(h, inputs, item(ModItems.SKY_INGOT, 1));
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern(h, List.of(item(ModItems.INFUSED_IRON, 1)), item(ModItems.SKY_INGOT, 1))) == null,
                "start item is paid, not invented");
        var manualStart = new ItemEntity(h.getLevel(), pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                ModItems.CALLING_SPIRIT.getDefaultInstance());
        manualStart.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        manualStart.setNoPickUpDelay();
        h.getLevel().addFreshEntity(manualStart);
        dispatch(h, adapter, pos, pattern, inputs);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).stream()
                .filter(e -> e.getItem().is(ModItems.CALLING_SPIRIT)).allMatch(e -> e == manualStart), "paid start is never a world entity");
        h.assertTrue(NaturesAuraAccess.items(h.getLevel().getBlockEntity(pos)).getStackInSlot(0).isEmpty()
                        && NaturesAuraCraftState.offeringStart(h.getLevel().getBlockEntity(pos)).isEmpty(),
                "native offering consumes ingredient and paid start in the same dispatch tick");
        var unrelated = new ItemEntity(h.getLevel(), pos.getX() + 2, pos.getY() + 1, pos.getZ(), Items.DIAMOND.getDefaultInstance());
        h.getLevel().addFreshEntity(unrelated);
        h.runAfterDelay(65, () -> {
            reload(h, pos);
            var result = harvest(h, adapter, pos);
            h.assertTrue(count(result, ModItems.SKY_INGOT) == 1 && count(result, Items.BLACK_DYE) >= 4,
                    "native product and Wither Rose bonuses: " + result + "; slot="
                            + NaturesAuraAccess.items(h.getLevel().getBlockEntity(pos)).getStackInSlot(0)
                            + "; state=" + h.getLevel().getBlockEntity(pos).getPersistentData());
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                    .filter(e -> e.getItem().is(ModItems.CALLING_SPIRIT)).mapToInt(e -> e.getItem().getCount()).sum() == 1
                    && manualStart.isAlive(), "manual start item was not consumed in place of the paid one");
            h.assertTrue(unrelated.isAlive() && harvest(h, adapter, pos).isEmpty(), "unrelated entity and duplicate extraction untouched");
            h.succeed();
        });
    }

    @GameTest(template = "natures_aura_test", timeoutTicks = 160)
    public static void forestPatternPowderAnyStandAndMidRitualReload(GameTestHelper h) {
        treeCycle(h, 0, 16, true);
    }

    @GameTest(template = "natures_aura_test", timeoutTicks = 160)
    public static void forestWorldPowderDoesNotNeedPatternPowder(GameTestHelper h) {
        treeCycle(h, 16, 0, false);
    }

    @GameTest(template = "natures_aura_test", timeoutTicks = 160)
    public static void forestPartialPowderIsFilledExactly(GameTestHelper h) {
        treeCycle(h, 7, 9, false);
    }

    private static void treeCycle(GameTestHelper h, int worldPowder, int patternPowder, boolean reload) {
        var center = forest(h, worldPowder);
        var adapter = new NaturesAuraAdapter(NaturesAuraAdapter.Kind.TREE);
        var inputs = treeInputs(patternPowder);
        var pattern = pattern(h, inputs, item(Items.EMERALD, 2));
        var stands = NaturesAuraAccess.positions("TREE_RITUAL", center, 'W');
        for (var stand : stands) {
            var binding = adapter.bind(h.getLevel(), stand, pattern);
            h.assertTrue(binding != null && adapter.canDispatch(h.getLevel(), stand, binding.handle()), "each of eight stands binds: " + stand);
        }
        var anchor = stands.getLast();
        dispatch(h, adapter, anchor, pattern, inputs);
        h.assertTrue(powderCount(h, center) == 16 && h.getLevel().getBlockState(center).is(Blocks.OAK_SAPLING), "exact ring and paid sapling placed");
        h.assertTrue(harvest(h, adapter, anchor).isEmpty(), "unstarted ritual has no output");
        grow(h, center);
        h.runAfterDelay(45, () -> {
            for (var stand : stands) h.assertTrue(NaturesAuraAccess.items(h.getLevel().getBlockEntity(stand)).getStackInSlot(0).isEmpty(),
                    "native midpoint consumed all three ingredients");
            var binding = adapter.bind(h.getLevel(), stands.get(2), pattern);
            h.assertTrue(binding != null && !adapter.canDispatch(h.getLevel(), stands.get(2), binding.handle()), "another bound stand cannot overlap the active craft");
            if (reload) {
                var leader = h.getLevel().getBlockEntity(stands.getFirst());
                var tag = leader.saveWithoutMetadata(h.getLevel().registryAccess());
                h.assertTrue(tag.getString("recipe").equals("ae2ltpp:na_forest"), "native save stores recipe id, not RecipeHolder.toString");
                reload(h, stands.getFirst());
                reload(h, anchor);
            }
            h.runAfterDelay(50, () -> {
                var result = harvest(h, adapter, anchor);
                h.assertTrue(count(result, Items.EMERALD) == 2, "native forest result returned through bound nonleader stand: " + result);
                h.assertTrue(powderCount(h, center) == 0 && h.getLevel().getBlockState(center).isAir(), "native tree and all powder consumed");
                h.assertTrue(harvest(h, adapter, anchor).isEmpty(), "no duplicate result after reload");
                h.succeed();
            });
        });
    }

    @GameTest(template = "natures_aura_test")
    public static void forestMissingExcessAndObstructedPowderReject(GameTestHelper h) {
        var center = forest(h, 16); var adapter = new NaturesAuraAdapter(NaturesAuraAdapter.Kind.TREE);
        var anchor = NaturesAuraAccess.positions("TREE_RITUAL", center, 'W').get(3);
        var input = treeInputs(16); var pattern = pattern(h, input, item(Items.EMERALD, 2));
        var binding = adapter.bind(h.getLevel(), anchor, pattern);
        h.assertTrue(binding != null && !adapter.canDispatch(h.getLevel(), anchor, binding.handle()), "extra encoded powder is not discarded");
        for (var pos : NaturesAuraAccess.positions("TREE_RITUAL", center, 'G')) h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        var noPowder = treeInputs(0); var noPowderPattern = pattern(h, noPowder, item(Items.EMERALD, 2));
        h.assertTrue(!adapter.canDispatch(h.getLevel(), anchor, adapter.bind(h.getLevel(), anchor, noPowderPattern).handle()), "no free powder");
        var missingSapling = List.of(item(Items.QUARTZ, 2), item(Items.LAPIS_LAZULI, 1), item(ModBlocks.GOLD_POWDER.asItem(), 16));
        h.assertTrue(adapter.bind(h.getLevel(), anchor, pattern(h, missingSapling, item(Items.EMERALD, 2))) == null, "sapling must be in pattern");
        h.getLevel().setBlockAndUpdate(NaturesAuraAccess.positions("TREE_RITUAL", center, 'G').getFirst(), Blocks.STONE.defaultBlockState());
        h.assertTrue(adapter.planWithBinding(h.getLevel(), anchor, pattern, counters(input), binding.handle(), null) == null, "obstruction is never replaced");
        h.assertTrue(h.getLevel().getBlockState(center).isAir(), "rejected plan does not plant a free sapling");
        h.succeed();
    }

    @GameTest(template = "natures_aura_test", timeoutTicks = 40)
    public static void cancelledForestKeepsUnconsumedItemsAndReleasesOwnership(GameTestHelper h) {
        var center = forest(h, 0); var adapter = new NaturesAuraAdapter(NaturesAuraAdapter.Kind.TREE);
        var stands = NaturesAuraAccess.positions("TREE_RITUAL", center, 'W');
        var anchor = stands.getLast(); var inputs = treeInputs(16);
        dispatch(h, adapter, anchor, pattern(h, inputs, item(Items.EMERALD, 2)), inputs);
        grow(h, center);
        h.getLevel().setBlockAndUpdate(NaturesAuraAccess.positions("TREE_RITUAL", center, 'G').getFirst(), Blocks.AIR.defaultBlockState());
        h.runAfterDelay(10, () -> {
            h.assertTrue(!NaturesAuraCraftState.active(h.getLevel().getBlockEntity(stands.getFirst()))
                    && !h.getLevel().getBlockEntity(anchor).getPersistentData().contains(NaturesAuraCraftState.TREE_LINK), "native cancellation releases both machine and anchor flags");
            h.assertTrue(stands.stream().mapToInt(p -> NaturesAuraAccess.items(h.getLevel().getBlockEntity(p)).getStackInSlot(0).getCount()).sum() == 3,
                    "unconsumed native ingredients remain in the world exactly once");
            h.assertTrue(harvest(h, adapter, anchor).isEmpty(), "cancellation cannot create a result");
            h.succeed();
        });
    }

    @GameTest(template = "natures_aura_test", timeoutTicks = 80)
    public static void manualAltarCompletionIsNotCaptured(GameTestHelper h) {
        var pos = altar(h); var adapter = new NaturesAuraAdapter(NaturesAuraAdapter.Kind.ALTAR);
        var be = (BlockEntityNatureAltar) h.getLevel().getBlockEntity(pos);
        be.items.setStackInSlot(0, Items.AMETHYST_SHARD.getDefaultInstance());
        be.container.storeAura(300000, false);
        h.runAfterDelay(20, () -> {
            h.assertTrue(be.items.getStackInSlot(0).is(Items.DIAMOND) && harvest(h, adapter, pos).isEmpty(), "manual craft retains native output behavior");
            h.succeed();
        });
    }

    private static BlockPos altar(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(9, 4, 9));
        build(h, Multiblocks.ALTAR, pos);
        h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof NaturesAuraManagedMachine, "native altar hooks applied");
        return pos;
    }

    private static BlockPos offering(GameTestHelper h, boolean wither) {
        var pos = h.absolutePos(new BlockPos(9, 4, 9));
        build(h, Multiblocks.OFFERING_TABLE, pos);
        Multiblocks.OFFERING_TABLE.forEach(pos, 'R', (flower, matcher) -> {
            h.getLevel().setBlockAndUpdate(flower.below(), Blocks.DIRT.defaultBlockState());
            h.getLevel().setBlockAndUpdate(flower, (wither ? Blocks.WITHER_ROSE : Blocks.POPPY).defaultBlockState());
            return true;
        });
        return pos;
    }

    private static BlockPos forest(GameTestHelper h, int powder) {
        var center = h.absolutePos(new BlockPos(9, 4, 9));
        for (var pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, -1, 4))) {
            h.getLevel().setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        }
        for (var pos : NaturesAuraAccess.positions("TREE_RITUAL", center, 'W')) h.getLevel().setBlockAndUpdate(pos, ModBlocks.WOOD_STAND.defaultBlockState());
        var ring = NaturesAuraAccess.positions("TREE_RITUAL", center, 'G');
        h.assertTrue(ring.size() == 16, "native ring has sixteen positions");
        for (int i = 0; i < powder; i++) h.getLevel().setBlockAndUpdate(ring.get(i), ModBlocks.GOLD_POWDER.defaultBlockState());
        return center;
    }

    private static void build(GameTestHelper h, IMultiblock structure, BlockPos center) {
        var start = structure.getStart(center);
        structure.getMatchers().forEach((offset, matcher) -> h.getLevel().setBlockAndUpdate(start.offset(offset), matcher.defaultState()));
    }

    private static void grow(GameTestHelper h, BlockPos center) {
        var random = RandomSource.create(42);
        for (int i = 0; i < 10 && h.getLevel().getBlockState(center).is(Blocks.OAK_SAPLING); i++) {
            ((SaplingBlock) Blocks.OAK_SAPLING).advanceTree(h.getLevel(), center, h.getLevel().getBlockState(center), random);
        }
        h.assertTrue(h.getLevel().getBlockState(center).is(Blocks.OAK_LOG), "real sapling growth event produced an oak tree");
    }

    private static void reload(GameTestHelper h, BlockPos pos) {
        var old = h.getLevel().getBlockEntity(pos);
        var tag = old.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored = BlockEntity.loadStatic(pos, old.getBlockState(), tag, h.getLevel().registryAccess());
        h.assertTrue(restored != null, "native BE reconstructed before it receives its level");
        // Saving again before the first server tick must not erase a pending native recipe.
        restored = BlockEntity.loadStatic(pos, old.getBlockState(),
                restored.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.getLevel().removeBlockEntity(pos);
        h.getLevel().setBlockEntity(restored);
    }

    private static List<GenericStack> treeInputs(int powder) {
        var result = new ArrayList<>(List.of(item(Items.QUARTZ, 2), item(Items.LAPIS_LAZULI, 1), item(Items.OAK_SAPLING, 1)));
        if (powder > 0) result.add(item(ModBlocks.GOLD_POWDER.asItem(), powder));
        return List.copyOf(result);
    }

    private static int powderCount(GameTestHelper h, BlockPos center) {
        return (int) NaturesAuraAccess.positions("TREE_RITUAL", center, 'G').stream().filter(p -> h.getLevel().getBlockState(p).is(ModBlocks.GOLD_POWDER)).count();
    }

    private static void dispatch(GameTestHelper h, NaturesAuraAdapter adapter, BlockPos pos, IPatternDetails pattern, List<GenericStack> inputs) {
        var binding = adapter.bind(h.getLevel(), pos, pattern);
        h.assertTrue(binding != null, "native recipe binds");
        var plan = adapter.planWithBinding(h.getLevel(), pos, pattern, counters(inputs), binding.handle(), null);
        h.assertTrue(plan != null, "ready native target has dispatch plan");
        for (var target : plan.targets()) for (var stack : target.stacks()) {
            h.assertTrue(target.customInserter().apply(stack, appeng.api.config.Actionable.SIMULATE) == stack.amount(), "simulation accepts exact amount");
        }
        h.assertTrue(!NaturesAuraCraftState.active(h.getLevel().getBlockEntity(pos)), "simulation does not start a craft");
        var retained = new ArrayList<GenericStack>();
        h.assertTrue(DispatchExecutor.execute(plan, null, retained::addAll).success() && retained.isEmpty(), "paid inputs committed exactly once");
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "busy target rejects overlapping dispatch");
    }

    private static List<GenericStack> harvest(GameTestHelper h, NaturesAuraAdapter adapter, BlockPos pos) {
        return adapter.extractOutputs(h.getLevel(), pos, new AllowedOutputFilter(), null);
    }
    private static IPatternDetails pattern(GameTestHelper h, List<GenericStack> input, GenericStack output) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(input, List.of(output)), h.getLevel());
    }
    private static KeyCounter[] counters(List<GenericStack> inputs) {
        return inputs.stream().map(stack -> { var counter = new KeyCounter(); counter.add(stack.what(), stack.amount()); return counter; }).toArray(KeyCounter[]::new);
    }
    private static GenericStack item(Item item, long count) { return new GenericStack(AEItemKey.of(item), count); }
    private static long count(List<GenericStack> stacks, Item item) { return stacks.stream().filter(s -> s.what().equals(AEItemKey.of(item))).mapToLong(GenericStack::amount).sum(); }
}
