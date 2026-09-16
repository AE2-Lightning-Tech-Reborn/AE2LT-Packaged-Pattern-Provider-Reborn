package com.moakiee.ae2lt.packaged.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterRecipeTypes;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;

/**
 * Engine behind the adapter connection checks: places the <b>real</b> machine
 * block from each target mod, hammers {@link MultiblockAdapterRegistry#find}
 * in a loop and asserts the expected adapter recognizes it (which also
 * exercises every per-adapter reflection gate, since all recognizers check
 * the real block-entity class), then resolves the adapter's recipe type
 * through {@link AdapterRecipeTypes#find} exactly as production code does.
 *
 * <p>After recognition, each placed machine is also exercised through the
 * idle dispatch/harvest surface: {@code extractOutputs} must not vacuum an
 * unowned machine, {@code bind}/{@code planWithBinding} with an unmatched
 * dummy pattern must not crash or mutate the world, and
 * {@link VirtualCraftingAdapter#planVirtualWithBinding} must stay pure
 * (the harness never calls {@link VirtualCraftingResult#commit()}).
 *
 * <p>This is fail-closed adapter-API coverage, not a substitute for the
 * full ME-network scenarios in {@code MANUAL_TESTING.md} 1–16.
 *
 * <p>Two runners share this engine: {@link AdapterConnectionTests} for the
 * Forge GameTest framework (dev environments only — Forge disables gametests
 * on production servers) and {@link ConnectionDiagnosticCommand} for the
 * {@code /ae2ltdiag connections} command available on any dedicated server.
 */
public final class AdapterConnectionCheck {

    /** Repetitions of the registry lookup per case. */
    public static final int STRESS_ROUNDS = 300;

    /** Ticks to wait after co-location stress before the post-tick rescan. */
    public static final int COLOCATED_SETTLE_TICKS = 40;

    /**
     * Clients-only + known-shape: the harness must not notify neighbors, or
     * co-located machines (and GameTest air) pop each other via updateShape.
     */
    private static final int HARNESS_SET_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final int COLOCATED_GRID = 5;
    private static final int COLOCATED_MAX_SLOTS = COLOCATED_GRID * COLOCATED_GRID * 6;

    public record Case(String name, String blockId, String expectedAdapter,
                       String recipeTypeId, boolean recipeTypeRequired) {

        /**
         * Recognition is gated on the target fork's classes; when only the
         * same-modid fork (e.g. AvaritiaNeo vs. the Avaritia fork) is present
         * the block exists but must stay inert — see {@link #AVARITIA_EXTREME_TABLE}.
         */
        public boolean forkGated() {
            return this == AVARITIA_EXTREME_TABLE || this == AVARITIA_EXTREME_SMITHING;
        }

        /**
         * Fork-only block: absent under AvaritiaNeo, present on the Avaritia
         * fork. Missing on Neo is a pass, not a broken mod connection.
         */
        public boolean forkOnlyBlock() {
            return this == AVARITIA_EXTREME_SMITHING;
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
    // Avaritia fork-only: absent on AvaritiaNeo (pass inert), recognized on the fork.
    private static final Case AVARITIA_EXTREME_SMITHING = addCase("conn40_avaritia_extreme_smithing",
            "avaritia:extreme_smithing_table", "AvaritiaExtremeSmithingAdapter",
            "avaritia:extreme_smithing_recipe", false);

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
     * A placed case block plus the saved original world at {@code pos} (and
     * optionally the block below for fire-like cases), so the harness can
     * restore the spawn grid instead of leaving air.
     */
    public record Placement(BlockPos pos, BlockState original, @Nullable CompoundTag originalBe,
                            @Nullable BlockState belowOriginal, @Nullable CompoundTag belowBe) {
    }

    @Nullable
    private static CompoundTag captureBe(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        return be == null ? null : be.saveWithFullMetadata();
    }

    private static void restoreBlock(ServerLevel level, BlockPos pos, BlockState state,
                                     @Nullable CompoundTag beTag) {
        level.setBlock(pos, state, HARNESS_SET_FLAGS);
        if (beTag == null) {
            return;
        }
        var restored = BlockEntity.loadStatic(pos, state, beTag);
        if (restored != null) {
            level.setBlockEntity(restored);
        }
    }

    @Nullable
    private static Placement placeCaseBlock(ServerLevel level, BlockPos pos, Case c) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(c.blockId()));
        if (block == null) {
            return null;
        }
        var original = level.getBlockState(pos);
        var originalBe = captureBe(level, pos);
        BlockState belowOriginal = null;
        CompoundTag belowBe = null;
        if (c.needsSturdyBase()) {
            belowOriginal = level.getBlockState(pos.below());
            belowBe = captureBe(level, pos.below());
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), HARNESS_SET_FLAGS);
        }
        level.setBlock(pos, block.defaultBlockState(), HARNESS_SET_FLAGS);
        if (level.getBlockState(pos).getBlock() == block) {
            return new Placement(pos, original, originalBe, belowOriginal, belowBe);
        }
        restoreBlock(level, pos, original, originalBe);
        if (belowOriginal != null) {
            restoreBlock(level, pos.below(), belowOriginal, belowBe);
        }
        return null;
    }

    private static void undoPlacement(ServerLevel level, Placement placement) {
        restoreBlock(level, placement.pos(), placement.original(), placement.originalBe());
        if (placement.belowOriginal() != null) {
            restoreBlock(level, placement.pos().below(), placement.belowOriginal(), placement.belowBe());
        }
    }

    public static void runCase(ServerLevel level, BlockPos pos, Case c, Sink sink) {
        sink.begin(c.name());
        var id = new ResourceLocation(c.blockId());
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            if (c.forkOnlyBlock() && !avaritiaForkClassesPresent()) {
                sink.pass(c.name(), "fork-only block " + id
                        + " absent — correctly inert (AvaritiaNeo split)");
                return;
            }
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
        try {
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
                sink.pass(c.name(), "block present but fork classes absent — correctly inert "
                        + "(AvaritiaNeo split; recognition requires the fork's TierCraftTile)");
                return;
            }
            if (found == null) {
                sink.fail(c.name(), "MultiblockAdapterRegistry.find returned null at round " + failedRound
                        + " for " + id + " — adapter does not recognize the real machine block"
                        + " [blockEntity=" + (be == null ? "none" : be.getClass().getName()) + "]");
                return;
            }
            var actual = found.getClass().getSimpleName();
            if (!actual.equals(c.expectedAdapter())) {
                sink.fail(c.name(), "recognized by " + actual + ", expected " + c.expectedAdapter());
                return;
            }
            String typeSummary = "n/a";
            if (c.recipeTypeId() != null) {
                var type = AdapterRecipeTypes.find(new ResourceLocation(c.recipeTypeId()));
                if (type == null && c.recipeTypeRequired()) {
                    sink.fail(c.name(), "recipe type " + c.recipeTypeId()
                            + " not resolvable via AdapterRecipeTypes (checked Forge + vanilla registries)");
                    return;
                }
                typeSummary = type == null ? c.recipeTypeId() + " absent (optional — fork split)"
                        : c.recipeTypeId() + " present";
            }
            var harvest = checkIdleDispatchHarvest(level, placed.pos(), found, c.name(), sink);
            if (harvest == null) {
                return;
            }
            sink.pass(c.name(), "recognized as " + actual + " x" + STRESS_ROUNDS
                    + " rounds; recipe type: " + typeSummary + "; " + harvest);
        } finally {
            undoPlacement(level, placed);
        }
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
        var original = level.getBlockState(pos);
        var originalBe = captureBe(level, pos);
        level.setBlock(pos, ForgeRegistries.BLOCKS.getValue(id).defaultBlockState(), HARNESS_SET_FLAGS);
        try {
            var be = level.getBlockEntity(pos);
            for (int round = 0; round < STRESS_ROUNDS; round++) {
                var found = MultiblockAdapterRegistry.find(level, pos, be);
                if (found != null) {
                    sink.fail(c.name(), "support block " + id + " was falsely recognized as a main block by "
                            + found.getClass().getSimpleName() + " at round " + round);
                    return;
                }
            }
            sink.pass(c.name(), "never recognized as a main block x" + STRESS_ROUNDS + " rounds");
        } finally {
            restoreBlock(level, pos, original, originalBe);
        }
    }

    /**
     * AvaritiaNeo vs fork split: fork-only blocks/types must be absent when
     * fork classes are missing, and present when the fork is installed.
     */
    public static void runForkSplit(ServerLevel level, Sink sink) {
        sink.begin("conn37_avaritia_fork_split");
        boolean fork = avaritiaForkClassesPresent();
        if (!fork) {
            for (var blockId : AVARITIA_FORK_BLOCKS) {
                if (ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(blockId))) {
                    sink.fail("conn37_avaritia_fork_split", "fork block " + blockId
                            + " is present without fork classes — unexpected Avaritia layout");
                    return;
                }
            }
            for (var typeId : AVARITIA_FORK_TYPES) {
                if (AdapterRecipeTypes.find(new ResourceLocation(typeId)) != null) {
                    sink.fail("conn37_avaritia_fork_split", "fork recipe type " + typeId
                            + " is present without fork classes — unexpected Avaritia layout");
                    return;
                }
            }
            sink.pass("conn37_avaritia_fork_split",
                    "fork blocks and types correctly absent under AvaritiaNeo");
            return;
        }
        var missingBlocks = new ArrayList<String>();
        for (var blockId : AVARITIA_FORK_BLOCKS) {
            if (!ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(blockId))) {
                missingBlocks.add(blockId);
            }
        }
        var missingTypes = new ArrayList<String>();
        for (var typeId : AVARITIA_FORK_TYPES) {
            if (AdapterRecipeTypes.find(new ResourceLocation(typeId)) == null) {
                missingTypes.add(typeId);
            }
        }
        if (!missingBlocks.isEmpty() || !missingTypes.isEmpty()) {
            sink.fail("conn37_avaritia_fork_split", "Avaritia fork classes present but missing "
                    + "blocks=" + missingBlocks + " types=" + missingTypes);
            return;
        }
        sink.pass("conn37_avaritia_fork_split",
                "fork blocks and types present with Avaritia fork classes");
    }

    public static void runInventory(ServerLevel level, Sink sink) {
        sink.begin("conn39_registry_inventory");
        var active = MultiblockAdapterRegistry.activeAdapters();
        var names = new LinkedHashSet<String>();
        for (var adapter : active) {
            names.add(adapter.getClass().getSimpleName());
            sink.info("conn39_registry_inventory",
                    "active: " + adapter.getClass().getName() + " (priority " + adapter.priority() + ")");
        }
        var expected = expectedAdapterNames();
        var missingAdapters = new ArrayList<String>();
        for (var name : expected) {
            if (!names.contains(name)) {
                missingAdapters.add(name);
            }
        }
        if (!missingAdapters.isEmpty()) {
            sink.fail("conn39_registry_inventory",
                    "missing registered adapters " + missingAdapters
                            + " (active=" + names.size() + ")");
            return;
        }
        var missingMods = new ArrayList<String>();
        for (var c : CASES.values()) {
            if (c.forkOnlyBlock() && !avaritiaForkClassesPresent()) {
                continue;
            }
            if (!ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(c.blockId()))) {
                missingMods.add(c.blockId());
            }
        }
        if (!missingMods.isEmpty()) {
            sink.fail("conn39_registry_inventory",
                    "compatible-mod blocks not registered: " + missingMods
                            + " — target mods are not all loaded");
            return;
        }
        sink.pass("conn39_registry_inventory",
                names.size() + " adapters active; all expected target-mod blocks registered");
    }

    public static Set<String> expectedAdapterNames() {
        var names = new LinkedHashSet<String>();
        for (var c : CASES.values()) {
            names.add(c.expectedAdapter());
        }
        return names;
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
     * Blocks that pop at their first grid slot (fire-like {@code canSurvive}
     * rules over non-sturdy decorations) retry later in-bounds slots. Fork-only
     * blocks (extreme smithing) stay skipped under AvaritiaNeo; the shared
     * extreme table is still placed so the scan can assert it stays inert.
     */
    public static Colocated placeColocated(ServerLevel level, BlockPos origin, Sink sink) {
        sink.begin("conn38_all_adapters_colocated");
        var placements = new ArrayList<Placement>();
        var placedCases = new ArrayList<Case>();
        var skipped = new ArrayList<String>();
        var missingMods = new ArrayList<String>();
        int slot = 0;
        int expected = 0;
        for (var c : CASES.values()) {
            if (c.forkOnlyBlock() && !avaritiaForkClassesPresent()) {
                continue;
            }
            var id = new ResourceLocation(c.blockId());
            if (!ForgeRegistries.BLOCKS.containsKey(id)) {
                missingMods.add(c.blockId());
                continue;
            }
            expected++;
            Placement placed = null;
            for (int attempt = 0; attempt < COLOCATED_GRID && placed == null; attempt++) {
                if (slot >= COLOCATED_MAX_SLOTS) {
                    break;
                }
                placed = placeCaseBlock(level, colocatedSlot(origin, slot++), c);
            }
            if (placed == null) {
                skipped.add(c.blockId());
                sink.info("conn38_all_adapters_colocated",
                        "skipped unplaceable " + c.blockId()
                                + " (standalone placement failed in-bounds)");
                continue;
            }
            placements.add(placed);
            placedCases.add(c);
        }
        if (!missingMods.isEmpty()) {
            removeColocated(level, new Colocated(List.copyOf(placements),
                    List.copyOf(placedCases)));
            sink.fail("conn38_all_adapters_colocated",
                    "compatible-mod blocks not registered: " + missingMods);
            return null;
        }
        if (placements.isEmpty()) {
            sink.fail("conn38_all_adapters_colocated",
                    "no adapter main blocks could be placed (unplaceable: " + skipped + ")");
            return null;
        }
        if (!skipped.isEmpty()) {
            sink.info("conn38_all_adapters_colocated", "placed " + placements.size() + "/"
                    + expected + ", skipped unplaceable " + skipped);
        }
        return new Colocated(List.copyOf(placements), List.copyOf(placedCases));
    }

    private static BlockPos colocatedSlot(BlockPos origin, int slot) {
        int x = slot % COLOCATED_GRID;
        int z = (slot / COLOCATED_GRID) % COLOCATED_GRID;
        int y = slot / (COLOCATED_GRID * COLOCATED_GRID);
        return origin.offset(x, y, z);
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
            var c = state.cases().get(i);
            boolean expectInert = c.forkGated() && !c.forkOnlyBlock() && !avaritiaForkClassesPresent();
            if (expectInert) {
                if (found != null) {
                    sink.fail("conn38_all_adapters_colocated", "co-located scan at " + pos + " ("
                            + c.blockId() + "): expected inert under AvaritiaNeo, got "
                            + found.getClass().getSimpleName());
                    return false;
                }
                continue;
            }
            var expected = c.expectedAdapter();
            if (found == null || !found.getClass().getSimpleName().equals(expected)) {
                sink.fail("conn38_all_adapters_colocated", "co-located scan at " + pos + " ("
                        + c.blockId() + "): expected " + expected + ", got "
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

    /**
     * Idle dispatch/harvest surface for every co-located machine. Must run
     * while the blocks are still placed; a failed vacuum/bind/plan check
     * reports as {@code conn41_idle_dispatch_harvest}. Does not call
     * {@link Sink#pass} so GameTest runners can succeed only once.
     */
    public static boolean runDispatchHarvestColocated(ServerLevel level, Colocated state, Sink sink) {
        sink.begin("conn41_idle_dispatch_harvest");
        for (int i = 0; i < state.placements().size(); i++) {
            var pos = state.placements().get(i).pos();
            var c = state.cases().get(i);
            var found = MultiblockAdapterRegistry.find(level, pos, level.getBlockEntity(pos));
            boolean expectInert = c.forkGated() && !c.forkOnlyBlock() && !avaritiaForkClassesPresent();
            if (expectInert) {
                if (found != null) {
                    sink.fail("conn41_idle_dispatch_harvest", "expected inert under AvaritiaNeo at "
                            + pos + " (" + c.blockId() + "), got " + found.getClass().getSimpleName());
                    return false;
                }
                continue;
            }
            if (found == null) {
                sink.fail("conn41_idle_dispatch_harvest", "adapter vanished before idle harvest at "
                        + pos + " (" + c.blockId() + ")");
                return false;
            }
            var neighbors = captureNeighborBe(level, state, i);
            var harvest = checkIdleDispatchHarvest(level, pos, found,
                    "conn41_idle_dispatch_harvest", sink);
            if (harvest == null) {
                return false;
            }
            if (!neighborsUnchanged(level, state, i, neighbors, sink)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fail-closed idle dispatch/harvest: unmatched dummy patterns must not
     * crash or mutate the world, and {@code extractOutputs} must not pull
     * anything the provider did not dispatch.
     *
     * @return a short pass summary, or {@code null} after {@link Sink#fail}
     */
    @Nullable
    static String checkIdleDispatchHarvest(ServerLevel level, BlockPos pos,
                                           MultiblockAdapter adapter, String name, Sink sink) {
        var source = IActionSource.empty();
        var dummy = dummyUnmatchedPattern();
        var emptyInputs = new KeyCounter[] {new KeyCounter()};
        var dummyHandle = "ae2ltdiag-unmatched-handle";
        ItemEntity bait = null;
        try {
            bait = spawnHarvestBait(level, pos);
            if (bait == null) {
                sink.fail(name, "failed to spawn harvest bait at " + pos);
                return null;
            }
            var before = captureFingerprint(level, pos);
            if (!extractMustBeEmpty(level, pos, adapter, new AllowedOutputFilter(), source,
                    name, "empty filter", sink)
                    || !fingerprintUnchanged(level, pos, before, name, "empty-filter harvest", sink)) {
                return null;
            }

            var barrierFilter = new AllowedOutputFilter();
            barrierFilter.allowStrict(AEItemKey.of(Items.BARRIER));
            if (!extractMustBeEmpty(level, pos, adapter, barrierFilter, source,
                    name, "barrier filter", sink)
                    || !fingerprintUnchanged(level, pos, before, name, "barrier-filter harvest", sink)) {
                return null;
            }

            var baitFilter = new AllowedOutputFilter();
            baitFilter.allowStrict(AEItemKey.of(Items.DIAMOND));
            if (!extractMustBeEmpty(level, pos, adapter, baitFilter, source,
                    name, "diamond-bait filter", sink)
                    || !fingerprintUnchanged(level, pos, before, name, "diamond-bait harvest", sink)) {
                return null;
            }

            BindingResult bound;
            try {
                bound = adapter.bind(level, pos, dummy);
            } catch (RuntimeException | LinkageError e) {
                sink.fail(name, "bind(dummy unmatched pattern) at " + pos + " threw "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                return null;
            }
            if (bound != null) {
                sink.fail(name, "bind matched dummy barrier pattern at " + pos + " via "
                        + bound.handle().getClass().getSimpleName()
                        + " — idle harness must not obtain a live recipe handle");
                return null;
            }
            if (!fingerprintUnchanged(level, pos, before, name, "bind", sink)) {
                return null;
            }

            try {
                adapter.canDispatch(level, pos, dummyHandle);
                adapter.canDispatch(level, pos, dummyHandle, AdapterPersistentScope.NOOP);
            } catch (RuntimeException | LinkageError e) {
                sink.fail(name, "canDispatch(dummy handle) at " + pos + " threw "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                return null;
            }
            if (!fingerprintUnchanged(level, pos, before, name, "canDispatch", sink)) {
                return null;
            }

            DispatchPlan plan;
            try {
                plan = adapter.planWithBinding(level, pos, dummy, emptyInputs, dummyHandle, source,
                        AdapterPersistentScope.NOOP);
            } catch (RuntimeException | LinkageError e) {
                sink.fail(name, "planWithBinding(dummy handle) at " + pos + " threw "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                return null;
            }
            if (plan != null) {
                sink.fail(name, "planWithBinding produced a live plan for a dummy handle at " + pos);
                return null;
            }
            if (!fingerprintUnchanged(level, pos, before, name, "planWithBinding", sink)) {
                return null;
            }

            if (adapter instanceof VirtualCraftingAdapter virtual) {
                VirtualCraftingResult virtualPlan;
                try {
                    virtualPlan = virtual.planVirtualWithBinding(
                            level, pos, dummy, emptyInputs, dummyHandle, source);
                } catch (RuntimeException | LinkageError e) {
                    sink.fail(name, "planVirtualWithBinding(dummy handle) at " + pos + " threw "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                    return null;
                }
                if (virtualPlan != null) {
                    sink.fail(name, "planVirtualWithBinding produced a result for a dummy handle at "
                            + pos + " (harness never commits virtual plans)");
                    return null;
                }
                if (!fingerprintUnchanged(level, pos, before, name, "planVirtualWithBinding", sink)) {
                    return null;
                }
            }
            return "idle harvest empty; unmatched bind/plan fail-closed";
        } finally {
            discardBait(bait);
        }
    }

    @Nullable
    private static ItemEntity spawnHarvestBait(ServerLevel level, BlockPos pos) {
        var entity = new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 2.2, pos.getZ() + 0.5,
                new ItemStack(Items.DIAMOND));
        entity.setDeltaMovement(0, 0, 0);
        entity.setNoGravity(true);
        entity.setPickUpDelay(Short.MAX_VALUE);
        entity.setNeverPickUp();
        entity.getPersistentData().putBoolean("ae2ltdiag_harvest_bait", true);
        if (!level.addFreshEntity(entity)) {
            return null;
        }
        return entity;
    }

    private static void discardBait(@Nullable ItemEntity bait) {
        if (bait != null && bait.isAlive()) {
            bait.discard();
        }
    }

    private static List<CompoundTag> captureNeighborBe(ServerLevel level, Colocated state, int skipIndex) {
        var tags = new ArrayList<CompoundTag>(state.placements().size());
        for (int i = 0; i < state.placements().size(); i++) {
            if (i == skipIndex) {
                tags.add(null);
                continue;
            }
            var be = level.getBlockEntity(state.placements().get(i).pos());
            tags.add(be == null ? null : be.saveWithFullMetadata());
        }
        return tags;
    }

    private static boolean neighborsUnchanged(ServerLevel level, Colocated state, int skipIndex,
                                              List<CompoundTag> before, Sink sink) {
        for (int i = 0; i < state.placements().size(); i++) {
            if (i == skipIndex) {
                continue;
            }
            var pos = state.placements().get(i).pos();
            var be = level.getBlockEntity(pos);
            var after = be == null ? null : be.saveWithFullMetadata();
            if (!nbtEqual(before.get(i), after)) {
                sink.fail("conn41_idle_dispatch_harvest",
                        "idle harvest at " + state.placements().get(skipIndex).pos()
                                + " mutated neighbor " + state.cases().get(i).blockId() + " at " + pos);
                return false;
            }
        }
        return true;
    }

    private static boolean extractMustBeEmpty(ServerLevel level, BlockPos pos,
                                              MultiblockAdapter adapter, AllowedOutputFilter filter,
                                              IActionSource source, String name, String label, Sink sink) {
        List<GenericStack> pulled;
        try {
            pulled = adapter.extractOutputs(level, pos, filter, source, AdapterPersistentScope.NOOP);
        } catch (RuntimeException | LinkageError e) {
            sink.fail(name, "extractOutputs(" + label + ") threw "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
        if (pulled == null) {
            sink.fail(name, "extractOutputs(" + label + ") returned null");
            return false;
        }
        if (!pulled.isEmpty()) {
            sink.fail(name, "extractOutputs(" + label + ") vacuumed " + pulled.size()
                    + " stack(s) from an idle unowned machine: " + pulled);
            return false;
        }
        return true;
    }

    private static boolean fingerprintUnchanged(ServerLevel level, BlockPos pos,
                                                WorldFingerprint before, String name,
                                                String phase, Sink sink) {
        var after = captureFingerprint(level, pos);
        if (!Objects.equals(before.state(), after.state())) {
            sink.fail(name, phase + " mutated block state at " + pos
                    + " from " + before.state() + " to " + after.state());
            return false;
        }
        if (!nbtEqual(before.be(), after.be())) {
            sink.fail(name, phase + " mutated block-entity NBT at " + pos);
            return false;
        }
        var missing = new ArrayList<UUID>();
        for (var item : before.items()) {
            var found = after.items().stream()
                    .filter(candidate -> candidate.id().equals(item.id()))
                    .findFirst()
                    .orElse(null);
            if (found == null) {
                missing.add(item.id());
                continue;
            }
            if (!found.itemId().equals(item.itemId()) || found.count() != item.count()) {
                sink.fail(name, phase + " mutated nearby ItemEntity " + item.id()
                        + " from " + item.itemId() + "x" + item.count()
                        + " to " + found.itemId() + "x" + found.count());
                return false;
            }
        }
        if (!missing.isEmpty()) {
            sink.fail(name, phase + " removed nearby ItemEntity " + missing
                    + " around " + pos);
            return false;
        }
        return true;
    }

    private static WorldFingerprint captureFingerprint(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0)).stream()
                .map(entity -> new EntitySnap(
                        entity.getUUID(),
                        String.valueOf(ForgeRegistries.ITEMS.getKey(entity.getItem().getItem())),
                        entity.getItem().getCount(),
                        entity.getX(), entity.getY(), entity.getZ()))
                .sorted(Comparator.comparing(EntitySnap::id))
                .toList();
        return new WorldFingerprint(
                level.getBlockState(pos),
                be == null ? null : be.saveWithFullMetadata(),
                items);
    }

    private static boolean nbtEqual(@Nullable CompoundTag left, @Nullable CompoundTag right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.equals(right);
    }

    private static IPatternDetails dummyUnmatchedPattern() {
        var output = new GenericStack(AEItemKey.of(Items.BARRIER), 1);
        var definition = AEItemKey.of(Items.BARRIER);
        var inputs = new IPatternDetails.IInput[0];
        return (IPatternDetails) java.lang.reflect.Proxy.newProxyInstance(
                IPatternDetails.class.getClassLoader(),
                new Class<?>[] {IPatternDetails.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "ae2ltdiag-dummy-unmatched-pattern";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> Boolean.valueOf(proxy == args[0]);
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    }
                    if (method.isDefault()) {
                        return java.lang.reflect.InvocationHandler.invokeDefault(
                                proxy, method, args == null ? new Object[0] : args);
                    }
                    return switch (method.getName()) {
                        case "getDefinition" -> definition;
                        case "getInputs" -> inputs;
                        case "getOutputs" -> new GenericStack[] {output};
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }

    private record WorldFingerprint(BlockState state, @Nullable CompoundTag be, List<EntitySnap> items) {
    }

    private record EntitySnap(UUID id, String itemId, int count, double x, double y, double z) {
    }
}
