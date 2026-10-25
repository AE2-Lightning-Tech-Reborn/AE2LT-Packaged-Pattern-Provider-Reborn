package com.moakiee.ae2lt.packaged;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.blockentity.AEBaseBlockEntity;

import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.blockentity.ExtendedPackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.blockentity.WirelessPackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterRegistration;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.chisel.ChippedWorkbenchAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.chisel.HandheldChiselAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.astral.AstralSorceryAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.aa.ActuallyAdditionsAtomicReconstructorAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.aa.ActuallyAdditionsEmpowererAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ars.ArsNouveauEnchantingApparatusAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ars.ArsNouveauImbuementChamberAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ars.ArsRitualBrazierAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ars.ArsScribesAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.naturesaura.NaturesAuraAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.avaritia.AvaritiaExtremeSmithingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.avaritia.AvaritiaTableAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.botania.AlfheimPortalAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.botania.ManaPoolAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.botania.PetalApothecaryAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.botania.RunicAltarAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.botania.TerraPlateAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.de.DraconicFusionCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingCombinationAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingEnderCrafterAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingFluxCrafterAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingTableAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ma.AwakeningAltarAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ma.InfusionAltarAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.malum.MalumSpiritFocusingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.malum.MalumSpiritInfusionAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.occultism.OccultismRitualAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.occultism.OccultismSpiritFireAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekanismMoreMachinesAdapter;
import com.moakiee.ae2lt.packaged.registry.PPBlockEntities;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPCreativeTabs;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.packaged.registry.PPMenuTypes;
import com.moakiee.ae2lt.packaged.registry.PPDataComponents;
import com.moakiee.ae2lt.packaged.network.ResonantCoreSelectPacket;
import com.moakiee.ae2lt.packaged.patternprovider.InsertOnlyPatternProviderReturnInventory;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity;

@Mod(AE2LTPackagedProvider.MODID)
public class AE2LTPackagedProvider {
    public static final String MODID = "ae2ltpp";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AE2LTPackagedProvider(IEventBus modEventBus, ModContainer modContainer) {
        PPItems.ITEMS.register(modEventBus);
        com.moakiee.ae2lt.packaged.recipe.CoreUpgradeRecipes.register(modEventBus);
        com.moakiee.ae2lt.packaged.recipe.RavenRecipes.register(modEventBus);
        com.moakiee.ae2lt.packaged.recipe.FARecipes.register(modEventBus);
        PPDataComponents.COMPONENTS.register(modEventBus);
        PPBlocks.BLOCKS.register(modEventBus);
        PPBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        PPMenuTypes.MENU_TYPES.register(modEventBus);
        PPCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(ResonantCoreSelectPacket::register);
        modEventBus.addListener(com.moakiee.ae2lt.packaged.resource.PackagedResourceStorage::registerKeyType);
        modEventBus.addListener(com.moakiee.ae2lt.packaged.resource.ForbiddenEssenceStorage::registerCapabilities);
        modEventBus.addListener(com.moakiee.ae2lt.packaged.resource.MaidPowerStorage::registerCapabilities);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.moakiee.ae2lt.packaged.resource.PackagedResourceStorage.registerBehaviors();
            if (ModList.get().isLoaded("apothic_enchanting")) {
                MultiblockAdapterRegistry.register(new com.moakiee.ae2lt.packaged.logic.multiblock.apothic.RavenInfusionAdapter());
            }
            if (ModList.get().isLoaded("touhou_little_maid")) {
                MultiblockAdapterRegistry.register(new com.moakiee.ae2lt.packaged.logic.multiblock.maid.MaidAltarAdapter());
            }
            if (ModList.get().isLoaded("forbidden_arcanus")) {
                MultiblockAdapterRegistry.register(new com.moakiee.ae2lt.packaged.logic.multiblock.fa.FAForgeAdapter());
                MultiblockAdapterRegistry.register(new com.moakiee.ae2lt.packaged.logic.multiblock.fa.FAClibanoAdapter());
                MultiblockAdapterRegistry.register(new com.moakiee.ae2lt.packaged.logic.multiblock.fa.FABlackHoleAdapter());
            }
            if (ModList.get().isLoaded("farmersdelight")) {
                MultiblockAdapterRegistry.register(new com.moakiee.ae2lt.packaged.logic.multiblock.delight.CookingPotAdapter());
            }
            MultiblockAdapterRegistry.register(new ActuallyAdditionsAtomicReconstructorAdapter());
            MultiblockAdapterRegistry.register(new ActuallyAdditionsEmpowererAdapter());
            MultiblockAdapterRegistry.register(new ArsNouveauEnchantingApparatusAdapter());
            MultiblockAdapterRegistry.register(new ArsNouveauImbuementChamberAdapter());
            MultiblockAdapterRegistry.register(new ArsRitualBrazierAdapter());
            MultiblockAdapterRegistry.register(new ArsScribesAdapter());
            if (ModList.get().isLoaded("naturesaura")) {
                for (var kind : NaturesAuraAdapter.Kind.values()) {
                    var adapter = new NaturesAuraAdapter(kind);
                    MultiblockAdapterRegistry.register(new AdapterRegistration(adapter.requiredAdapterId(null, null),
                            adapter.priority(), () -> true, adapter));
                }
            }
            MultiblockAdapterRegistry.register(new DraconicFusionCraftingAdapter());
            MultiblockAdapterRegistry.register(new ExtendedCraftingTableAdapter());
            MultiblockAdapterRegistry.register(new ExtendedCraftingEnderCrafterAdapter());
            MultiblockAdapterRegistry.register(new ExtendedCraftingFluxCrafterAdapter());
            MultiblockAdapterRegistry.register(new ExtendedCraftingCombinationAdapter());
            MultiblockAdapterRegistry.register(new AvaritiaTableAdapter());
            MultiblockAdapterRegistry.register(new AvaritiaExtremeSmithingAdapter());
            MultiblockAdapterRegistry.register(new OccultismRitualAdapter());
            MultiblockAdapterRegistry.register(new OccultismSpiritFireAdapter());
            MultiblockAdapterRegistry.register(new InfusionAltarAdapter());
            MultiblockAdapterRegistry.register(new AwakeningAltarAdapter());
            MultiblockAdapterRegistry.register(new MekanismMoreMachinesAdapter());
            MultiblockAdapterRegistry.register(new MalumSpiritFocusingAdapter());
            MultiblockAdapterRegistry.register(new MalumSpiritInfusionAdapter());
            MultiblockAdapterRegistry.register(new PetalApothecaryAdapter());
            MultiblockAdapterRegistry.register(new ManaPoolAdapter());
            MultiblockAdapterRegistry.register(new AlfheimPortalAdapter());
            MultiblockAdapterRegistry.register(new TerraPlateAdapter());
            MultiblockAdapterRegistry.register(new RunicAltarAdapter());
            if (ModList.get().isLoaded("astralsorcery")) {
                MultiblockAdapterRegistry.register(new AstralSorceryAdapter.AstralAltarAdapter());
                MultiblockAdapterRegistry.register(new AstralSorceryAdapter.AstralInfuserAdapter());
            }

            if (ModList.get().isLoaded("chipped")) {
                MultiblockAdapterRegistry.register(new ChippedWorkbenchAdapter());
            }
            if (ModList.get().isLoaded("rechiseled")) {
                MultiblockAdapterRegistry.register(new AdapterRegistration(AdapterIds.RECHISELED, 120,
                        () -> true, new HandheldChiselAdapter(true)));
            }
            if (ModList.get().isLoaded("chisel")) {
                MultiblockAdapterRegistry.register(new AdapterRegistration(AdapterIds.CHISEL, 120,
                        () -> true, new HandheldChiselAdapter(false)));
            }

            var packagedBlock = PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
            var packagedBeType = PPBlockEntities.PACKAGED_PATTERN_PROVIDER.get();
            packagedBlock.setBlockEntity(
                    PackagedPatternProviderBlockEntity.class,
                    packagedBeType,
                    null,
                    StablePatternProviderBlockEntity::serverTick);
            AEBaseBlockEntity.registerBlockEntityItem(packagedBeType, packagedBlock.asItem());

            var wirelessPackagedBlock = PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get();
            var wirelessPackagedBeType = PPBlockEntities.WIRELESS_PACKAGED_PATTERN_PROVIDER.get();
            wirelessPackagedBlock.setBlockEntity(
                    WirelessPackagedPatternProviderBlockEntity.class,
                    wirelessPackagedBeType,
                    null,
                    StablePatternProviderBlockEntity::serverTick);
            AEBaseBlockEntity.registerBlockEntityItem(
                    wirelessPackagedBeType,
                    wirelessPackagedBlock.asItem());

            var extendedBlock = PPBlocks.EXTENDED_PACKAGED_PATTERN_PROVIDER.get();
            var extendedBeType = PPBlockEntities.EXTENDED_PACKAGED_PATTERN_PROVIDER.get();
            extendedBlock.setBlockEntity(
                    ExtendedPackagedPatternProviderBlockEntity.class,
                    extendedBeType,
                    null,
                    StablePatternProviderBlockEntity::serverTick);
            AEBaseBlockEntity.registerBlockEntityItem(extendedBeType, extendedBlock.asItem());

            LOGGER.info("AE2LT Packaged Pattern Provider Reborn initialized");
        });
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        registerGridNodeHost(event, PPBlockEntities.PACKAGED_PATTERN_PROVIDER.get());
        registerGridNodeHost(event, PPBlockEntities.WIRELESS_PACKAGED_PATTERN_PROVIDER.get());
        registerGridNodeHost(event, PPBlockEntities.EXTENDED_PACKAGED_PATTERN_PROVIDER.get());

        event.registerBlock(
                AECapabilities.GENERIC_INTERNAL_INV,
                (level, pos, state, blockEntity, context) -> {
                    if (blockEntity instanceof PackagedPatternProviderBlockEntity be
                            && be.getLogic() instanceof PackagedPatternProviderLogic logic) {
                        return new InsertOnlyPatternProviderReturnInventory(
                                logic.getInternalReturnInv(),
                                logic);
                    }
                    return null;
                },
                PPBlocks.PACKAGED_PATTERN_PROVIDER.get(),
                PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get(),
                PPBlocks.EXTENDED_PACKAGED_PATTERN_PROVIDER.get());
    }

    private static void registerGridNodeHost(
            RegisterCapabilitiesEvent event,
            BlockEntityType<? extends StablePatternProviderBlockEntity> type) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                type,
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);
    }

}
