package com.moakiee.ae2lt.packaged.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.moakiee.ae2lt.packaged.registry.PPBlockEntities;

public class WirelessPackagedPatternProviderBlockEntity extends PackagedPatternProviderBlockEntity {

    public WirelessPackagedPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(PPBlockEntities.WIRELESS_PACKAGED_PATTERN_PROVIDER.get(), pos, blockState, ProviderMode.WIRELESS);
    }

    @Override
    public String ae2lt$titleTranslationKey() {
        return "ae2ltpp.gui.title.wireless_packaged_pattern_provider";
    }
}
