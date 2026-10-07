package com.moakiee.ae2lt.packaged.compat;

import static com.moakiee.ae2lt.packaged.compat.CompatTestSupport.*;

import com.moakiee.ae2lt.packaged.logic.multiblock.aa.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.ars.ArsNouveauEnchantingApparatusAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.de.DraconicFusionCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.ma.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.malum.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.occultism.OccultismRitualAdapter;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real upstream recipes/serializers, blocks, capabilities and ticking; no mocked machines. */
@GameTestHolder("ae2ltpp")
@PrefixGameTestTemplate(false)
public final class MachineCompatibilityGameTests {
    @GameTest(template = "mek_routing_test")
    public static void requiredCompatibilityModsAreActuallyLoaded(GameTestHelper h) {
        for (var mod : List.of("ae2", "ae2lt", "thunderbolt", "actuallyadditions", "ars_nouveau",
                "draconicevolution", "extendedcrafting", "avaritia", "mysticalagriculture",
                "occultism", "malum", "mekanism", "mekmm", "appmek")) {
            var container = ModList.get().getModContainerById(mod).orElseThrow(() ->
                    new AssertionError("Required compatibility test mod missing: " + mod));
            System.out.println("PP_COMPAT_MOD " + mod + "=" + container.getModInfo().getVersion());
        }
        for (var recipe : List.of("aa_empower", "ars_apparatus", "ec_combination", "de_fusion", "occultism_ritual")) {
            h.assertTrue(h.getLevel().getRecipeManager().byKey(id("ae2ltpp:compat/" + recipe)).isPresent(),
                    "upstream serializer loaded test recipe " + recipe);
        }
        h.succeed();
    }

    @GameTest(template = "mek_routing_test")
    public static void atomicReconstructorChargesExactEnergyAndRejectsInvalidInputs(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "actuallyadditions:atomic_reconstructor");
        var adapter = new ActuallyAdditionsAtomicReconstructorAdapter();
        var inputs = List.of(item("minecraft:diamond", 1));
        var output = item("actuallyadditions:diamatine_crystal", 1);
        var pattern = pattern(h, inputs, output);
        var binding = bind(h, pos, adapter, pattern);
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(), pos, pattern, counters(inputs), binding.handle(), null) == null,
                "empty FE buffer rejects the conversion");
        charge(h, pos);
        int before = energy(h, pos).getEnergyStored();
        h.assertTrue(adapter.planVirtualWithBinding(h.getLevel(), pos, pattern,
                counters(List.of(item("minecraft:dirt", 1))), binding.handle(), null) == null,
                "wrong input cannot consume energy");
        h.assertTrue(energy(h, pos).getEnergyStored() == before, "rejection leaves FE unchanged");
        var result = adapter.planVirtualWithBinding(h.getLevel(), pos, pattern, counters(inputs), binding.handle(), null);
        h.assertTrue(result != null && result.outputs().equals(List.of(output)), "real laser recipe produces one diamatine");
        h.assertTrue(before - energy(h, pos).getEnergyStored() == 1060, "one conversion charges 1000 base + 60 recipe FE");
        h.succeed();
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 250)
    public static void empowererRequiresFourPoweredStandsAndCompletes(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "actuallyadditions:empowerer");
        var adapter = new ActuallyAdditionsEmpowererAdapter();
        var inputs = List.of(item("minecraft:iron_ingot", 1), item("minecraft:clay_ball", 4));
        var output = item("minecraft:amethyst_shard", 1);
        var pattern = pattern(h, inputs, output);
        var binding = bind(h, pos, adapter, pattern);
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "missing stands reject dispatch");
        for (var face : Direction.Plane.HORIZONTAL) place(h, pos.relative(face, 3), "actuallyadditions:display_stand");
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "unpowered stands reject dispatch");
        for (var face : Direction.Plane.HORIZONTAL) charge(h, pos.relative(face, 3));
        dispatch(h, pos, adapter, pattern, binding, inputs);
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "occupied machine rejects another job");
        awaitOutput(h, pos, adapter, output);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 500)
    public static void arsApparatusRequiresCoreAndConsumesPedestalInputs(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "ars_nouveau:enchanting_apparatus");
        var adapter = new ArsNouveauEnchantingApparatusAdapter();
        var inputs = List.of(item("minecraft:iron_ingot", 1), item("minecraft:diamond", 2));
        var output = item("minecraft:prismarine_crystals", 1);
        var pattern = pattern(h, inputs, output);
        var binding = bind(h, pos, adapter, pattern);
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "missing arcane core rejects dispatch");
        place(h, pos.below(), "ars_nouveau:arcane_core");
        place(h, pos.west(2), "ars_nouveau:arcane_pedestal");
        place(h, pos.east(2), "ars_nouveau:arcane_platform");
        dispatch(h, pos, adapter, pattern, binding, inputs);
        awaitOutput(h, pos, adapter, output);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 800)
    public static void mysticalInfusionUsesAllEightPedestals(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "mysticalagriculture:infusion_altar");
        var adapter = new InfusionAltarAdapter();
        var inputs = List.of(item("mysticalagriculture:unattuned_augment", 1),
                item("minecraft:golden_apple", 4), item("mysticalagriculture:inferium_essence", 4));
        var output = item("mysticalagriculture:absorption_i_augment", 1);
        var pattern = pattern(h, inputs, output);
        var binding = bind(h, pos, adapter, pattern);
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "incomplete altar rejects dispatch");
        for (int[] offset : new int[][]{{-3,0},{2,2},{3,0},{-2,-2},{0,-3},{2,-2},{0,3},{-2,2}})
            place(h, pos.offset(offset[0], 0, offset[1]), "mysticalagriculture:infusion_pedestal");
        dispatch(h, pos, adapter, pattern, binding, inputs);
        awaitOutput(h, pos, adapter, output);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 800)
    public static void mysticalAwakeningConsumesExactVesselEssences(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "mysticalagriculture:awakening_altar");
        var adapter = new AwakeningAltarAdapter();
        var inputs = List.of(item("mysticalagriculture:supremium_block", 1), item("mysticalagriculture:cognizant_dust", 4),
                item("mysticalagriculture:air_essence", 10), item("mysticalagriculture:earth_essence", 10),
                item("mysticalagriculture:water_essence", 10), item("mysticalagriculture:fire_essence", 10));
        var output = item("mysticalagriculture:awakened_supremium_block", 1);
        var pattern = pattern(h, inputs, output);
        var binding = bind(h, pos, adapter, pattern);
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "missing vessels/pedestals reject dispatch");
        for (var face : Direction.Plane.HORIZONTAL) place(h, pos.relative(face, 3), "mysticalagriculture:awakening_pedestal");
        for (int x : new int[]{-2,2}) for (int z : new int[]{-2,2})
            place(h, pos.offset(x, 0, z), "mysticalagriculture:essence_vessel");
        dispatch(h, pos, adapter, pattern, binding, inputs);
        awaitOutput(h, pos, adapter, output);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 1600)
    public static void enderCrafterRespectsNativeShapedRecipe(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "extendedcrafting:ender_crafter");
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            place(h, pos.offset(x, -1, z), "extendedcrafting:ender_alternator");
        var adapter = new ExtendedCraftingEnderCrafterAdapter();
        var inputs = List.of(item("minecraft:nether_star", 1), item("minecraft:ender_eye", 4));
        var output = item("extendedcrafting:ender_star", 1);
        var pattern = pattern(h, inputs, output);
        dispatch(h, pos, adapter, pattern, bind(h, pos, adapter, pattern), inputs);
        awaitOutput(h, pos, adapter, output);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 2400)
    public static void fluxCrafterConsumesPowerAndReturnsFourIngots(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "extendedcrafting:flux_crafter");
        place(h, pos.below(), "extendedcrafting:flux_alternator");
        h.onEachTick(() -> charge(h, pos.below()));
        var adapter = new ExtendedCraftingFluxCrafterAdapter();
        var inputs = List.of(item("extendedcrafting:flux_star", 1), item("extendedcrafting:redstone_ingot", 4));
        var output = item("extendedcrafting:enhanced_redstone_ingot", 4);
        var pattern = pattern(h, inputs, output);
        dispatch(h, pos, adapter, pattern, bind(h, pos, adapter, pattern), inputs);
        awaitOutput(h, pos, adapter, output);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 300)
    public static void combinationCraftingRefreshesRecipeAndConsumesPower(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "extendedcrafting:crafting_core");
        var adapter = new ExtendedCraftingCombinationAdapter();
        var inputs = List.of(item("minecraft:iron_ingot", 1), item("minecraft:diamond", 2));
        var output = item("minecraft:echo_shard", 1);
        var pattern = pattern(h, inputs, output);
        var binding = bind(h, pos, adapter, pattern);
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "missing pedestals reject dispatch");
        place(h, pos.west(2), "extendedcrafting:pedestal");
        place(h, pos.east(2), "extendedcrafting:pedestal");
        h.onEachTick(() -> charge(h, pos));
        dispatch(h, pos, adapter, pattern, binding, inputs);
        awaitOutput(h, pos, adapter, output);
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 1600)
    public static void draconicFusionUsesNativeInjectorsAndCraftingTicker(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "draconicevolution:crafting_core");
        var adapter = new DraconicFusionCraftingAdapter();
        var inputs = List.of(item("minecraft:iron_ingot", 1), item("minecraft:diamond", 2));
        var output = item("minecraft:nautilus_shell", 1);
        var pattern = pattern(h, inputs, output);
        var binding = bind(h, pos, adapter, pattern);
        h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "missing injectors reject dispatch");
        for (var face : List.of(Direction.WEST, Direction.EAST)) {
            var injector = pos.relative(face, 3);
            place(h, injector, "draconicevolution:basic_crafting_injector");
            var state = h.getLevel().getBlockState(injector);
            h.getLevel().setBlockAndUpdate(injector, state.setValue(BlockStateProperties.FACING, face.getOpposite()));
            h.onEachTick(() -> charge(h, injector));
        }
        h.runAfterDelay(5, () -> {
            h.assertTrue(adapter.canDispatch(h.getLevel(), pos, binding.handle()), "new injectors are discovered without manual insertion");
            var injector = pos.west(3);
            var state = h.getLevel().getBlockState(injector);
            h.getLevel().setBlockAndUpdate(injector, Blocks.AIR.defaultBlockState());
            h.assertTrue(!adapter.canDispatch(h.getLevel(), pos, binding.handle()), "removed injector invalidates the cached structure");
            h.getLevel().setBlockAndUpdate(injector, state);
            dispatch(h, pos, adapter, pattern, binding, inputs);
            awaitOutput(h, pos, adapter, output);
        });
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 1600)
    public static void malumInfusionConsumesSpiritsAndPedestalExtras(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "malum:spirit_altar");
        place(h, pos.east(2), "malum:runewood_item_pedestal");
        var adapter = new MalumSpiritInfusionAdapter();
        var inputs = List.of(item("minecraft:iron_ingot", 1), item("malum:refined_soulstone", 4),
                item("malum:wicked_spirit", 3), item("malum:earthen_spirit", 1), item("malum:arcane_spirit", 1));
        var output = item("malum:soul_stained_steel_ingot", 1);
        var pattern = pattern(h, inputs, output);
        var binding = bind(h, pos, adapter, pattern);
        h.runAfterDelay(20, () -> {
            dispatch(h, pos, adapter, pattern, binding, inputs);
            awaitOutput(h, pos, adapter, output);
        });
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 1600)
    public static void malumFocusingRequiresCompleteCrucibleAndReturnsCatalyst(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "malum:spirit_crucible");
        var adapter = new MalumSpiritFocusingAdapter();
        var inputs = List.of(item("malum:alchemical_impetus", 1), item("malum:infernal_spirit", 2), item("malum:arcane_spirit", 2));
        var output = item("malum:blazing_quartz", 4);
        var pattern = pattern(h, inputs, output);
        h.getLevel().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern) == null, "missing crucible top rejects binding");
        place(h, pos.above(), "malum:spirit_crucible_component");
        dispatch(h, pos, adapter, pattern, bind(h, pos, adapter, pattern), inputs);
        awaitOutput(h, pos, adapter, output, received -> {
            long catalysts = 0;
            for (var entry : received) {
                if (entry.getKey() instanceof appeng.api.stacks.AEItemKey key
                        && key.getId().equals(id("malum:alchemical_impetus"))) {
                    // The unmodified crucible has 20% instability: native Malum
                    // randomly charges either the recipe's 1 durability or 2.
                    int damage = key.toStack(1).getDamageValue();
                    h.assertTrue(damage == 1 || damage == 2, "returned catalyst retains native durability cost: " + damage);
                    catalysts += entry.getLongValue();
                }
            }
            h.assertTrue(catalysts == 1, "exactly one used catalyst returns after crafting has finished");
        });
    }

    @GameTest(template = "mek_routing_test", timeoutTicks = 500)
    public static void occultismRitualRequiresPentacleAndCollectsNativeOutput(GameTestHelper h) {
        var pos = h.absolutePos(CENTER);
        place(h, pos, "occultism:golden_sacrificial_bowl");
        var adapter = new OccultismRitualAdapter();
        var inputs = List.of(item("minecraft:apple", 1), item("minecraft:clay_ball", 4));
        var output = item("minecraft:heart_of_the_sea", 1);
        var pattern = pattern(h, inputs, output);
        h.assertTrue(adapter.bind(h.getLevel(), pos, pattern) == null, "incomplete pentacle rejects binding");
        // Native craft_foliot pentacle from Occultism 1.215.0. Display-only floor
        // materials are not constraints; solid stone supports glyphs and candles.
        var rows = List.of("_________", "__WXXXW__", "_WW1_1WW_", "_X1W_W1X_", "_X__0__X_",
                "_X1W_W1X_", "_WW1_1WW_", "__WXXXW__", "_________");
        for (int z = 0; z < 9; z++) for (int x = 0; x < 9; x++) {
            var cell = pos.offset(x - 4, 0, z - 4);
            h.getLevel().setBlockAndUpdate(cell.below(), Blocks.STONE.defaultBlockState());
            switch (rows.get(z).charAt(x)) {
                case 'W' -> place(h, cell, "occultism:chalk_glyph_white");
                case 'X' -> place(h, cell, "occultism:chalk_glyph_purple");
                case '1' -> place(h, cell, "minecraft:candle");
                default -> { }
            }
        }
        for (var face : Direction.Plane.HORIZONTAL) place(h, pos.relative(face, 2), "occultism:sacrificial_bowl");
        h.getLevel().setBlockAndUpdate(pos.above(3), Blocks.STONE.defaultBlockState());
        place(h, pos.above(2), "occultism:sacrificial_bowl");
        h.getLevel().setBlockAndUpdate(pos.above(2), h.getLevel().getBlockState(pos.above(2))
                .setValue(BlockStateProperties.FACING, Direction.DOWN));
        var binding = bind(h, pos, adapter, pattern);
        dispatch(h, pos, adapter, pattern, binding, inputs);
        awaitOutput(h, pos, adapter, output);
    }
}
