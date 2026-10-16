package com.moakiee.ae2lt.packaged.client;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.item.ResonantPackagedCoreItem;
import com.moakiee.ae2lt.packaged.network.ResonantCoreSelectPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.InputEvent;

@EventBusSubscriber(modid = AE2LTPackagedProvider.MODID, value = Dist.CLIENT)
public final class ResonantCoreInput {
    private ResonantCoreInput() {}

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (minecraft.screen != null || player == null || !player.isShiftKeyDown()
                || ResonantPackagedCoreItem.layout(player.getMainHandItem()) == null
                || event.getScrollDelta() == 0) return;
        event.setCanceled(true);
        ResonantCoreSelectPacket.send(event.getScrollDelta() < 0);
    }
}
