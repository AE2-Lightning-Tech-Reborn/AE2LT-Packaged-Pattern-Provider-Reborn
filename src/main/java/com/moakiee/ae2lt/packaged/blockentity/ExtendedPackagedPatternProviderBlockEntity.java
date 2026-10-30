package com.moakiee.ae2lt.packaged.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.moakiee.ae2lt.packaged.registry.PPBlockEntities;

public class ExtendedPackagedPatternProviderBlockEntity extends PackagedPatternProviderBlockEntity {
    public static final int PATTERN_SLOTS = 144;

    public ExtendedPackagedPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(PPBlockEntities.EXTENDED_PACKAGED_PATTERN_PROVIDER.get(), pos, blockState, ProviderMode.NORMAL);
    }

    @Override
    public int getPatternSlotCount() {
        return PATTERN_SLOTS;
    }

    @Override
    public boolean isProviderModeSwitchable() {
        return true;
    }

    @Override
    public String ae2lt$titleTranslationKey() {
        return "ae2ltpp.gui.title.extended_packaged_pattern_provider";
    }
}
