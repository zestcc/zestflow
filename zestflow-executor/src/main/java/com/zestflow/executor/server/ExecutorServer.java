package com.zestflow.executor.server;

import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.executor.event.EventPublisher;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class ExecutorServer {

    private final int port;
    private final EventPublisher eventPublisher;
    private final ServerHandler serverHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public ExecutorServer(int port) {
        this(port, null);
    }

    public ExecutorServer(int port, EventPublisher eventPublisher) {
        this.port = port;
        this.eventPublisher = eventPublisher;
        this.serverHandler = new ServerHandler();
        this.serverHandler.setEventPublisher(eventPublisher);
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1, r -> {
            Thread t = new Thread(r, "zestflow-server-boss");
            t.setDaemon(true);
            return t;
        });
        workerGroup = new NioEventLoopGroup(
                Runtime.getRuntime().availableProcessors() * 2,
                r -> {
                    Thread t = new Thread(r, "zestflow-server-worker");
                    t.setDaemon(true);
                    return t;
                });

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline()
                                .addLast(new HttpRequestDecoder())
                                .addLast(new HttpResponseEncoder())
                                .addLast(new IdleStateHandler(
                                        0, 0, RegistryConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS * 2))
                                .addLast(serverHandler);
                    }
                });

        channel = bootstrap.bind(port).sync().channel();
        log.info("执行器 Netty 服务启动成功 port={}", port);
    }

    public void stop() {
        if (channel != null) {
            try {
                channel.close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
        log.info("执行器 Netty 服务已关闭");
    }

    public int getPort() {
        return port;
    }
}
