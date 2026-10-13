package com.moakiee.ae2lt.packaged.item;

import net.minecraft.resources.Identifier;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;

/**
 * Central registry of adapter ids used as a contract between:
 * <ul>
 *   <li>{@code MultiblockAdapter.requiredAdapterId} &mdash; what an adapter needs.</li>
 *   <li>{@link MultiblockAdapterItem} &mdash; what an installed packaged core covers.</li>
 * </ul>
 *
 * <p>Naming convention:
 * <pre>
 *   ae2ltpp:&lt;target_mod_id&gt;/&lt;recipe_type&gt;_packaged_core
 * </pre>
 * The target mod id is the upstream mod (e.g. {@code actuallyadditions}), the
 * recipe type is the upstream-side recipe registry name (e.g. {@code laser}),
 * and the {@code _packaged_core} suffix marks this as a provider-core
 * namespace rather than a raw resource.
 *
 * <p>EC's four table tiers share the {@code extendedcrafting/table} recipe
 * type, so they're distinguished by a {@code _tierN} infix inside the recipe
 * portion to keep the scheme uniform.
 *
 * <p>These strings are stable: changing one orphans any existing in-world key
 * core holding the old id.
 */
public final class AdapterIds {

    private AdapterIds() {
    }

    public static final Identifier AA_RECONSTRUCTOR =
            id("actuallyadditions/laser_packaged_core");
    public static final Identifier AA_EMPOWERER =
            id("actuallyadditions/empower_packaged_core");
    public static final Identifier ARS_APPARATUS =
            id("ars_nouveau/enchanting_apparatus_packaged_core");
    public static final Identifier ARS_IMBUEMENT =
            id("ars_nouveau/imbuement_packaged_core");
    public static final Identifier DE_FUSION =
            id("draconicevolution/fusion_crafting_packaged_core");
    public static final Identifier EC_BASIC =
            id("extendedcrafting/table_tier1_packaged_core");
    public static final Identifier EC_ADVANCED =
            id("extendedcrafting/table_tier2_packaged_core");
    public static final Identifier EC_ELITE =
            id("extendedcrafting/table_tier3_packaged_core");
    public static final Identifier EC_ULTIMATE =
            id("extendedcrafting/table_tier4_packaged_core");
    public static final Identifier EC_ENDER =
            id("extendedcrafting/ender_crafter_packaged_core");
    public static final Identifier EC_FLUX =
            id("extendedcrafting/flux_crafter_packaged_core");
    public static final Identifier EC_COMBINATION =
            id("extendedcrafting/combination_packaged_core");
    public static final Identifier MA_AWAKENING =
            id("mysticalagriculture/awakening_packaged_core");
    public static final Identifier MA_INFUSION =
            id("mysticalagriculture/infusion_packaged_core");
    public static final Identifier MALUM_SPIRIT_FOCUSING =
            id("malum/spirit_focusing_packaged_core");
    public static final Identifier MALUM_SPIRIT_INFUSION =
            id("malum/spirit_infusion_packaged_core");
    public static final Identifier OCCULTISM_RITUAL =
            id("occultism/ritual_packaged_core");
    public static final Identifier OCCULTISM_SPIRIT_FIRE =
            id("occultism/spirit_fire_packaged_core");
    /**
     * Single packaged core covering every Mekanism: More Machines large multiblock
     * (chemical infuser, electrolytic separator, antiprotonic nucleosynthesizer,
     * rotary condensentrator, solar neutron activator, pigment mixer). The
     * generic {@code MekanismMoreMachinesAdapter} dispatches between them at
     * runtime via {@code MekmmMachines}.
     */
    public static final Identifier MEKMM =
            id("mekmm/large_machine_packaged_core");
    public static final Identifier BOTANIA_PETAL_APOTHECARY =
            id("botania/petal_apothecary_packaged_core");
    public static final Identifier BOTANIA_MANA_POOL =
            id("botania/mana_pool_packaged_core");
    public static final Identifier BOTANIA_ALFHEIM_PORTAL =
            id("botania/alfheim_portal_packaged_core");
    public static final Identifier BOTANIA_TERRA_PLATE =
            id("botania/terra_plate_packaged_core");
    public static final Identifier BOTANIA_RUNIC_ALTAR =
            id("botania/runic_altar_packaged_core");
    public static final Identifier AVARITIA_SCULK_TABLE =
            id("avaritia/crafting_table_tier1_packaged_core");
    public static final Identifier AVARITIA_NETHER_TABLE =
            id("avaritia/crafting_table_tier2_packaged_core");
    public static final Identifier AVARITIA_END_TABLE =
            id("avaritia/crafting_table_tier3_packaged_core");
    public static final Identifier AVARITIA_EXTREME_TABLE =
            id("avaritia/crafting_table_tier4_packaged_core");
    public static final Identifier AVARITIA_EXTREME_SMITHING =
            id("avaritia/extreme_smithing_packaged_core");

    public static final Identifier CHISELING = id("chiseling/conversion_packaged_core");

    public static final Identifier RECHISELED = id("rechiseled/chiseling_packaged_core");

    public static final Identifier CHISEL = id("chisel/chiseling_packaged_core");

    public static final Identifier CHIPPED = id("chipped/workbench_packaged_core");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AE2LTPackagedProvider.MODID, path);
    }
}
