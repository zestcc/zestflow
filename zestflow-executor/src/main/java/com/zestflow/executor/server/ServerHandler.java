package com.zestflow.executor.server;

import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.executor.event.EventPublisher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Setter
@ChannelHandler.Sharable
public class ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    /** 事件发布器（可选，未配置 Collector 时为 null） */
    private EventPublisher eventPublisher;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        // 只允许 POST 和 GET
        if (request.method() != HttpMethod.POST && request.method() != HttpMethod.GET) {
            writeResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "Method not allowed");
            return;
        }

        String uri = request.uri();
        String content = request.content().toString(CharsetUtil.UTF_8);
        log.debug("收到请求 method={} uri={}", request.method(), uri);

        if ("/health".equals(uri)) {
            handleHealth(ctx);
        } else if ("/execute".equals(uri) && request.method() == HttpMethod.POST) {
            handleExecute(ctx, content);
        } else {
            writeResponse(ctx, HttpResponseStatus.NOT_FOUND, "Not found");
        }
    }

    private void handleHealth(ChannelHandlerContext ctx) {
        writeResponse(ctx, HttpResponseStatus.OK, "{\"status\":\"UP\",\"timestamp\":" + System.currentTimeMillis() + "}");
    }

    private void handleExecute(ChannelHandlerContext ctx, String body) {
        log.info("收到执行请求 body={}", body);
        // 发射 CHAIN_STARTED 事件
        if (eventPublisher != null) {
            eventPublisher.publish(ChainEvent.builder()
                    .eventId(java.util.UUID.randomUUID().toString())
                    .eventType(ChainEvent.EventType.CHAIN_STARTED)
                    .timestamp(System.currentTimeMillis())
                    .params(body)
                    .build());
        }
        writeResponse(ctx, HttpResponseStatus.OK, "{\"code\":200,\"message\":\"received\"}");
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
        ByteBuf buf = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, buf);
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
                .set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Netty 处理异常", cause);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            log.debug("连接空闲，关闭 channel");
            ctx.close();
        }
    }
}
