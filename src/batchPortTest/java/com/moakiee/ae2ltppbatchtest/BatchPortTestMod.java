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

    public BatchPortTestMod(IEventBus modEventBus) {
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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
