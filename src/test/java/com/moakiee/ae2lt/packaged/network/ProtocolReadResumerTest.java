package com.moakiee.ae2lt.packaged.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProtocolReadResumerTest {
    private static final class ProbeChannel extends NioSocketChannel {
        boolean readsEnabled() {
            return (selectionKey().interestOps() & SelectionKey.OP_READ) != 0;
        }
    }

    @Test
    void restoresReadSubscriptionAfterQueuedDisableOvertakesForgeEnable() throws Exception {
        var group = new NioEventLoopGroup(1);
        var received = new CompletableFuture<Integer>();
        var release = new CountDownLatch(1);
        try (var listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var channel = (ProbeChannel) new Bootstrap().group(group).channelFactory(ProbeChannel::new)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object message) {
                            var buffer = (ByteBuf) message;
                            try {
                                received.complete((int) buffer.readUnsignedByte());
                            } finally {
                                buffer.release();
                            }
                        }
                    }).connect(listener.getLocalSocketAddress()).sync().channel();
            try (var peer = listener.accept()) {
                var paused = new CountDownLatch(1);
                channel.eventLoop().execute(() -> {
                    paused.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                assertTrue(paused.await(5, TimeUnit.SECONDS));

                // Reproduce Forge's deferred initial enable, the connector thread's
                // disable, and the next protocol's enable, in that execution order.
                channel.eventLoop().execute(() -> channel.config().setAutoRead(true));
                channel.config().setAutoRead(false);
                channel.eventLoop().execute(() -> channel.config().setAutoRead(true));
                release.countDown();
                assertTrue(channel.eventLoop().submit(() -> channel.config().isAutoRead()).get(5, TimeUnit.SECONDS));
                assertFalse(channel.eventLoop().submit(channel::readsEnabled).get(5, TimeUnit.SECONDS),
                        "the queued clear removes OP_READ while autoRead remains true");

                peer.getOutputStream().write(42);
                peer.getOutputStream().flush();
                ProtocolReadResumer.afterProtocolChange(channel);
                assertEquals(42, received.get(5, TimeUnit.SECONDS));
                assertTrue(channel.eventLoop().submit(channel::readsEnabled).get(5, TimeUnit.SECONDS));

                // Preserve intentional read pauses instead of overriding flow control.
                channel.eventLoop().submit(() -> channel.config().setAutoRead(false)).get(5, TimeUnit.SECONDS);
                ProtocolReadResumer.afterProtocolChange(channel);
                assertFalse(channel.eventLoop().submit(() -> channel.config().isAutoRead()).get(5, TimeUnit.SECONDS));
                assertFalse(channel.eventLoop().submit(channel::readsEnabled).get(5, TimeUnit.SECONDS));
            } finally {
                channel.close().sync();
            }
        } finally {
            release.countDown();
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }
}
