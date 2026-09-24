package com.moakiee.ae2lt.packaged.logic.multiblock.astral;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.structure.PatternInfuser;
import hellfirepvp.astralsorcery.common.structure.PatternAltarT4;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import hellfirepvp.astralsorcery.common.tile.TileInfuser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class AstralGameTests {
    @GameTest(template = "astral_test", timeoutTicks = 180)
    public static void relayCraftReloadAndRepeat(GameTestHelper h) {
        var pos = altar(h);
        var offset = TileAltar.getRelayGridOffsets().get(0);
        h.getLevel().setBlockAndUpdate(pos.offset(offset), BlocksAS.FOCUS_RELAY.get().defaultBlockState());
        var provider = provider(h);
        var inputs = List.of(item(Items.IRON_INGOT, 1), item(Items.GOLD_INGOT, 1));
        var pattern = pattern(h, inputs, item(Items.DIAMOND, 1));
        var adapter = new AstralSorceryAdapter(true);
        h.assertTrue(MultiblockAdapterRegistry.find(h.getLevel(), pos, h.getLevel().getBlockEntity(pos)) != null,
                "altar registered without colliding with infuser");
        dispatch(h, pos, provider, adapter, pattern, inputs);
        var saved = provider.saveWithoutMetadata(h.getLevel().registryAccess());
        provider.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(adapter.extractOutputs(h.getLevel(), pos, filter(Items.DIAMOND), null, provider).isEmpty(), "no early output");
        h.runAfterDelay(55, () -> {
            var result = adapter.extractOutputs(h.getLevel(), pos, filter(Items.DIAMOND), null, provider);
            h.assertTrue(count(result, Items.DIAMOND) == 1, "one native relay output after provider reload: " + result);
            h.assertTrue(AstralAccess.inventory(h.getLevel().getBlockEntity(pos.offset(offset)), false).getStackInSlot(0).isEmpty(), "relay input consumed");
            h.assertTrue(adapter.extractOutputs(h.getLevel(), pos, filter(Items.DIAMOND), null, provider).isEmpty(), "no duplicate harvest");
            dispatch(h, pos, provider, adapter, pattern, inputs);
            h.runAfterDelay(55, () -> {
                h.assertTrue(count(adapter.extractOutputs(h.getLevel(), pos, filter(Items.DIAMOND), null, provider), Items.DIAMOND) == 1,
                        "second native cycle succeeds");
                h.succeed();
            });
        });
    }

    @GameTest(template = "astral_test", timeoutTicks = 160)
    public static void additionalInputsAreCapturedAndConsumed(GameTestHelper h) {
        var pos = altar(h); var provider = provider(h); var adapter = new AstralSorceryAdapter(true);
        var inputs = List.of(item(Items.IRON_INGOT, 1), item(Items.GOLD_INGOT, 3));
        var pattern = pattern(h, inputs, item(Items.EMERALD, 2));
        dispatch(h, pos, provider, adapter, pattern, inputs);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4)).stream()
                .mapToInt(e -> e.getItem().is(Items.GOLD_INGOT) ? e.getItem().getCount() : 0).sum() == 3,
                "all three extra items physically spawned");
        h.runAfterDelay(110, () -> {
            var result = adapter.extractOutputs(h.getLevel(), pos, filter(Items.EMERALD), null, provider);
            h.assertTrue(count(result, Items.EMERALD) == 2, "native additional-input craft finished: " + result);
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4)).stream()
                    .noneMatch(e -> e.getItem().is(Items.GOLD_INGOT)), "no refunded or duplicated extra material");
            h.succeed();
        });
    }

    @GameTest(template = "astral_test", timeoutTicks = 110)
    public static void containerRemainderAndManualCraftOwnership(GameTestHelper h) {
        var pos = altar(h); var provider = provider(h); var adapter = new AstralSorceryAdapter(true);
        var inputs = List.of(item(Items.WATER_BUCKET, 1));
        var pattern = pattern(h, inputs, item(Items.LAPIS_LAZULI, 1));
        dispatch(h, pos, provider, adapter, pattern, inputs);
        h.runAfterDelay(55, () -> {
            var result = adapter.extractOutputs(h.getLevel(), pos, filter(Items.LAPIS_LAZULI), null, provider);
            h.assertTrue(count(result, Items.LAPIS_LAZULI) == 1 && count(result, Items.BUCKET) == 1,
                    "return actual product and native container remainder: " + result);
            var manual = new ItemEntity(h.getLevel(), pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, Items.LAPIS_LAZULI.getDefaultInstance());
            h.getLevel().addFreshEntity(manual);
            h.assertTrue(adapter.extractOutputs(h.getLevel(), pos, filter(Items.LAPIS_LAZULI), null, provider).isEmpty() && manual.isAlive(),
                    "finished ownership does not scavenge a later manual output");
            h.succeed();
        });
    }

    @GameTest(template = "astral_test", timeoutTicks = 100)
    public static void infuserUsesRealStructureAndConsumesWorldFluid(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(8, 4, 8));
        new PatternInfuser().place(h.getLevel(), pos);
        var provider = provider(h); var adapter = new AstralSorceryAdapter(false);
        var inputs = List.of(item(Items.IRON_INGOT, 1));
        var pattern = pattern(h, inputs, item(Items.GOLD_INGOT, 2));
        var binding = adapter.bind(h.getLevel(), pos, pattern);
        h.assertTrue(binding != null, "bind infuser recipe");
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "missing fluid prevents input transfer");
        for (var offset : TileInfuser.getLiquidOffsets()) h.getLevel().setBlockAndUpdate(pos.offset(offset), Blocks.WATER.defaultBlockState());
        dispatch(h, pos, provider, adapter, pattern, inputs);
        h.runAfterDelay(55, () -> {
            var result = adapter.extractOutputs(h.getLevel(), pos, filter(Items.GOLD_INGOT), null, provider);
            h.assertTrue(count(result, Items.GOLD_INGOT) == 2, "infuser generated the real output: " + result);
            // Surrounding sources can refill a consumed source, but native completion must clear the item.
            h.assertTrue(AstralAccess.inventory(h.getLevel().getBlockEntity(pos), false).getStackInSlot(0).isEmpty(), "native infuser consumed its input");
            h.succeed();
        });
    }

    @GameTest(template = "astral_test", timeoutTicks = 300)
    public static void radianceUsesNativeMultiblockAndTierGate(GameTestHelper h) {
        var pos = altar(h); var provider = provider(h); var adapter = new AstralSorceryAdapter(true);
        var inputs = List.of(item(Items.IRON_INGOT, 1));
        var pattern = pattern(h, inputs, item(Items.NETHERITE_INGOT, 1));
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern) == null, "tier-one altar cannot execute tier-four recipe");
        new PatternAltarT4().place(h.getLevel(), pos);
        h.getLevel().setBlockAndUpdate(pos, BlocksAS.ALTAR_RADIANCE.get().defaultBlockState());
        h.assertTrue(PPItems.ASTRAL_RADIANCE_ADAPTER.get().covers(adapter.requiredAdapterId(h.getLevel(), pos)), "tier-four core matches machine");
        h.assertTrue(!PPItems.ASTRAL_ILLUMINATION_ADAPTER.get().covers(adapter.requiredAdapterId(h.getLevel(), pos)), "low-tier core rejected");
        radianceCycle(h, pos, provider, adapter, pattern, inputs, 4);
    }

    private static void radianceCycle(GameTestHelper h, BlockPos pos, PackagedPatternProviderBlockEntity provider,
            AstralSorceryAdapter adapter, IPatternDetails pattern, List<GenericStack> inputs, int remaining) {
        dispatch(h, pos, provider, adapter, pattern, inputs);
        h.runAfterDelay(55, () -> {
            var result = adapter.extractOutputs(h.getLevel(), pos, filter(Items.NETHERITE_INGOT), null, provider);
            h.assertTrue(count(result, Items.NETHERITE_INGOT) == 1,
                    "native tier-four drop recovered after collision, cycle " + (5 - remaining) + ": " + result);
            if (remaining > 1) radianceCycle(h, pos, provider, adapter, pattern, inputs, remaining - 1);
            else {
                System.out.println("AS_RADIANCE_CYCLES_PASS cycles=4");
                h.succeed();
            }
        });
    }

    @GameTest(template = "astral_test")
    public static void invalidInputsAndManualContentsDoNotMutate(GameTestHelper h) {
        var pos = altar(h); var adapter = new AstralSorceryAdapter(true); var level = h.getLevel();
        var inputs = List.of(item(Items.IRON_INGOT, 1), item(Items.GOLD_INGOT, 1));
        var pattern = pattern(h, inputs, item(Items.DIAMOND, 1));
        var binding = adapter.bind(level, pos, pattern);
        h.assertTrue(binding != null, "bind independent of missing relay");
        h.assertTrue(!adapter.canDispatch(level, pos, binding.handle()), "missing required relay blocked");
        var relayPos = pos.offset(TileAltar.getRelayGridOffsets().get(0));
        level.setBlockAndUpdate(relayPos, BlocksAS.FOCUS_RELAY.get().defaultBlockState());
        var manual = AstralAccess.inventory(level.getBlockEntity(pos), true);
        manual.setStackInSlot(1, Items.APPLE.getDefaultInstance());
        h.assertTrue(!adapter.canDispatch(level, pos, binding.handle()) && manual.getStackInSlot(1).is(Items.APPLE), "manual contents protected");
        manual.setStackInSlot(1, net.minecraft.world.item.ItemStack.EMPTY);
        h.assertTrue(adapter.planWithBinding(level, pos, pattern, counters(List.of(item(Items.IRON_INGOT, 2), item(Items.GOLD_INGOT, 1))), binding.handle(), null) == null,
                "extra input cannot be silently consumed");
        h.assertTrue(adapter.planWithBinding(level, pos, pattern, counters(List.of(item(Items.IRON_INGOT, 1))), binding.handle(), null) == null,
                "missing input rejected");
        var wrong = pattern(h, inputs, item(Items.DIAMOND, 2));
        h.assertTrue(adapter.bind(level, pos, wrong) == null, "wrong output count rejected");
        for (String core : List.of("illumination", "resonance", "luminance", "radiance", "infuser")) {
            h.assertTrue(level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "ae2ltpp", "packaged_core/astral_" + core + "_packaged_core")).isPresent(), "core recipe loaded: " + core);
        }
        h.succeed();
    }

    @GameTest(template = "astral_test")
    public static void nativeInventoryReceiptConservesQuantities(GameTestHelper h) {
        var pos = altar(h); var inv = AstralAccess.inventory(h.getLevel().getBlockEntity(pos), true);
        var offered = new net.minecraft.world.item.ItemStack(Items.IRON_INGOT, 1);
        h.assertTrue(inv.insertItem(0, offered, true).isEmpty() && inv.getStackInSlot(0).isEmpty(), "simulation does not mutate");
        h.assertTrue(inv.insertItem(0, offered, false).isEmpty() && inv.getStackInSlot(0).getCount() == 1, "one item remains one item");
        inv.setStackInSlot(0, new net.minecraft.world.item.ItemStack(Items.IRON_INGOT, 63));
        var rest = inv.insertItem(0, new net.minecraft.world.item.ItemStack(Items.IRON_INGOT, 3), false);
        h.assertTrue(rest.getCount() == 2 && inv.getStackInSlot(0).getCount() == 64, "partial receipt accounts for all three items");
        inv.setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
        rest = inv.insertItem(0, new net.minecraft.world.item.ItemStack(Items.BUCKET, 1), false);
        h.assertTrue(rest.isEmpty() && inv.getStackInSlot(0).getCount() == 1, "native remainder insertion does not create sixteen buckets");
        h.succeed();
    }

    @GameTest(template = "astral_test", timeoutTicks = 110)
    public static void deterministicComponentsAndUnsafeModifiers(GameTestHelper h) {
        var pos = altar(h); var provider = provider(h); var adapter = new AstralSorceryAdapter(true);
        var namedInput = Items.IRON_INGOT.getDefaultInstance();
        namedInput.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Astral exact component"));
        var namedOutput = Items.GOLD_NUGGET.getDefaultInstance();
        namedOutput.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, namedInput.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        var inputs = List.of(new GenericStack(AEItemKey.of(namedInput), 1));
        var output = new GenericStack(AEItemKey.of(namedOutput), 1);
        var pattern = pattern(h, inputs, output);
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern(h, inputs, item(Items.GOLD_NUGGET, 1))) == null,
                "strict output without copied component is rejected");
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern(h, inputs, item(Items.BLAZE_ROD, 1))) == null
                        && h.getLevel().getBlockEntity(pos) instanceof TileAltar,
                "binding never executes world-changing output modifier");
        dispatch(h, pos, provider, adapter, pattern, inputs);
        h.runAfterDelay(55, () -> {
            var filter = new AllowedOutputFilter(); filter.allowStrict(output.what());
            var actual = adapter.extractOutputs(h.getLevel(), pos, filter, null, provider);
            h.assertTrue(actual.equals(List.of(output)), "native output retains exact copied component: " + actual);
            h.succeed();
        });
    }

    @GameTest(template = "astral_test", timeoutTicks = 110)
    public static void randomComponentsRequireIdOnlyAndReturnActualKey(GameTestHelper h) {
        var pos = altar(h); var provider = provider(h); var adapter = new AstralSorceryAdapter(true);
        var inputs = List.of(item(Items.IRON_INGOT, 1));
        var strict = pattern(h, inputs, item(Items.ENDER_PEARL, 1));
        h.assertTrue(adapter.bind(h.getLevel(), pos, strict) == null, "random UUID cannot satisfy a strict plain output");
        var fuzzy = (IPatternDetails) java.lang.reflect.Proxy.newProxyInstance(IPatternDetails.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.class, com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isFuzzyOutput", "producesSameIdVariants" -> true;
                    case "isFuzzyInput", "hasFuzzyInputs", "acceptsSameIdVariants" -> false;
                    case "overloadPatternIdentity" -> "astral-test";
                    default -> method.invoke(strict, args);
                });
        dispatch(h, pos, provider, adapter, fuzzy, inputs);
        h.runAfterDelay(55, () -> {
            var filter = new AllowedOutputFilter(); filter.allowIdOnly(AEItemKey.of(Items.ENDER_PEARL));
            var actual = adapter.extractOutputs(h.getLevel(), pos, filter, null, provider);
            h.assertTrue(actual.size() == 1 && actual.getFirst().amount() == 1
                            && actual.getFirst().what().dropSecondary().equals(AEItemKey.of(Items.ENDER_PEARL))
                            && !actual.getFirst().what().equals(AEItemKey.of(Items.ENDER_PEARL)),
                    "harvest uses native generated UUID instead of preview or plain item: " + actual);
            h.succeed();
        });
    }

    @GameTest(template = "astral_test")
    public static void nightGateAndNativeFluidConsumption(GameTestHelper h) {
        var pos = altar(h); var adapter = new AstralSorceryAdapter(true);
        var inputs = List.of(item(Items.IRON_INGOT, 1));
        var pattern = pattern(h, inputs, item(Items.AMETHYST_SHARD, 1));
        var binding = adapter.bind(h.getLevel(), pos, pattern);
        h.assertTrue(binding != null, "night recipe binds during day");
        h.getLevel().setDayTime(6000);
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "native daytime threshold blocks dispatch");
        h.getLevel().setDayTime(18000);
        h.assertTrue(adapter.canDispatch(h.getLevel(), pos, binding.handle()), "native nighttime threshold permits dispatch");
        new PatternInfuser().place(h.getLevel(), pos);
        for (var offset : TileInfuser.getLiquidOffsets()) h.getLevel().setBlockAndUpdate(pos.offset(offset), Blocks.WATER.defaultBlockState());
        var infuser = (TileInfuser) h.getLevel().getBlockEntity(pos);
        dispatch(h, pos, provider(h), new AstralSorceryAdapter(false), pattern(h, inputs, item(Items.GOLD_INGOT, 2)), inputs);
        // Exercise the real server tick in one world tick, before flowing water can refill a consumed source.
        for (int i = 0; i < 26; i++) infuser.serverTick(h.getLevel());
        long water = TileInfuser.getLiquidOffsets().stream().filter(offset -> h.getLevel().getFluidState(pos.offset(offset)).isSource()).count();
        h.assertTrue(water == 11 && infuser.getTileData().getActiveRecipe().isEmpty(), "native recipe consumed exactly one world source: " + water);
        h.succeed();
    }

    private static BlockPos altar(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(8, 4, 8));
        // Solid floor keeps dropped ingredients inside the native capture area.
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) h.getLevel().setBlockAndUpdate(pos.offset(x,-1,z), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos, BlocksAS.ALTAR_ILLUMINATION.get().defaultBlockState());
        return pos;
    }

    private static PackagedPatternProviderBlockEntity provider(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(17,4,8));
        h.getLevel().setBlockAndUpdate(pos, PPBlocks.PACKAGED_PATTERN_PROVIDER.get().defaultBlockState());
        return (PackagedPatternProviderBlockEntity) h.getLevel().getBlockEntity(pos);
    }

    private static void dispatch(GameTestHelper h, BlockPos pos, PackagedPatternProviderBlockEntity provider,
            AstralSorceryAdapter adapter, IPatternDetails pattern, List<GenericStack> inputs) {
        var binding = adapter.bind(h.getLevel(), pos, pattern);
        h.assertTrue(binding != null, "recipe binds");
        var plan = adapter.planWithBinding(h.getLevel(), pos, pattern, counters(inputs), binding.handle(), null, provider);
        h.assertTrue(plan != null, "ready machine has physical dispatch plan");
        var retained = new ArrayList<GenericStack>();
        h.assertTrue(DispatchExecutor.execute(plan, null, retained::addAll).success() && retained.isEmpty(), "all paid inputs delivered once");
        long held = 0;
        var inventory = AstralAccess.inventory(h.getLevel().getBlockEntity(pos), adapter.requiredAdapterId(h.getLevel(), pos) != com.moakiee.ae2lt.packaged.item.AdapterIds.ASTRAL_INFUSER);
        for (int i = 0; i < inventory.getSlots(); i++) held += inventory.getStackInSlot(i).getCount();
        h.assertTrue(held == 1, "exactly one paid main-grid item, no slot-limit multiplication: " + held);
        h.assertTrue(AstralAccess.active(h.getLevel().getBlockEntity(pos)).isPresent(), "native active recipe started");
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "busy machine cannot accept duplicate dispatch");
    }

    private static IPatternDetails pattern(GameTestHelper h, List<GenericStack> inputs, GenericStack output) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(output)), h.getLevel());
    }
    private static KeyCounter[] counters(List<GenericStack> inputs) {
        return inputs.stream().map(stack -> { var counter = new KeyCounter(); counter.add(stack.what(), stack.amount()); return counter; }).toArray(KeyCounter[]::new);
    }
    private static GenericStack item(Item item, long count) { return new GenericStack(AEItemKey.of(item), count); }
    private static AllowedOutputFilter filter(Item item) { var filter = new AllowedOutputFilter(); filter.allowStrict(AEItemKey.of(item)); return filter; }
    private static long count(List<GenericStack> stacks, Item item) { return stacks.stream().filter(s -> s.what().equals(AEItemKey.of(item))).mapToLong(GenericStack::amount).sum(); }
}
