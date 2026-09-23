package com.moakiee.ae2lt.packaged.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import appeng.client.gui.style.StyleManager;

import com.moakiee.ae2lt.api.client.PatternProviderToolbarButtonHider;
import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;

@EventBusSubscriber(modid = AE2LTPackagedProvider.MODID, value = Dist.CLIENT)
public final class PPClientScreens {
    private PPClientScreens() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        registerHiddenToolbarButtons();
        event.register(PackagedPatternProviderMenu.TYPE, PPClientScreens::createPackagedPatternProviderScreen);
    }

    private static void registerHiddenToolbarButtons() {
        PatternProviderToolbarButtonHider.registerHiddenButtonClassName(
                PatternProviderToolbarButtonHider.EXTENDED_AE_PLUS_SERVER_SETTING_BUTTON);
    }

    @SubscribeEvent
    public static void wrapPackagedCoreItemModels(ModelEvent.ModifyBakingResult event) {
        var models = event.getBakingResult().itemStackModels();
        for (var definition : PackagedCoreDefinition.all()) {
            var key = Identifier.fromNamespaceAndPath(
                    AE2LTPackagedProvider.MODID,
                    definition.itemId());
            var model = models.get(key);
            if (model != null) {
                models.put(key, new PackagedCoreBakedModel(model));
            }
        }
    }

    private static PackagedPatternProviderScreen createPackagedPatternProviderScreen(
            PackagedPatternProviderMenu menu,
            Inventory inv,
            Component title) {
        var style = StyleManager.loadStyleDoc("/screens/packaged_pattern_provider.json");
        return new PackagedPatternProviderScreen(menu, inv, title, style);
    }
}
