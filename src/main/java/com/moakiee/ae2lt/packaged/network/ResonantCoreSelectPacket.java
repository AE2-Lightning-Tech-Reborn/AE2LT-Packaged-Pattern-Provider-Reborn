package com.moakiee.ae2lt.packaged.network;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.item.ResonantPackagedCoreItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

public record ResonantCoreSelectPacket(boolean next) {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AE2LTPackagedProvider.MODID, "resonant_core"), () -> VERSION,
            VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, ResonantCoreSelectPacket.class, ResonantCoreSelectPacket::encode,
                ResonantCoreSelectPacket::decode, ResonantCoreSelectPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void send(boolean next) {
        CHANNEL.sendToServer(new ResonantCoreSelectPacket(next));
    }

    private void encode(FriendlyByteBuf buffer) { buffer.writeBoolean(next); }
    private static ResonantCoreSelectPacket decode(FriendlyByteBuf buffer) {
        return new ResonantCoreSelectPacket(buffer.readBoolean());
    }

    private void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        var sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) ResonantPackagedCoreItem.cycle(sender, next);
        });
        context.setPacketHandled(true);
    }
}
