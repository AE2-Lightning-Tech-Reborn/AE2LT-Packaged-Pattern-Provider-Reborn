package com.moakiee.ae2lt.packaged.client;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.item.ResonantPackagedCoreItem;
import com.moakiee.ae2lt.packaged.network.ResonantCoreSelectPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AE2LTPackagedProvider.MODID, value = Dist.CLIENT)
public final class ResonantCoreInput {
    private ResonantCoreInput() {}

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (minecraft.screen != null || player == null || !player.isShiftKeyDown()
                || ResonantPackagedCoreItem.layout(player.getMainHandItem()) == null
                || event.getScrollDeltaY() == 0) return;
        event.setCanceled(true);
        PacketDistributor.sendToServer(new ResonantCoreSelectPacket(event.getScrollDeltaY() < 0));
    }
}
