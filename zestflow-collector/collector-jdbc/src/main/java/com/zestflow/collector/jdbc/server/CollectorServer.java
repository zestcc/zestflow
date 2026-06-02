package com.zestflow.collector.jdbc.server;

import com.zestflow.collector.jdbc.metrics.CollectorMetricsProvider;
import com.zestflow.collector.jdbc.service.ChainGraphSnapshotService;
import com.zestflow.collector.spi.EventQueryService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Collector Netty HTTP 服务 — 独立端口，供 Admin 查询事件/轨迹/快照
 * <p>
 * 对标 ExecutorServer，监听 zestflow.collector.registry.port 端口，
 * 通过 CollectorServerHandler 处理 HTTP 请求。
 */
@Slf4j
public class CollectorServer {

    private final int port;
    private final CollectorServerHandler serverHandler;
    private final ExecutorService queryExecutor;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public CollectorServer(int port, EventQueryService eventQueryService,
                           ChainGraphSnapshotService snapshotService,
                           String accessToken,
                           CollectorMetricsProvider metricsProvider) {
        this.port = port;
        this.queryExecutor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "zestflow-collector-query");
                    t.setDaemon(true);
                    return t;
                });
        this.serverHandler = new CollectorServerHandler(eventQueryService, snapshotService,
                accessToken, queryExecutor, metricsProvider);
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1, r -> {
            Thread t = new Thread(r, "zestflow-collector-boss");
            t.setDaemon(true);
            return t;
        });
        workerGroup = new NioEventLoopGroup(
                Runtime.getRuntime().availableProcessors() * 2,
                r -> {
                    Thread t = new Thread(r, "zestflow-collector-worker");
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
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1048576))
                                .addLast(new IdleStateHandler(0, 0, 60))
                                .addLast(serverHandler);
                    }
                });

        channel = bootstrap.bind(port).sync().channel();
        log.info("采集器 Netty 服务启动成功 port={}", port);
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
        if (queryExecutor != null) {
            queryExecutor.shutdown();
            try {
                if (!queryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    queryExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                queryExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("采集器 Netty 服务已关闭");
    }

    public int getPort() {
        return port;
    }
}
