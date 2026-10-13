package com.moakiee.ae2ltppbatchtest;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Development-only registration of batch tests whose optional mods exist on 26.1.2. */
@Mod(BatchPortTestMod.MODID)
public final class BatchPortTestMod {
    static final String MODID = "ae2ltpp_batch_port_test";
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, MODID);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_BATCH =
            FUNCTIONS.register("ec_batch_remainders_reload",
                    () -> BatchProviderGameTests::ecBatchPreservesRemaindersAndSurvivesReload);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PARTIAL =
            FUNCTIONS.register("partial_insertion_reload",
                    () -> BatchProviderGameTests::partialInsertionRetainsEveryPortAndReloads);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CPU_BATCH =
            FUNCTIONS.register("thunderbolt_cpu_batch",
                    () -> BatchProviderGameTests::thunderboltDispatchesOrdinaryPpAsOneBatch);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LANE_FAILURE =
            FUNCTIONS.register("later_lane_failure",
                    () -> BatchProviderGameTests::laterLaneFailureDoesNotRefundAcceptedCopies);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LEGACY_MIGRATION =
            FUNCTIONS.register("legacy_batch_nbt_migration",
                    () -> RefactorIntegrationGameTests::legacyBatchNbtMigratesOnceWithComponentsAndPendingInputs);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> INSERTION_FAILURE =
            FUNCTIONS.register("ordinary_insertion_failure",
                    () -> RefactorIntegrationGameTests::laterOrdinaryInsertionExceptionRetainsEveryUnvisitedInput);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AVARITIA_TABLE =
            FUNCTIONS.register("avaritia_table_batch",
                    () -> RefactorIntegrationGameTests::avaritiaTableBatchPreservesContainers);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AVARITIA_SMITHING =
            FUNCTIONS.register("avaritia_smithing_batch",
                    () -> RefactorIntegrationGameTests::avaritiaSmithingNativeRecipeBatches);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SPIRIT_FIRE =
            FUNCTIONS.register("spirit_fire_batch",
                    () -> RefactorIntegrationGameTests::spiritFireNativeRecipeBatches);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OCCULTISM_RITUAL =
            FUNCTIONS.register("occultism_ritual_real_dispatch",
                    () -> OccultismRitualGameTests::ritualDistributesNativeRecipe);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OCCULTISM_PROVIDER =
            FUNCTIONS.register("occultism_ritual_provider_route",
                    () -> OccultismRitualGameTests::providerRoutesNativeRitual);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OCCULTISM_ITEM_USE =
            FUNCTIONS.register("occultism_item_use_proxy",
                    () -> OccultismRitualGameTests::itemUseProxyCompletesNativeRitual);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OCCULTISM_SACRIFICE =
            FUNCTIONS.register("occultism_sacrifice_proxy",
                    () -> OccultismRitualGameTests::sacrificeProxyCompletesNativeRitual);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OCCULTISM_OUTPUT =
            FUNCTIONS.register("occultism_native_output_capture",
                    () -> OccultismRitualGameTests::ritualCollectsNativeOutput);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MA_INFUSION =
            FUNCTIONS.register("ma_infusion_real_dispatch",
                    () -> MysticalAgricultureGameTests::infusionAltarDistributesNativeRecipe);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MA_AWAKENING =
            FUNCTIONS.register("ma_awakening_real_dispatch",
                    () -> MysticalAgricultureGameTests::awakeningAltarDistributesNativeRecipe);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MA_PROVIDER =
            FUNCTIONS.register("ma_infusion_provider_route",
                    () -> MysticalAgricultureGameTests::providerRoutesInfusionRecipe);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ONE_CORE_REGISTRATION_AND_NATIVE_CRAFTING =
            FUNCTIONS.register("one_core_registration_and_native_crafting", () -> ChiselGameTests::oneCoreRegistrationAndNativeCrafting);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CHIPPED_ALL_BENCHES_REQUIRE_BOTH_HALVES =
            FUNCTIONS.register("chipped_all_benches_require_both_halves", () -> ChiselGameTests::chippedAllBenchesRequireBothHalves);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> REJECT_FORGED_AMOUNTS_COMPONENTS_AND_EXTRA_INPUTS =
            FUNCTIONS.register("reject_forged_amounts_components_and_extra_inputs", () -> ChiselGameTests::rejectForgedAmountsComponentsAndExtraInputs);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RECHISELED_RATIOS_CONNECTED_AND_REVERSE =
            FUNCTIONS.register("rechiseled_ratios_connected_and_reverse", () -> ChiselGameTests::rechiseledRatiosConnectedAndReverse);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CHISEL_NATIVE_GROUPS_BOTH_DIRECTIONS =
            FUNCTIONS.register("chisel_native_groups_both_directions", () -> ChiselGameTests::chiselNativeGroupsBothDirections);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PORTABLE_PROVIDER_NORMAL_BATCH_AND_PERSISTENCE =
            FUNCTIONS.register("portable_provider_normal_batch_and_persistence", () -> ChiselGameTests::portableProviderNormalBatchAndPersistence);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PORTABLE_PROVIDER_WIRELESS_WITHOUT_TARGET =
            FUNCTIONS.register("portable_provider_wireless_without_target", () -> ChiselGameTests::portableProviderWirelessWithoutTarget);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CHIPPED_WIRELESS_TARGET_REMOVAL_STOPS_CACHED_CRAFT =
            FUNCTIONS.register("chipped_wireless_target_removal_stops_cached_craft", () -> ChiselGameTests::chippedWirelessTargetRemovalStopsCachedCraft);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CONNECTOR_REQUIRES_MATCHING_CORE_FOR_EVERY_TARGET =
            FUNCTIONS.register("connector_requires_matching_core_for_every_target", () -> checked(ChiselGameTests::connectorRequiresMatchingCoreForEveryTarget));

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OCCULTISM_SPIRIT_FIRE_LINKS_CRAFTS_AND_PRUNES =
            FUNCTIONS.register("occultism_spirit_fire_links_crafts_and_prunes", () -> checked(ChiselGameTests::occultismSpiritFireLinksCraftsAndPrunes));

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_STRICT =
            FUNCTIONS.register("av_strict", () -> OverloadWorkbenchGameTests::avStrict);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_STRICT_LARGE =
            FUNCTIONS.register("av_strict_large", () -> OverloadWorkbenchGameTests::avStrictLarge);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_ID_INPUT_STRICT_OUTPUT =
            FUNCTIONS.register("av_id_input_strict_output", () -> OverloadWorkbenchGameTests::avIdInputStrictOutput);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_ID_INPUT_STRICT_OUTPUT_WIRELESS =
            FUNCTIONS.register("av_id_input_strict_output_wireless", () -> OverloadWorkbenchGameTests::avIdInputStrictOutputWireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_ID_INPUT_ID_OUTPUT =
            FUNCTIONS.register("av_id_input_id_output", () -> OverloadWorkbenchGameTests::avIdInputIdOutput);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_ID_INPUT_ID_OUTPUT_WIRELESS =
            FUNCTIONS.register("av_id_input_id_output_wireless", () -> OverloadWorkbenchGameTests::avIdInputIdOutputWireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_ID_INPUT_LARGE =
            FUNCTIONS.register("av_id_input_large", () -> OverloadWorkbenchGameTests::avIdInputLarge);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_ID_INPUT_STRICT_OUTPUT =
            FUNCTIONS.register("ec_id_input_strict_output", () -> OverloadWorkbenchGameTests::ecIdInputStrictOutput);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_ID_INPUT_STRICT_OUTPUT_WIRELESS =
            FUNCTIONS.register("ec_id_input_strict_output_wireless", () -> OverloadWorkbenchGameTests::ecIdInputStrictOutputWireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_ID_INPUT_ID_OUTPUT =
            FUNCTIONS.register("ec_id_input_id_output", () -> OverloadWorkbenchGameTests::ecIdInputIdOutput);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_ID_INPUT_ID_OUTPUT_WIRELESS =
            FUNCTIONS.register("ec_id_input_id_output_wireless", () -> OverloadWorkbenchGameTests::ecIdInputIdOutputWireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_ID_INPUT_LARGE =
            FUNCTIONS.register("ec_id_input_large", () -> OverloadWorkbenchGameTests::ecIdInputLarge);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_BASIC =
            FUNCTIONS.register("ec_basic", () -> UnboundedWorkbenchGameTests::ec_basic);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_BASIC_WIRELESS =
            FUNCTIONS.register("ec_basic_wireless", () -> UnboundedWorkbenchGameTests::ec_basic_wireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_ADVANCED =
            FUNCTIONS.register("ec_advanced", () -> UnboundedWorkbenchGameTests::ec_advanced);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_ADVANCED_WIRELESS =
            FUNCTIONS.register("ec_advanced_wireless", () -> UnboundedWorkbenchGameTests::ec_advanced_wireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_ELITE =
            FUNCTIONS.register("ec_elite", () -> UnboundedWorkbenchGameTests::ec_elite);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_ELITE_WIRELESS =
            FUNCTIONS.register("ec_elite_wireless", () -> UnboundedWorkbenchGameTests::ec_elite_wireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_ULTIMATE =
            FUNCTIONS.register("ec_ultimate", () -> UnboundedWorkbenchGameTests::ec_ultimate);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EC_ULTIMATE_WIRELESS =
            FUNCTIONS.register("ec_ultimate_wireless", () -> UnboundedWorkbenchGameTests::ec_ultimate_wireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_SCULK =
            FUNCTIONS.register("av_sculk", () -> UnboundedWorkbenchGameTests::av_sculk);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_SCULK_WIRELESS =
            FUNCTIONS.register("av_sculk_wireless", () -> UnboundedWorkbenchGameTests::av_sculk_wireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_NETHER =
            FUNCTIONS.register("av_nether", () -> UnboundedWorkbenchGameTests::av_nether);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_NETHER_WIRELESS =
            FUNCTIONS.register("av_nether_wireless", () -> UnboundedWorkbenchGameTests::av_nether_wireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_END =
            FUNCTIONS.register("av_end", () -> UnboundedWorkbenchGameTests::av_end);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_END_WIRELESS =
            FUNCTIONS.register("av_end_wireless", () -> UnboundedWorkbenchGameTests::av_end_wireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_EXTREME =
            FUNCTIONS.register("av_extreme", () -> UnboundedWorkbenchGameTests::av_extreme);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> AV_EXTREME_WIRELESS =
            FUNCTIONS.register("av_extreme_wireless", () -> UnboundedWorkbenchGameTests::av_extreme_wireless);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LONG_BATCH =
            FUNCTIONS.register("long_batch", () -> UnboundedWorkbenchGameTests::longBatch);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SMITHING =
            FUNCTIONS.register("smithing", () -> UnboundedWorkbenchGameTests::smithing);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SMITHING_WIRELESS =
            FUNCTIONS.register("smithing_wireless", () -> UnboundedWorkbenchGameTests::smithingWireless);

    public BatchPortTestMod(IEventBus modEventBus) {
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(BatchPortTestMod::finalizeChiselingPlugins));
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(this::registerTests);
    }

    private void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        register(event, environment, "ec_batch_remainders_reload", EC_BATCH, 160);
        register(event, environment, "partial_insertion_reload", PARTIAL, 100);
        register(event, environment, "thunderbolt_cpu_batch", CPU_BATCH, 160);
        register(event, environment, "later_lane_failure", LANE_FAILURE, 160);
        register(event, environment, "legacy_batch_nbt_migration", LEGACY_MIGRATION, 100);
        register(event, environment, "ordinary_insertion_failure", INSERTION_FAILURE, 100);
        if (ModList.get().isLoaded("avaritia")) {
            register(event, environment, "avaritia_table_batch", AVARITIA_TABLE, 160);
            register(event, environment, "avaritia_smithing_batch", AVARITIA_SMITHING, 160);
        }
        if (ModList.get().isLoaded("occultism")) {
            register(event, environment, "spirit_fire_batch", SPIRIT_FIRE, 160);
            register(event, environment, "occultism_ritual_real_dispatch", OCCULTISM_RITUAL, 800,
                    Identifier.fromNamespaceAndPath("ae2ltpp", "ma_altar_empty"));
            register(event, environment, "occultism_ritual_provider_route", OCCULTISM_PROVIDER, 800,
                    Identifier.fromNamespaceAndPath("ae2ltpp", "ma_altar_empty"));
            register(event, environment, "occultism_item_use_proxy", OCCULTISM_ITEM_USE, 650,
                    Identifier.fromNamespaceAndPath("ae2ltpp", "ma_altar_empty"));
            register(event, environment, "occultism_sacrifice_proxy", OCCULTISM_SACRIFICE, 5_000,
                    Identifier.fromNamespaceAndPath("ae2ltpp", "ma_altar_empty"));
            register(event, environment, "occultism_native_output_capture", OCCULTISM_OUTPUT, 800,
                    Identifier.fromNamespaceAndPath("ae2ltpp", "ma_altar_empty"));
        }
        if (ModList.get().isLoaded("mysticalagriculture")) {
            var altarTemplate = Identifier.fromNamespaceAndPath("ae2ltpp", "ma_altar_empty");
            register(event, environment, "ma_infusion_real_dispatch", MA_INFUSION, 180, altarTemplate);
            register(event, environment, "ma_awakening_real_dispatch", MA_AWAKENING, 180, altarTemplate);
            register(event, environment, "ma_infusion_provider_route", MA_PROVIDER, 240, altarTemplate);
        }
        register(event, environment, "one_core_registration_and_native_crafting", ONE_CORE_REGISTRATION_AND_NATIVE_CRAFTING, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "chisel_test"));
        register(event, environment, "chipped_all_benches_require_both_halves", CHIPPED_ALL_BENCHES_REQUIRE_BOTH_HALVES, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "chisel_test"));
        register(event, environment, "reject_forged_amounts_components_and_extra_inputs", REJECT_FORGED_AMOUNTS_COMPONENTS_AND_EXTRA_INPUTS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "chisel_test"));
        register(event, environment, "rechiseled_ratios_connected_and_reverse", RECHISELED_RATIOS_CONNECTED_AND_REVERSE, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "chisel_test"));
        register(event, environment, "chisel_native_groups_both_directions", CHISEL_NATIVE_GROUPS_BOTH_DIRECTIONS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "chisel_test"));
        register(event, environment, "portable_provider_normal_batch_and_persistence", PORTABLE_PROVIDER_NORMAL_BATCH_AND_PERSISTENCE, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "chisel_test"));
        register(event, environment, "portable_provider_wireless_without_target", PORTABLE_PROVIDER_WIRELESS_WITHOUT_TARGET, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "chisel_test"));
        register(event, environment, "chipped_wireless_target_removal_stops_cached_craft", CHIPPED_WIRELESS_TARGET_REMOVAL_STOPS_CACHED_CRAFT, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "chisel_test"));
        register(event, environment, "connector_requires_matching_core_for_every_target", CONNECTOR_REQUIRES_MATCHING_CORE_FOR_EVERY_TARGET, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "chisel_test"));
        register(event, environment, "occultism_spirit_fire_links_crafts_and_prunes", OCCULTISM_SPIRIT_FIRE_LINKS_CRAFTS_AND_PRUNES, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "chisel_test"));
        register(event, environment, "av_strict", AV_STRICT, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_strict_large", AV_STRICT_LARGE, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_id_input_strict_output", AV_ID_INPUT_STRICT_OUTPUT, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_id_input_strict_output_wireless", AV_ID_INPUT_STRICT_OUTPUT_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_id_input_id_output", AV_ID_INPUT_ID_OUTPUT, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_id_input_id_output_wireless", AV_ID_INPUT_ID_OUTPUT_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_id_input_large", AV_ID_INPUT_LARGE, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_id_input_strict_output", EC_ID_INPUT_STRICT_OUTPUT, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_id_input_strict_output_wireless", EC_ID_INPUT_STRICT_OUTPUT_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_id_input_id_output", EC_ID_INPUT_ID_OUTPUT, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_id_input_id_output_wireless", EC_ID_INPUT_ID_OUTPUT_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_id_input_large", EC_ID_INPUT_LARGE, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_basic", EC_BASIC, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_basic_wireless", EC_BASIC_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_advanced", EC_ADVANCED, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_advanced_wireless", EC_ADVANCED_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_elite", EC_ELITE, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_elite_wireless", EC_ELITE_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_ultimate", EC_ULTIMATE, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "ec_ultimate_wireless", EC_ULTIMATE_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_sculk", AV_SCULK, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_sculk_wireless", AV_SCULK_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_nether", AV_NETHER, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_nether_wireless", AV_NETHER_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_end", AV_END, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_end_wireless", AV_END_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_extreme", AV_EXTREME, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "av_extreme_wireless", AV_EXTREME_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "long_batch", LONG_BATCH, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "smithing", SMITHING, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
        register(event, environment, "smithing_wireless", SMITHING_WIRELESS, 500, Identifier.fromNamespaceAndPath("ae2ltpp", "workbench_test"));
    }

    private static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, String name,
            DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int timeout) {
        register(event, environment, name, function, timeout,
                Identifier.fromNamespaceAndPath("ae2ltpp", "mek_routing_test"));
    }

    private static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, String name,
            DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int timeout, Identifier template) {
        event.registerTest(id(name), new FunctionGameTestInstance(function.getKey(),
                new TestData<>(environment, template,
                        timeout, 0, true)));
    }

    private static void finalizeChiselingPlugins() {
        if (!ModList.get().isLoaded("rechiseled")) return;
        // GameTestServer bypasses the native ServerMainMixin bootstrap.
        try {
            Class.forName("com.supermartijn642.rechiseled.chiseling.ChiselingRecipeManagerImpl")
                    .getMethod("finalizePlugins").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to initialize native Rechiseled test recipes", failure);
        }
    }

    private interface CheckedTest { void run(GameTestHelper helper) throws Exception; }

    private static Consumer<GameTestHelper> checked(CheckedTest test) {
        return helper -> {
            try { test.run(helper); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        };
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
