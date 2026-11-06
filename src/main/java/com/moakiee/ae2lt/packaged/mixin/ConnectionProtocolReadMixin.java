package com.moakiee.ae2lt.packaged.mixin;

import com.moakiee.ae2lt.packaged.network.ProtocolReadResumer;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionProtocolReadMixin {
    @Shadow
    private Channel channel;

    @Inject(method = "setProtocol", at = @At("TAIL"))
    private void ae2ltpp$resumeProtocolRead(ConnectionProtocol protocol, CallbackInfo ci) {
        ProtocolReadResumer.afterProtocolChange(channel);
    }
}
