package com.moakiee.ae2lt.packaged.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterRecipeTypes;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;

/**
 * Engine behind the adapter connection checks: places the <b>real</b> machine
 * block from each target mod, hammers {@link MultiblockAdapterRegistry#find}
 * in a loop and asserts the expected adapter recognizes it (which also
 * exercises every per-adapter reflection gate, since all recognizers check
 * the real block-entity class), then resolves the adapter's recipe type
 * through {@link AdapterRecipeTypes#find} exactly as production code does.
 *
 * <p>Two runners share this engine: {@link AdapterConnectionTests} for the
 * Forge GameTest framework (dev environments only — Forge disables gametests
 * on production servers) and {@link ConnectionDiagnosticCommand} for the
 * {@code /ae2ltdiag connections} command available on any dedicated server.
 */
public final class AdapterConnectionCheck {

    private static final Logger LOG = LoggerFactory.getLogger("ae2ltpp-conn");

    /** Repetitions of the registry lookup per case. */
    public static final int STRESS_ROUNDS = 300;

    public record Case(String name, String blockId, String expectedAdapter,
                       String recipeTypeId, boolean recipeTypeRequired) {

        /**
         * Recognition is gated on the target fork's classes; when only the
         * same-modid fork (e.g. AvaritiaNeo vs. the Avaritia fork) is present
         * the block exists but must stay inert — see {@link #conn05}.
         */
        public boolean forkGated() {
            return this == AVARITIA_EXTREME_TABLE;
        }

        /**
         * Fire-like blocks ({@code canSurvive}) pop instantly unless the
         * block below has a sturdy top face, so the harness puts a temporary
         * stone base underneath and restores the original block afterwards.
         */
        public boolean needsSturdyBase() {
            return this == OCCULTISM_SPIRIT_FIRE;
        }
    }

    public record NegCase(String name, String blockId) {
    }

    /** Outcome consumer so the engine stays independent of any runner. */
    public interface Sink {
        default void begin(String name) {
        }

        void pass(String name, String summary);

        void fail(String name, String detail);

        default void info(String name, String message) {
        }
    }

    private static final Map<String, Case> CASES = new LinkedHashMap<>();

    private static Case addCase(String name, String blockId, String expectedAdapter,
                                String recipeTypeId, boolean recipeTypeRequired) {
        var c = new Case(name, blockId, expectedAdapter, recipeTypeId, recipeTypeRequired);
        CASES.put(name, c);
        return c;
    }

    // Actually Additions (Actually Subtractions port)
    private static final Case AA_EMPOWERER = addCase("conn01_aa_empowerer",
            "actuallyadditions:empowerer", "ActuallyAdditionsEmpowererAdapter",
            "actuallyadditions:empower", true);
    private static final Case AA_ATOMIC_RECONSTRUCTOR = addCase("conn02_aa_atomic_reconstructor",
            "actuallyadditions:atomic_reconstructor", "ActuallyAdditionsAtomicReconstructorAdapter",
            "actuallyadditions:laser", true);
    // Ars Nouveau
    private static final Case ARS_APPARATUS = addCase("conn03_ars_apparatus",
            "ars_nouveau:enchanting_apparatus", "ArsNouveauEnchantingApparatusAdapter",
            "ars_nouveau:enchanting_apparatus", true);
    private static final Case ARS_IMBUEMENT = addCase("conn04_ars_imbuement",
            "ars_nouveau:imbuement_chamber", "ArsNouveauImbuementChamberAdapter",
            "ars_nouveau:imbuement", true);
    // Avaritia: the fork's recipe type is intentionally optional — on an
    // AvaritiaNeo server the block exists but the fork-only recipe type does not.
    private static final Case AVARITIA_EXTREME_TABLE = addCase("conn05_avaritia_extreme_table",
            "avaritia:extreme_crafting_table", "AvaritiaTableAdapter",
            "avaritia:crafting_table_recipe", false);
    // Draconic Evolution (recipe type comes from DE's own API field at bind time)
    private static final Case DE_CRAFTING_CORE = addCase("conn06_de_crafting_core",
            "draconicevolution:crafting_core", "DraconicFusionCraftingAdapter",
            "draconicevolution:fusion_crafting", false);
    // Extended Crafting
    private static final Case EC_TABLE_BASIC = addCase("conn07_ec_table_basic",
            "extendedcrafting:basic_table", "ExtendedCraftingTableAdapter",
            "extendedcrafting:table", true);
    private static final Case EC_TABLE_ADVANCED = addCase("conn08_ec_table_advanced",
            "extendedcrafting:advanced_table", "ExtendedCraftingTableAdapter",
            "extendedcrafting:table", true);
    private static final Case EC_TABLE_ELITE = addCase("conn09_ec_table_elite",
            "extendedcrafting:elite_table", "ExtendedCraftingTableAdapter",
            "extendedcrafting:table", true);
    private static final Case EC_TABLE_ULTIMATE = addCase("conn10_ec_table_ultimate",
            "extendedcrafting:ultimate_table", "ExtendedCraftingTableAdapter",
            "extendedcrafting:table", true);
    private static final Case EC_COMBINATION = addCase("conn11_ec_combination",
            "extendedcrafting:crafting_core", "ExtendedCraftingCombinationAdapter",
            "extendedcrafting:combination", true);
    private static final Case EC_FLUX_CRAFTER = addCase("conn12_ec_flux_crafter",
            "extendedcrafting:flux_crafter", "ExtendedCraftingFluxCrafterAdapter",
            "extendedcrafting:flux_crafter", true);
    private static final Case EC_ENDER_CRAFTER = addCase("conn13_ec_ender_crafter",
            "extendedcrafting:ender_crafter", "ExtendedCraftingEnderCrafterAdapter",
            "extendedcrafting:ender_crafter", true);
    // Mystical Agriculture
    private static final Case MA_INFUSION = addCase("conn14_ma_infusion",
            "mysticalagriculture:infusion_altar", "InfusionAltarAdapter",
            "mysticalagriculture:infusion", true);
    private static final Case MA_AWAKENING = addCase("conn15_ma_awakening",
            "mysticalagriculture:awakening_altar", "AwakeningAltarAdapter",
            "mysticalagriculture:awakening", true);
    // Malum
    private static final Case MALUM_FOCUSING = addCase("conn16_malum_focusing",
            "malum:spirit_crucible", "MalumSpiritFocusingAdapter",
            "malum:spirit_focusing", true);
    private static final Case MALUM_INFUSION = addCase("conn17_malum_infusion",
            "malum:spirit_altar", "MalumSpiritInfusionAdapter",
            "malum:spirit_infusion", true);
    // Occultism
    private static final Case OCCULTISM_SPIRIT_FIRE = addCase("conn18_occultism_spirit_fire",
            "occultism:spirit_fire", "OccultismSpiritFireAdapter",
            "occultism:spirit_fire", true);
    private static final Case OCCULTISM_RITUAL = addCase("conn19_occultism_ritual",
            "occultism:golden_sacrificial_bowl", "OccultismRitualAdapter",
            "occultism:ritual", true);
    // Mekanism More Machines (all six machines share one adapter)
    private static final Case MEKMM_NUCLEOSYNTHESIZER = addCase("conn20_mekmm_nucleosynthesizer",
            "mekmm:large_antiprotonic_nucleosynthesizer", "MekanismMoreMachinesAdapter",
            null, false);
    private static final Case MEKMM_CHEMICAL_INFUSER = addCase("conn21_mekmm_chemical_infuser",
            "mekmm:large_chemical_infuser", "MekanismMoreMachinesAdapter",
            null, false);
    private static final Case MEKMM_ELECTROLYTIC_SEPARATOR = addCase("conn22_mekmm_electrolytic_separator",
            "mekmm:large_electrolytic_separator", "MekanismMoreMachinesAdapter",
            null, false);
    private static final Case MEKMM_ROTARY_CONDENSENTRATOR = addCase("conn23_mekmm_rotary_condensentrator",
            "mekmm:large_rotary_condensentrator", "MekanismMoreMachinesAdapter",
            null, false);
    private static final Case MEKMM_SOLAR_NEUTRON_ACTIVATOR = addCase("conn24_mekmm_solar_neutron_activator",
            "mekmm:large_solar_neutron_activator", "MekanismMoreMachinesAdapter",
            null, false);
    private static final Case MEKMM_PIGMENT_MIXER = addCase("conn25_mekmm_pigment_mixer",
            "mekmm:large_pigment_mixer", "MekanismMoreMachinesAdapter",
            null, false);
    // Botania (recognition is reflection-gated on the real BE class)
    private static final Case BOTANIA_TERRA_PLATE = addCase("conn26_botania_terra_plate",
            "botania:terra_plate", "TerraPlateAdapter", null, false);
    private static final Case BOTANIA_RUNIC_ALTAR = addCase("conn27_botania_runic_altar",
            "botania:runic_altar", "RunicAltarAdapter", null, false);
    private static final Case BOTANIA_APOTHECARY = addCase("conn28_botania_apothecary",
            "botania:apothecary_default", "PetalApothecaryAdapter", null, false);
    private static final Case BOTANIA_MANA_POOL = addCase("conn29_botania_mana_pool",
            "botania:mana_pool", "ManaPoolAdapter", null, false);
    private static final Case BOTANIA_ALFHEIM_PORTAL = addCase("conn30_botania_alfheim_portal",
            "botania:alfheim_portal", "AlfheimPortalAdapter", null, false);

    /** Support blocks that must NOT be recognized as a main block. */
    private static final List<NegCase> NEG_CASES = List.of(
            new NegCase("neg31_furnace", "minecraft:furnace"),
            new NegCase("neg32_ec_pedestal", "extendedcrafting:pedestal"),
            new NegCase("neg33_aa_display_stand", "actuallyadditions:display_stand"),
            new NegCase("neg34_ars_pedestal", "ars_nouveau:arcane_pedestal"),
            new NegCase("neg35_ma_vessel", "mysticalagriculture:essence_vessel"),
            new NegCase("neg36_malum_component", "malum:spirit_crucible_component"));

    // fork-only blocks that must be absent on an AvaritiaNeo server
    private static final List<String> AVARITIA_FORK_BLOCKS = List.of(
            "avaritia:sculk_crafting_table",
            "avaritia:nether_crafting_table",
            "avaritia:end_crafting_table",
            "avaritia:extreme_smithing_table");
    private static final List<String> AVARITIA_FORK_TYPES = List.of(
            "avaritia:crafting_table_recipe",
            "avaritia:extreme_smithing_recipe");

    public static List<Case> cases() {
        return List.copyOf(CASES.values());
    }

    public static List<NegCase> negativeCases() {
        return NEG_CASES;
    }

    private AdapterConnectionCheck() {
    }

    /**
     * A placed case block plus the saved state of the block below it (only
     * for fire-like cases needing a sturdy base), so the world can be
     * restored exactly.
     */
    private record Placement(BlockPos pos, BlockState belowOriginal) {
    }

    @Nullable
    private static Placement placeCaseBlock(ServerLevel level, BlockPos pos, Case c) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(c.blockId()));
        BlockState belowOriginal = null;
        if (c.needsSturdyBase()) {
            belowOriginal = level.getBlockState(pos.below());
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        }
        level.setBlock(pos, block.defaultBlockState(), 3);
        if (level.getBlockState(pos).getBlock() == block) {
            return new Placement(pos, belowOriginal);
        }
        // The block popped (e.g. shape updates) — undo everything.
        level.removeBlock(pos, false);
        if (belowOriginal != null) {
            level.setBlock(pos.below(), belowOriginal, 3);
        }
        return null;
    }

    private static void undoPlacement(ServerLevel level, Placement placement) {
        level.removeBlock(placement.pos(), false);
        if (placement.belowOriginal() != null) {
            level.setBlock(placement.pos().below(), placement.belowOriginal(), 3);
        }
    }

    public static void runCase(ServerLevel level, BlockPos pos, Case c, Sink sink) {
        sink.begin(c.name());
        var id = new ResourceLocation(c.blockId());
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            sink.fail(c.name(), "block " + id + " is NOT registered by the target mod — "
                    + "mod connection broken or unexpected mod version");
            return;
        }
        var placed = placeCaseBlock(level, pos, c);
        if (placed == null) {
            sink.fail(c.name(), "block " + id + " did not stay placed at " + pos
                    + " (popped by canSurvive/shape updates) — harness placement issue");
            return;
        }
        var be = level.getBlockEntity(placed.pos());
        MultiblockAdapter found = null;
        int failedRound = -1;
        for (int round = 0; round < STRESS_ROUNDS; round++) {
            found = MultiblockAdapterRegistry.find(level, placed.pos(), be);
            if (found == null) {
                failedRound = round;
                break;
            }
        }
        if (found == null && c.forkGated() && !avaritiaForkClassesPresent()) {
            undoPlacement(level, placed);
            sink.pass(c.name(), "block present but fork classes absent — correctly inert "
                    + "(AvaritiaNeo split; recognition requires the fork's TierCraftTile)");
            return;
        }
        if (found == null) {
            sink.fail(c.name(), "MultiblockAdapterRegistry.find returned null at round " + failedRound
                    + " for " + id + " — adapter does not recognize the real machine block"
                    + " [blockEntity=" + (be == null ? "none" : be.getClass().getName()) + "]");
            undoPlacement(level, placed);
            return;
        }
        var actual = found.getClass().getSimpleName();
        if (!actual.equals(c.expectedAdapter())) {
            sink.fail(c.name(), "recognized by " + actual + ", expected " + c.expectedAdapter());
            undoPlacement(level, placed);
            return;
        }
        String typeSummary = "n/a";
        if (c.recipeTypeId() != null) {
            var type = AdapterRecipeTypes.find(new ResourceLocation(c.recipeTypeId()));
            if (type == null && c.recipeTypeRequired()) {
                sink.fail(c.name(), "recipe type " + c.recipeTypeId()
                        + " not resolvable via AdapterRecipeTypes (checked Forge + vanilla registries)");
                undoPlacement(level, placed);
                return;
            }
            typeSummary = type == null ? c.recipeTypeId() + " absent (optional — fork split)"
                    : c.recipeTypeId() + " present";
        }
        undoPlacement(level, placed);
        sink.pass(c.name(), "recognized as " + actual + " x" + STRESS_ROUNDS
                + " rounds; recipe type: " + typeSummary);
    }

    /** Whether the Avaritia fork's craft tile classes are on this server. */
    private static volatile Boolean avaritiaForkClasses;

    private static boolean avaritiaForkClassesPresent() {
        var present = avaritiaForkClasses;
        if (present == null) {
            present = false;
            for (var candidate : new String[] {
                    "committee.nova.mods.avaritia.common.tile.TierCraftTile",
                    "committee.nova.mods.avaritia.common.tile.tiers.TierCraftTile"}) {
                try {
                    Class.forName(candidate);
                    present = true;
                    break;
                } catch (ClassNotFoundException | LinkageError ignored) {
                    // try the next fork layout
                }
            }
            avaritiaForkClasses = present;
        }
        return present;
    }

    public static void runNegative(ServerLevel level, BlockPos pos, NegCase c, Sink sink) {
        sink.begin(c.name());
        var id = new ResourceLocation(c.blockId());
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            sink.fail(c.name(), "support block " + id + " not registered — unexpected mod version");
            return;
        }
        level.setBlock(pos, ForgeRegistries.BLOCKS.getValue(id).defaultBlockState(), 3);
        var be = level.getBlockEntity(pos);
        for (int round = 0; round < STRESS_ROUNDS; round++) {
            var found = MultiblockAdapterRegistry.find(level, pos, be);
            if (found != null) {
                sink.fail(c.name(), "support block " + id + " was falsely recognized as a main block by "
                        + found.getClass().getSimpleName() + " at round " + round);
                level.removeBlock(pos, false);
                return;
            }
        }
        level.removeBlock(pos, false);
        sink.pass(c.name(), "never recognized as a main block x" + STRESS_ROUNDS + " rounds");
    }

    /**
     * The four fork-only Avaritia blocks must be absent on an AvaritiaNeo
     * server (fork split), and their fork-only recipe types unresolvable.
     * If a fork jar is added later this check fails on purpose until the
     * expectation table is updated.
     */
    public static void runForkSplit(ServerLevel level, Sink sink) {
        sink.begin("conn37_avaritia_fork_split");
        for (var blockId : AVARITIA_FORK_BLOCKS) {
            if (ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(blockId))) {
                sink.fail("conn37_avaritia_fork_split", "fork block " + blockId
                        + " is present — Avaritia fork is installed, fork-split expectations need updating");
                return;
            }
        }
        for (var typeId : AVARITIA_FORK_TYPES) {
            if (AdapterRecipeTypes.find(new ResourceLocation(typeId)) != null) {
                sink.fail("conn37_avaritia_fork_split", "fork recipe type " + typeId
                        + " is present — Avaritia fork is installed, fork-split expectations need updating");
                return;
            }
        }
        sink.pass("conn37_avaritia_fork_split",
                "fork blocks and types correctly absent under AvaritiaNeo");
    }

    public static void runInventory(ServerLevel level, Sink sink) {
        sink.begin("conn39_registry_inventory");
        var active = MultiblockAdapterRegistry.activeAdapters();
        for (var adapter : active) {
            sink.info("conn39_registry_inventory",
                    "active: " + adapter.getClass().getName() + " (priority " + adapter.priority() + ")");
        }
        if (active.size() < 23) {
            sink.fail("conn39_registry_inventory",
                    "expected at least 23 active adapters with all compatible mods loaded, got "
                            + active.size() + " — some adapter's mod-loaded gate is broken");
            return;
        }
        sink.pass("conn39_registry_inventory", active.size() + " adapters active");
    }

    /** Placement snapshot of the co-location stress. */
    public record Colocated(List<Placement> placements, List<Case> cases) {

        public List<BlockPos> positions() {
            return placements.stream().map(Placement::pos).toList();
        }
    }

    /**
     * Places every registered adapter main block on a 5-wide grid starting at
     * {@code origin} so all target mods' machines coexist while being routed.
     * Blocks that pop at their grid slot (fire-like {@code canSurvive} rules
     * over non-sturdy decorations) are relocated to a spare row.
     */
    public static Colocated placeColocated(ServerLevel level, BlockPos origin, Sink sink) {
        sink.begin("conn38_all_adapters_colocated");
        var placements = new ArrayList<Placement>();
        var placedCases = new ArrayList<Case>();
        var skipped = new ArrayList<String>();
        int slot = 0;
        int spare = 0;
        for (var c : CASES.values()) {
            if (c.forkGated() && !avaritiaForkClassesPresent()) {
                continue;
            }
            var id = new ResourceLocation(c.blockId());
            if (!ForgeRegistries.BLOCKS.containsKey(id)) {
                continue;
            }
            var planned = origin.offset(slot % 5, slot / 25, (slot / 5) % 5);
            slot++;
            var placed = placeCaseBlock(level, planned, c);
            if (placed == null) {
                placed = placeCaseBlock(level, origin.offset(spare++, 0, 8), c);
            }
            if (placed == null) {
                skipped.add(c.blockId());
                continue;
            }
            placements.add(placed);
            placedCases.add(c);
        }
        if (placements.size() < CASES.size() - 4) {
            removeColocated(level, new Colocated(List.copyOf(placements),
                    List.copyOf(placedCases)));
            sink.fail("conn38_all_adapters_colocated", "only " + placements.size() + "/"
                    + CASES.size() + " adapter main blocks could be placed (unregistered or"
                    + " unplaceable: " + skipped + ")");
            return null;
        }
        if (!skipped.isEmpty()) {
            sink.info("conn38_all_adapters_colocated",
                    "relocated/unplaceable blocks: " + skipped);
        }
        return new Colocated(List.copyOf(placements), List.copyOf(placedCases));
    }

    /** Full immediate stress: repeated routing of every co-located machine. */
    public static boolean stressColocated(ServerLevel level, Colocated state, Sink sink) {
        for (int round = 0; round < colocatedExpectedRounds(); round++) {
            if (!scanColocated(level, state, sink)) {
                return false;
            }
        }
        return true;
    }

    /** Routes every co-located machine once; returns false on mismatch. */
    public static boolean scanColocated(ServerLevel level, Colocated state, Sink sink) {
        for (int i = 0; i < state.placements().size(); i++) {
            var pos = state.placements().get(i).pos();
            var found = MultiblockAdapterRegistry.find(level, pos, level.getBlockEntity(pos));
            var expected = state.cases().get(i).expectedAdapter();
            if (found == null || !found.getClass().getSimpleName().equals(expected)) {
                sink.fail("conn38_all_adapters_colocated", "co-located scan at " + pos + " ("
                        + state.cases().get(i).blockId() + "): expected " + expected + ", got "
                        + (found == null ? "null" : found.getClass().getSimpleName()));
                return false;
            }
        }
        return true;
    }

    public static void removeColocated(ServerLevel level, Colocated state) {
        for (var placement : state.placements()) {
            undoPlacement(level, placement);
        }
    }

    public static int colocatedExpectedRounds() {
        return 100;
    }
}
