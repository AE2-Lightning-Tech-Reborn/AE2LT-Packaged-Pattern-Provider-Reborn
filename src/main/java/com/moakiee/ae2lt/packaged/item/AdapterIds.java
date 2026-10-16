package com.moakiee.ae2lt.packaged.item;

import net.minecraft.resources.ResourceLocation;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;

/**
 * Stable ids shared by adapters and packaged cores. Paths follow
 * {@code <target_mod>/<recipe_type>[_tierN]_packaged_core}.
 * Changing an existing id breaks saved cores and provider bindings.
 */
public final class AdapterIds {

    public static final ResourceLocation CHISELING = new ResourceLocation("ae2ltpp", "chiseling_packaged_core");

    private AdapterIds() {
    }

    public static final ResourceLocation AA_RECONSTRUCTOR =
            id("actuallyadditions/laser_packaged_core");
    public static final ResourceLocation AA_EMPOWERER =
            id("actuallyadditions/empower_packaged_core");
    public static final ResourceLocation ARS_APPARATUS =
            id("ars_nouveau/enchanting_apparatus_packaged_core");
    public static final ResourceLocation ARS_IMBUEMENT =
            id("ars_nouveau/imbuement_packaged_core");
    public static final ResourceLocation DE_FUSION =
            id("draconicevolution/fusion_crafting_packaged_core");
    public static final ResourceLocation EC_BASIC =
            id("extendedcrafting/table_tier1_packaged_core");
    public static final ResourceLocation EC_ADVANCED =
            id("extendedcrafting/table_tier2_packaged_core");
    public static final ResourceLocation EC_ELITE =
            id("extendedcrafting/table_tier3_packaged_core");
    public static final ResourceLocation EC_ULTIMATE =
            id("extendedcrafting/table_tier4_packaged_core");
    public static final ResourceLocation EC_ENDER =
            id("extendedcrafting/ender_crafter_packaged_core");
    public static final ResourceLocation EC_FLUX =
            id("extendedcrafting/flux_crafter_packaged_core");
    public static final ResourceLocation EC_COMBINATION =
            id("extendedcrafting/combination_packaged_core");
    public static final ResourceLocation MA_AWAKENING =
            id("mysticalagriculture/awakening_packaged_core");
    public static final ResourceLocation MA_INFUSION =
            id("mysticalagriculture/infusion_packaged_core");
    public static final ResourceLocation MALUM_SPIRIT_FOCUSING =
            id("malum/spirit_focusing_packaged_core");
    public static final ResourceLocation MALUM_SPIRIT_INFUSION =
            id("malum/spirit_infusion_packaged_core");
    public static final ResourceLocation OCCULTISM_RITUAL =
            id("occultism/ritual_packaged_core");
    public static final ResourceLocation OCCULTISM_SPIRIT_FIRE =
            id("occultism/spirit_fire_packaged_core");
    /** Shared core for Mekanism: More Machines large multiblocks. */
    public static final ResourceLocation MEKMM =
            id("mekmm/large_machine_packaged_core");
    public static final ResourceLocation BOTANIA_PETAL_APOTHECARY =
            id("botania/petal_apothecary_packaged_core");
    public static final ResourceLocation BOTANIA_MANA_POOL =
            id("botania/mana_pool_packaged_core");
    public static final ResourceLocation BOTANIA_ALFHEIM_PORTAL =
            id("botania/alfheim_portal_packaged_core");
    public static final ResourceLocation BOTANIA_TERRA_PLATE =
            id("botania/terra_plate_packaged_core");
    public static final ResourceLocation BOTANIA_RUNIC_ALTAR =
            id("botania/runic_altar_packaged_core");
    public static final ResourceLocation AVARITIA_SCULK_TABLE =
            id("avaritia/crafting_table_tier1_packaged_core");
    public static final ResourceLocation AVARITIA_NETHER_TABLE =
            id("avaritia/crafting_table_tier2_packaged_core");
    public static final ResourceLocation AVARITIA_END_TABLE =
            id("avaritia/crafting_table_tier3_packaged_core");
    public static final ResourceLocation AVARITIA_EXTREME_TABLE =
            id("avaritia/crafting_table_tier4_packaged_core");
    public static final ResourceLocation AVARITIA_EXTREME_SMITHING =
            id("avaritia/extreme_smithing_packaged_core");
    public static final ResourceLocation BLOOD_MAGIC_ALTAR = id("bloodmagic/altar_packaged_core");
    public static final ResourceLocation FORBIDDEN_ARCANUS_CLIBANO = id("forbidden_arcanus/clibano_packaged_core");
    public static final ResourceLocation FORBIDDEN_ARCANUS_HEPHAESTUS_FORGE = id("forbidden_arcanus/hephaestus_forge_packaged_core");

    private static ResourceLocation id(String path) {
        return new ResourceLocation(AE2LTPackagedProvider.MODID, path);
    }
}
