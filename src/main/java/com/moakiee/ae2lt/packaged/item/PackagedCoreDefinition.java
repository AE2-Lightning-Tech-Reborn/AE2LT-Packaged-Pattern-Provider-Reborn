package com.moakiee.ae2lt.packaged.item;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredItem;

import com.moakiee.ae2lt.packaged.registry.PPItems;

/**
 * Single source of truth for optional integration packaged cores.
 * Keep target mod ids aligned with the upstream mod metadata.
 */
public record PackagedCoreDefinition(
        String itemId,
        String targetModId,
        ResourceLocation targetItemId) {

    private static final String ACTUALLY_ADDITIONS = "actuallyadditions";
    private static final String ARS_NOUVEAU = "ars_nouveau";
    private static final String DRACONIC_EVOLUTION = "draconicevolution";
    private static final String EXTENDED_CRAFTING = "extendedcrafting";
    private static final String MYSTICAL_AGRICULTURE = "mysticalagriculture";
    private static final String MALUM = "malum";
    private static final String OCCULTISM = "occultism";
    private static final String MEKANISM_MORE_MACHINE = "mekmm";
    private static final String BOTANIA = "botania";
    private static final String AVARITIA = "avaritia";

    private static final List<PackagedCoreDefinition> ALL = List.of(
            core("naturesaura_altar_packaged_core", "naturesaura", "nature_altar"),
            core("naturesaura_tree_ritual_packaged_core", "naturesaura", "wood_stand"),
            core("naturesaura_offering_packaged_core", "naturesaura", "offering_table"),
            core("aa_reconstructor_packaged_core", ACTUALLY_ADDITIONS, "atomic_reconstructor"),
            core("aa_empowerer_packaged_core", ACTUALLY_ADDITIONS, "empowerer"),
            core("ars_apparatus_packaged_core", ARS_NOUVEAU, "enchanting_apparatus"),
            core("ars_imbuement_packaged_core", ARS_NOUVEAU, "imbuement_chamber"),
            core("ars_ritual_brazier_packaged_core", ARS_NOUVEAU, "ritual_brazier"),
            core("de_fusion_packaged_core", DRACONIC_EVOLUTION, "crafting_core"),
            core("ec_basic_table_packaged_core", EXTENDED_CRAFTING, "basic_table"),
            core("ec_advanced_table_packaged_core", EXTENDED_CRAFTING, "advanced_table"),
            core("ec_elite_table_packaged_core", EXTENDED_CRAFTING, "elite_table"),
            core("ec_ultimate_table_packaged_core", EXTENDED_CRAFTING, "ultimate_table"),
            core("ec_ender_crafter_packaged_core", EXTENDED_CRAFTING, "ender_crafter"),
            core("ec_flux_crafter_packaged_core", EXTENDED_CRAFTING, "flux_crafter"),
            core("ec_combination_packaged_core", EXTENDED_CRAFTING, "crafting_core"),
            core("ma_awakening_altar_packaged_core", MYSTICAL_AGRICULTURE, "awakening_altar"),
            core("ma_infusion_altar_packaged_core", MYSTICAL_AGRICULTURE, "infusion_altar"),
            core("malum_spirit_focusing_packaged_core", MALUM, "spirit_crucible"),
            core("malum_spirit_infusion_packaged_core", MALUM, "spirit_altar"),
            core("occultism_ritual_packaged_core", OCCULTISM, "golden_sacrificial_bowl"),
            core("occultism_spirit_fire_packaged_core", OCCULTISM, "spirit_fire"),
            core("mekmm_packaged_core", MEKANISM_MORE_MACHINE, "large_antiprotonic_nucleosynthesizer"),
            core("botania_petal_apothecary_packaged_core", BOTANIA, "apothecary_default"),
            core("botania_mana_pool_packaged_core", BOTANIA, "mana_pool"),
            core("botania_alfheim_portal_packaged_core", BOTANIA, "alfheim_portal"),
            core("botania_terra_plate_packaged_core", BOTANIA, "terra_plate"),
            core("botania_runic_altar_packaged_core", BOTANIA, "runic_altar"),
            core("avaritia_sculk_table_packaged_core", AVARITIA, "sculk_crafting_table"),
            core("avaritia_nether_table_packaged_core", AVARITIA, "nether_crafting_table"),
            core("avaritia_end_table_packaged_core", AVARITIA, "end_crafting_table"),
            core("avaritia_extreme_table_packaged_core", AVARITIA, "extreme_crafting_table"),
            core("avaritia_extreme_smithing_packaged_core", AVARITIA, "extreme_smithing_table"),
            core("astral_illumination_packaged_core", "astralsorcery", "altar_illumination"),
            core("astral_resonance_packaged_core", "astralsorcery", "altar_resonance"),
            core("astral_luminance_packaged_core", "astralsorcery", "altar_luminance"),
            core("astral_radiance_packaged_core", "astralsorcery", "altar_radiance"),
            core("astral_infuser_packaged_core", "astralsorcery", "infuser"),
            core("chiseling_packaged_core", "chisel", "chisel"),
            core("apothic_infusion_packaged_core", "apothic_enchanting", "raven_enchanting_table"),
            core("maid_altar_packaged_core", "touhou_little_maid", "hakurei_gohei"),
            core("fa_hephaestus_forge_packaged_core", "forbidden_arcanus", "hephaestus_forge_tier_1"),
            core("fa_clibano_packaged_core", "forbidden_arcanus", "clibano_core"),
            core("fa_black_hole_packaged_core", "forbidden_arcanus", "dark_matter"),
            core("resonant_packaged_core", "ae2cs", "resonating_pattern_provider"),
            core("fd_cooking_pot_packaged_core", "farmersdelight", "cooking_pot"),
            core("ad_extreme_cooking_pot_packaged_core", "avaritia_delight", "extreme_cooking_pot"));

    public static List<PackagedCoreDefinition> all() {
        return ALL;
    }

    public static List<PackagedCoreDefinition> visibleWhen(Predicate<String> isModLoaded) {
        return ALL.stream()
                .filter(definition -> definition.itemId().equals("chiseling_packaged_core")
                        ? isModLoaded.test("chipped") || isModLoaded.test("rechiseled") || isModLoaded.test("chisel")
                        : isModLoaded.test(definition.targetModId()))
                .toList();
    }

    public DeferredItem<MultiblockAdapterItem> runtimeItem() {
        return switch (itemId) {
            case "naturesaura_altar_packaged_core" -> PPItems.NATURES_AURA_ALTAR_ADAPTER;
            case "naturesaura_tree_ritual_packaged_core" -> PPItems.NATURES_AURA_TREE_ADAPTER;
            case "naturesaura_offering_packaged_core" -> PPItems.NATURES_AURA_OFFERING_ADAPTER;
            case "aa_reconstructor_packaged_core" -> PPItems.AA_RECONSTRUCTOR_ADAPTER;
            case "aa_empowerer_packaged_core" -> PPItems.AA_EMPOWERER_ADAPTER;
            case "ars_apparatus_packaged_core" -> PPItems.ARS_APPARATUS_ADAPTER;
            case "ars_imbuement_packaged_core" -> PPItems.ARS_IMBUEMENT_ADAPTER;
            case "ars_ritual_brazier_packaged_core" -> PPItems.ARS_RITUAL_ADAPTER;
            case "de_fusion_packaged_core" -> PPItems.DE_FUSION_ADAPTER;
            case "ec_basic_table_packaged_core" -> PPItems.EC_BASIC_ADAPTER;
            case "ec_advanced_table_packaged_core" -> PPItems.EC_ADVANCED_ADAPTER;
            case "ec_elite_table_packaged_core" -> PPItems.EC_ELITE_ADAPTER;
            case "ec_ultimate_table_packaged_core" -> PPItems.EC_ULTIMATE_ADAPTER;
            case "ec_ender_crafter_packaged_core" -> PPItems.EC_ENDER_CRAFTER_ADAPTER;
            case "ec_flux_crafter_packaged_core" -> PPItems.EC_FLUX_CRAFTER_ADAPTER;
            case "ec_combination_packaged_core" -> PPItems.EC_COMBINATION_ADAPTER;
            case "ma_awakening_altar_packaged_core" -> PPItems.MA_AWAKENING_ADAPTER;
            case "ma_infusion_altar_packaged_core" -> PPItems.MA_INFUSION_ADAPTER;
            case "malum_spirit_focusing_packaged_core" -> PPItems.MALUM_SPIRIT_FOCUSING_ADAPTER;
            case "malum_spirit_infusion_packaged_core" -> PPItems.MALUM_SPIRIT_INFUSION_ADAPTER;
            case "occultism_ritual_packaged_core" -> PPItems.OCCULTISM_RITUAL_ADAPTER;
            case "occultism_spirit_fire_packaged_core" -> PPItems.OCCULTISM_SPIRIT_FIRE_ADAPTER;
            case "mekmm_packaged_core" -> PPItems.MEKMM_ADAPTER;
            case "botania_petal_apothecary_packaged_core" -> PPItems.BOTANIA_PETAL_APOTHECARY_ADAPTER;
            case "botania_mana_pool_packaged_core" -> PPItems.BOTANIA_MANA_POOL_ADAPTER;
            case "botania_alfheim_portal_packaged_core" -> PPItems.BOTANIA_ALFHEIM_PORTAL_ADAPTER;
            case "botania_terra_plate_packaged_core" -> PPItems.BOTANIA_TERRA_PLATE_ADAPTER;
            case "botania_runic_altar_packaged_core" -> PPItems.BOTANIA_RUNIC_ALTAR_ADAPTER;
            case "avaritia_sculk_table_packaged_core" -> PPItems.AVARITIA_SCULK_TABLE_ADAPTER;
            case "avaritia_nether_table_packaged_core" -> PPItems.AVARITIA_NETHER_TABLE_ADAPTER;
            case "avaritia_end_table_packaged_core" -> PPItems.AVARITIA_END_TABLE_ADAPTER;
            case "avaritia_extreme_table_packaged_core" -> PPItems.AVARITIA_EXTREME_TABLE_ADAPTER;
            case "avaritia_extreme_smithing_packaged_core" -> PPItems.AVARITIA_EXTREME_SMITHING_ADAPTER;
            case "astral_illumination_packaged_core" -> PPItems.ASTRAL_ILLUMINATION_ADAPTER;
            case "astral_resonance_packaged_core" -> PPItems.ASTRAL_RESONANCE_ADAPTER;
            case "astral_luminance_packaged_core" -> PPItems.ASTRAL_LUMINANCE_ADAPTER;
            case "astral_radiance_packaged_core" -> PPItems.ASTRAL_RADIANCE_ADAPTER;
            case "astral_infuser_packaged_core" -> PPItems.ASTRAL_INFUSER_ADAPTER;
            case "chiseling_packaged_core" -> PPItems.CHISELING_ADAPTER;
            case "apothic_infusion_packaged_core" -> PPItems.APOTHIC_INFUSION_ADAPTER;
            case "maid_altar_packaged_core" -> PPItems.MAID_ALTAR_ADAPTER;
            case "fa_hephaestus_forge_packaged_core" -> PPItems.FA_FORGE_ADAPTER;
            case "fa_clibano_packaged_core" -> PPItems.FA_CLIBANO_ADAPTER;
            case "fa_black_hole_packaged_core" -> PPItems.FA_BLACK_HOLE_ADAPTER;
            case "resonant_packaged_core" -> PPItems.RESONANT_ADAPTER;
            case "fd_cooking_pot_packaged_core" -> PPItems.FD_COOKING_POT_ADAPTER;
            case "ad_extreme_cooking_pot_packaged_core" -> PPItems.AD_COOKING_POT_ADAPTER;
            default -> throw new IllegalStateException("Unknown packaged core item id: " + itemId);
        };
    }

    /** Display the portable tool provided by the installed Chisel implementation. */
    public ResourceLocation displayItemId() {
        if (itemId.equals("chiseling_packaged_core")) {
            for (var id : List.of("chisel:chisel", "chisel:iron_chisel", "rechiseled:chisel", "chipped:chisel")) {
                var candidate = ResourceLocation.parse(id);
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(candidate)) return candidate;
            }
        }
        return targetItemId;
    }

    private static PackagedCoreDefinition core(
            String itemId,
            String targetModId,
            String targetItemPath) {
        return new PackagedCoreDefinition(
                itemId,
                targetModId,
                ResourceLocation.fromNamespaceAndPath(targetModId, targetItemPath));
    }
}
