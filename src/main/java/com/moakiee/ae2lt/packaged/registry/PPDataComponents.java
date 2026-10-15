package com.moakiee.ae2lt.packaged.registry;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.logic.resonant.ResonantLayout;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PPDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AE2LTPackagedProvider.MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResonantLayout>> RESONANT_LAYOUT =
            COMPONENTS.register("resonant_layout", () -> DataComponentType.<ResonantLayout>builder()
                    .persistent(ResonantLayout.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ResonantLayout.CODEC))
                    .build());

    private PPDataComponents() {}
}
