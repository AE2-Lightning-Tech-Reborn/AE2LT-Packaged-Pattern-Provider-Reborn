package com.moakiee.ae2lt.packaged.network;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.item.ResonantPackagedCoreItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ResonantCoreSelectPacket(boolean next) implements CustomPacketPayload {
    public static final Type<ResonantCoreSelectPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2LTPackagedProvider.MODID, "resonant_core_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResonantCoreSelectPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ResonantCoreSelectPacket::next, ResonantCoreSelectPacket::new);

    @Override public Type<ResonantCoreSelectPacket> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (packet, context) ->
                context.enqueueWork(() -> ResonantPackagedCoreItem.cycle(context.player(), packet.next())));
    }
}
