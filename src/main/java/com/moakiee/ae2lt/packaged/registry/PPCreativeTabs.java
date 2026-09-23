package com.moakiee.ae2lt.packaged.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Set;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;

public final class PPCreativeTabs {
    private static final Set<String> PORT_DISABLED_ADAPTER_MODS = Set.of(
            "actuallyadditions", "ars_nouveau", "mekmm");
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AE2LTPackagedProvider.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2ltpp"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> PPBlocks.PACKAGED_PATTERN_PROVIDER.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(PPBlocks.PACKAGED_PATTERN_PROVIDER);
                        output.accept(PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER);
                        output.accept(PPItems.BASIC_PACKAGED_CORE);
                        for (var core : PackagedCoreDefinition.visibleWhen(PPCreativeTabs::isLoaded)) {
                            output.accept(core.runtimeItem());
                        }
                    })
                    .build());

    private PPCreativeTabs() {
    }

    private static boolean isLoaded(String modId) {
        return !PORT_DISABLED_ADAPTER_MODS.contains(modId) && ModList.get().isLoaded(modId);
    }
}
