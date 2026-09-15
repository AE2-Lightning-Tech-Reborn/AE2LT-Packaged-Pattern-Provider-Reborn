package com.moakiee.ae2lt.packaged.gametest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Forge GameTest front end for the adapter connection checks.
 *
 * <p>Forge only enables gametests in development environments (see
 * {@code ForgeGameTestHooks.isGametestEnabled}); on production dedicated
 * servers use {@code /ae2ltdiag connections} instead, which runs the same
 * engine via {@link ConnectionDiagnosticCommand}.
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

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn01_aa_empowerer(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn01_aa_empowerer"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn02_aa_atomic_reconstructor(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn02_aa_atomic_reconstructor"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn03_ars_apparatus(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn03_ars_apparatus"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn04_ars_imbuement(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn04_ars_imbuement"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn05_avaritia_extreme_table(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn05_avaritia_extreme_table"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn06_de_crafting_core(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn06_de_crafting_core"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn07_ec_table_basic(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn07_ec_table_basic"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn08_ec_table_advanced(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn08_ec_table_advanced"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn09_ec_table_elite(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn09_ec_table_elite"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn10_ec_table_ultimate(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn10_ec_table_ultimate"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn11_ec_combination(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn11_ec_combination"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn12_ec_flux_crafter(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn12_ec_flux_crafter"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn13_ec_ender_crafter(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn13_ec_ender_crafter"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn14_ma_infusion(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn14_ma_infusion"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn15_ma_awakening(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn15_ma_awakening"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn16_malum_focusing(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn16_malum_focusing"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn17_malum_infusion(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn17_malum_infusion"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn18_occultism_spirit_fire(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn18_occultism_spirit_fire"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn19_occultism_ritual(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn19_occultism_ritual"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn20_mekmm_nucleosynthesizer(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn20_mekmm_nucleosynthesizer"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn21_mekmm_chemical_infuser(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn21_mekmm_chemical_infuser"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn22_mekmm_electrolytic_separator(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn22_mekmm_electrolytic_separator"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn23_mekmm_rotary_condensentrator(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn23_mekmm_rotary_condensentrator"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn24_mekmm_solar_neutron_activator(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn24_mekmm_solar_neutron_activator"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn25_mekmm_pigment_mixer(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn25_mekmm_pigment_mixer"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn26_botania_terra_plate(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn26_botania_terra_plate"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn27_botania_runic_altar(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn27_botania_runic_altar"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn28_botania_apothecary(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn28_botania_apothecary"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn29_botania_mana_pool(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn29_botania_mana_pool"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn30_botania_alfheim_portal(GameTestHelper helper) {
        AdapterConnectionCheck.runCase(level(helper), pos(helper),
                caseOf("conn30_botania_alfheim_portal"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void neg31_furnace(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg31_furnace"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void neg32_ec_pedestal(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg32_ec_pedestal"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void neg33_aa_display_stand(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg33_aa_display_stand"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void neg34_ars_pedestal(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg34_ars_pedestal"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void neg35_ma_vessel(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg35_ma_vessel"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void neg36_malum_component(GameTestHelper helper) {
        AdapterConnectionCheck.runNegative(level(helper), pos(helper),
                negOf("neg36_malum_component"), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn37_avaritia_fork_split(GameTestHelper helper) {
        AdapterConnectionCheck.runForkSplit(level(helper), sink(helper));
    }

    @GameTest(template = "ae2ltpp:adapter_conn")
    public static void conn39_registry_inventory(GameTestHelper helper) {
        AdapterConnectionCheck.runInventory(level(helper), sink(helper));
    }

    /**
     * Co-location stress: every main block from every compatible mod is placed
     * simultaneously, routed under repetition, then re-verified after the
     * placed block entities have had a settling window to tick.
     */
    @GameTest(template = "ae2ltpp:adapter_conn")
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
        helper.runAfterDelay(40, () -> {
            var ok = AdapterConnectionCheck.scanColocated(level(helper), state, s);
            AdapterConnectionCheck.removeColocated(level(helper), state);
            if (ok) {
                s.pass("conn38_all_adapters_colocated",
                        state.positions().size() + " machines co-located and routed correctly"
                                + " (100 rounds + post-tick)");
            }
        });
    }

    private static AdapterConnectionCheck.Case caseOf(String name) {
        return AdapterConnectionCheck.cases().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static AdapterConnectionCheck.NegCase negOf(String name) {
        return AdapterConnectionCheck.negativeCases().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
