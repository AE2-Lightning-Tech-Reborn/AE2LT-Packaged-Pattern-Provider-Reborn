package com.moakiee.ae2lt.packaged.network;

import io.netty.channel.Channel;

public final class ProtocolReadResumer {
    private ProtocolReadResumer() {}

    public static void afterProtocolChange(Channel channel) {
        // Forge 1.20.1 queues setAutoRead(true). An earlier off-thread disable can
        // queue clearReadPending behind that enable, removing OP_READ while the
        // autoRead flag stays true. A later enable then becomes a no-op in Netty.
        // Queue after Forge's enable and explicitly restore the read subscription.
        channel.eventLoop().execute(() -> {
            if (channel.isActive() && channel.config().isAutoRead()) {
                channel.read();
            }
        });
    }
}
