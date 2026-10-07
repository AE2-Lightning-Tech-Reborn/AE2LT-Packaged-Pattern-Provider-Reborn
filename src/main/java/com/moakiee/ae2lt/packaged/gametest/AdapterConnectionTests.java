package com.moakiee.ae2lt.packaged.gametest;

import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.gametest.GameTestHolder;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.avaritia.AvaritiaTableAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;

/**
 * Forge GameTest front end for the adapter connection checks.
 *
 * <p>Forge only enables gametests in development environments (see
 * {@code ForgeGameTestHooks.isGametestEnabled}); on production dedicated
 * servers use {@code /ae2ltdiag connections} instead, which runs the same
 * engine via {@link ConnectionDiagnosticCommand}. Each case also covers idle
 * dispatch/harvest fail-closed checks; conn38 additionally runs the
 * co-located harvest pass before succeeding.
 */
@GameTestHolder("ae2ltpp")
public final class AdapterConnectionTests {

    private static final Logger LOG = LoggerFactory.getLogger("ae2ltpp-conn");

    private AdapterConnectionTests() {
    }

    private static AdapterConnectionCheck.Sink sink(GameTestHelper helper) {
        return new AdapterConnectionCheck.Sink() {
            @Override
            public void pass(String name, String summary) {
                LOG.info("[ae2ltpp-conn] PASS {} | {}", name, summary);
                helper.succeed();
            }

            @Override
            public void fail(String name, String detail) {
                LOG.error("[ae2ltpp-conn] FAIL {} | {}", name, detail);
                helper.fail(name + ": " + detail);
            }

            @Override
            public void info(String name, String message) {
                LOG.info("[ae2ltpp-conn] INFO {} | {}", name, message);
            }
        };
    }

    private static ServerLevel level(GameTestHelper helper) {
        return helper.getLevel();
    }

    private static BlockPos pos(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(2, 1, 2));
    }

    @GameTest(template = "adapter_conn")
    public static void conn01_aa_empowerer(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn01_aa_empowerer"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn02_aa_atomic_reconstructor(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn02_aa_atomic_reconstructor"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn03_ars_apparatus(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn03_ars_apparatus"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn04_ars_imbuement(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn04_ars_imbuement"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn05_avaritia_extreme_table(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn05_avaritia_extreme_table"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn06_de_crafting_core(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn06_de_crafting_core"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn07_ec_table_basic(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn07_ec_table_basic"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn08_ec_table_advanced(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn08_ec_table_advanced"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn09_ec_table_elite(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn09_ec_table_elite"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn10_ec_table_ultimate(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn10_ec_table_ultimate"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn11_ec_combination(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn11_ec_combination"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn12_ec_flux_crafter(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn12_ec_flux_crafter"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn13_ec_ender_crafter(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn13_ec_ender_crafter"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn14_ma_infusion(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn14_ma_infusion"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn15_ma_awakening(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn15_ma_awakening"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn16_malum_focusing(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn16_malum_focusing"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn17_malum_infusion(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn17_malum_infusion"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn18_occultism_spirit_fire(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn18_occultism_spirit_fire"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn19_occultism_ritual(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn19_occultism_ritual"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn20_mekmm_nucleosynthesizer(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn20_mekmm_nucleosynthesizer"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn21_mekmm_chemical_infuser(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn21_mekmm_chemical_infuser"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn22_mekmm_electrolytic_separator(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn22_mekmm_electrolytic_separator"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn23_mekmm_rotary_condensentrator(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn23_mekmm_rotary_condensentrator"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn24_mekmm_solar_neutron_activator(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn24_mekmm_solar_neutron_activator"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn25_mekmm_pigment_mixer(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn25_mekmm_pigment_mixer"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn26_botania_terra_plate(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn26_botania_terra_plate"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn27_botania_runic_altar(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn27_botania_runic_altar"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn28_botania_apothecary(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn28_botania_apothecary"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn29_botania_mana_pool(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn29_botania_mana_pool"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn30_botania_alfheim_portal(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn30_botania_alfheim_portal"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void neg31_furnace(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg31_furnace"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void neg32_ec_pedestal(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg32_ec_pedestal"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void neg33_aa_display_stand(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg33_aa_display_stand"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void neg34_ars_pedestal(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg34_ars_pedestal"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void neg35_ma_vessel(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg35_ma_vessel"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void neg36_malum_component(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg36_malum_component"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn37_avaritia_fork_split(GameTestHelper helper) {
        AdapterConnectionCheck.runForkSplit(level(helper), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn39_registry_inventory(GameTestHelper helper) {
        AdapterConnectionCheck.runInventory(level(helper), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn40_avaritia_extreme_smithing(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn40_avaritia_extreme_smithing"), sink(helper));
    }

    /**
     * Co-location stress: every main block from every compatible mod is placed
     * simultaneously, routed under repetition, then re-verified after the
     * placed block entities have had a settling window to tick.
     */
    @GameTest(template = "adapter_conn", timeoutTicks = 200)
    public static void conn38_all_adapters_colocated(GameTestHelper helper) {
        var s = sink(helper);
        var state = AdapterConnectionCheck.placeColocated(level(helper),
                helper.absolutePos(new BlockPos(0, 1, 0)), s);
        if (state == null) {
            helper.fail("colocation setup failed");
            return;
        }
        if (!AdapterConnectionCheck.stressColocated(level(helper), state, s)) {
            AdapterConnectionCheck.removeColocated(level(helper), state);
            return;
        }
        helper.runAfterDelay(AdapterConnectionCheck.COLOCATED_SETTLE_TICKS, () -> {
            boolean passed = false;
            boolean restored = false;
            try {
                var ok = AdapterConnectionCheck.scanColocated(level(helper), state, s);
                passed = ok && AdapterConnectionCheck.runDispatchHarvestColocated(level(helper), state, s);
            } catch (RuntimeException | LinkageError e) {
                LOG.error("[ae2ltpp-conn] conn38 delayed scan threw", e);
                s.fail("conn38_all_adapters_colocated", "delayed scan threw "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                try {
                    AdapterConnectionCheck.removeColocated(level(helper), state);
                    restored = true;
                } catch (RuntimeException | LinkageError e) {
                    LOG.error("[ae2ltpp-conn] conn38 restore threw", e);
                    s.fail("conn38_all_adapters_colocated", "restore threw "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
            if (passed && restored) {
                s.pass("conn38_all_adapters_colocated",
                        state.positions().size() + " machines co-located and routed correctly"
                                + " (" + AdapterConnectionCheck.colocatedExpectedRounds()
                                + " rounds + post-tick); idle dispatch/harvest fail-closed");
            }
        });
    }

    @GameTest(template = "adapter_conn")
    public static void conn42_avaritia_neo_virtual_crafting(GameTestHelper helper) {
        if (ReflectionSupport.findClassCached("net.byAqua3.avaritia.tile.TileExtremeCraftingTable").isEmpty()) {
            helper.succeed();
            return;
        }
        var level = level(helper);
        var relative = new BlockPos(2, 1, 2);
        var tableBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("avaritia", "extreme_crafting_table"));
        helper.assertTrue(tableBlock != null, "AvaritiaNeo extreme table is missing");
        helper.setBlock(relative, tableBlock.defaultBlockState());
        var mainPos = helper.absolutePos(relative);
        var tile = level.getBlockEntity(mainPos);
        var adapter = new AvaritiaTableAdapter();
        helper.assertTrue(adapter.recognizesMain(level, mainPos, tile), "Real Neo table is not recognized");

        try {
            var matrix = (Container) tile.getClass().getField("matrix").get(tile);
            var preview = (Container) tile.getClass().getField("result").get(tile);
            helper.assertTrue(matrix.getContainerSize() == 81 && preview.getContainerSize() == 1,
                    "Unexpected Neo inventory layout");
            var recipe = level.getRecipeManager().byKey(new ResourceLocation("ae2ltpp",
                    "packaged_core/avaritia_neo_extreme_table_packaged_core")).orElse(null);
            helper.assertTrue(recipe != null, "Neo core recipe was not loaded");
            var amounts = new LinkedHashMap<AEItemKey, Long>();
            for (var ingredient : recipe.getIngredients()) {
                if (!ingredient.isEmpty()) {
                    var choices = ingredient.getItems();
                    helper.assertTrue(choices.length > 0, "Neo core recipe has an empty ingredient");
                    amounts.merge(AEItemKey.of(choices[0]), 2L, Long::sum);
                }
            }
            var encodedInputs = amounts.entrySet().stream()
                    .map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toArray(GenericStack[]::new);
            var result = recipe.getResultItem(level.registryAccess());
            var encoded = PatternDetailsHelper.encodeProcessingPattern(encodedInputs,
                    new GenericStack[] {new GenericStack(AEItemKey.of(result), 2L * result.getCount())});
            var pattern = PatternDetailsHelper.decodePattern(encoded, level);
            helper.assertTrue(pattern != null, "Neo processing pattern could not be decoded");
            var binding = adapter.bind(level, mainPos, pattern);
            helper.assertTrue(binding != null && binding.mode() == BindingMode.VIRTUAL,
                    "Neo recipe did not bind to the virtual lane");
            var counters = new KeyCounter[pattern.getInputs().length];
            for (int index = 0; index < counters.length; index++) {
                var input = pattern.getInputs()[index];
                var possible = input.getPossibleInputs()[0];
                counters[index] = new KeyCounter();
                counters[index].add(possible.what(), possible.amount() * input.getMultiplier());
            }
            var before = tile.saveWithoutMetadata();
            var planned = adapter.planVirtualWithBinding(level, mainPos, pattern, counters,
                    binding.handle(), IActionSource.empty());
            helper.assertTrue(planned != null && planned.outputs().get(0).what().equals(AEItemKey.of(result))
                            && planned.outputs().get(0).amount() == 2L * result.getCount(),
                    "Neo batch output is incorrect");
            helper.assertTrue(before.equals(tile.saveWithoutMetadata()), "Virtual plan mutated the real Neo inventory");

            preview.setItem(0, new ItemStack(Items.DIAMOND));
            helper.assertTrue(adapter.planVirtualWithBinding(level, mainPos, pattern, counters,
                    binding.handle(), IActionSource.empty()) == null, "Occupied Neo result slot must block crafting");
            preview.setItem(0, ItemStack.EMPTY);
            matrix.setItem(0, new ItemStack(Items.EMERALD));
            helper.assertTrue(adapter.planVirtualWithBinding(level, mainPos, pattern, counters,
                    binding.handle(), IActionSource.empty()) == null, "Occupied Neo matrix must block crafting");
            var filter = new AllowedOutputFilter();
            filter.allowStrict(AEItemKey.of(result));
            before = tile.saveWithoutMetadata();
            helper.assertTrue(adapter.extractOutputs(level, mainPos, filter, IActionSource.empty()).isEmpty(),
                    "Neo manual ingredients must never be harvested");
            helper.assertTrue(before.equals(tile.saveWithoutMetadata()), "Neo harvest mutated manual ingredients");
            matrix.setItem(0, ItemStack.EMPTY);
            LOG.info("[ae2ltpp-conn] PASS conn42_avaritia_neo_virtual_crafting | real Neo core recipe, batch x2, read-only plan and ownership checks");
            helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("Neo inventory reflection failed: " + e.getMessage());
        }
    }

    private static AdapterConnectionCheck.Case caseOf(String name) {
        return AdapterConnectionCheck.cases().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @GameTest(template = "adapter_conn")
    public static void conn43_bloodmagic_altar(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn43_bloodmagic_altar"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn44_forbidden_arcanus_clibano(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn44_forbidden_arcanus_clibano"), sink(helper));
    }

    @GameTest(template = "adapter_conn")
    public static void conn45_forbidden_arcanus_hephaestus_forge(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn45_forbidden_arcanus_hephaestus_forge"), sink(helper));
    }

    private static AdapterConnectionCheck.NegCase negOf(String name) {
        return AdapterConnectionCheck.negativeCases().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
