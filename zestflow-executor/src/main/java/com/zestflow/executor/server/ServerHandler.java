package com.zestflow.executor.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.executor.engine.ChainExecutionEngine;
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

import java.util.Map;

@Slf4j
@Setter
@ChannelHandler.Sharable
public class ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChainExecutionEngine chainExecutionEngine;

    /** 事件发布器（可选，未配置 Collector 时为 null） */
    private EventPublisher eventPublisher;

    public ServerHandler(ChainExecutionEngine chainExecutionEngine) {
        this.chainExecutionEngine = chainExecutionEngine;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
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
        writeResponse(ctx, HttpResponseStatus.OK,
                "{\"status\":\"UP\",\"timestamp\":" + System.currentTimeMillis() + "}");
    }

    @SuppressWarnings("unchecked")
    private void handleExecute(ChannelHandlerContext ctx, String body) {
        try {
            log.info("收到执行请求 body={}", body);

            // 解析请求
            ChainExecuteRequestDTO request = MAPPER.readValue(body, ChainExecuteRequestDTO.class);

            // 发布链启动事件
            publishEvent(ChainEvent.EventType.CHAIN_STARTED, request.getChainCode(), body, null);

            // 执行链
            ChainExecuteResultDTO result = chainExecutionEngine.execute(
                    request.getChainCode(), request.getParams());

            // 发布完成事件
            ChainEvent.EventType resultType = result.getStatus() != null && result.getStatus() == 3
                    ? ChainEvent.EventType.CHAIN_COMPLETED
                    : ChainEvent.EventType.CHAIN_FAILED;
            publishEvent(resultType, request.getChainCode(), null, result.getErrorMessage());

            // 返回结果
            String json = MAPPER.writeValueAsString(result);
            writeResponse(ctx, HttpResponseStatus.OK, json);

        } catch (Exception e) {
            log.error("执行请求处理失败", e);
            writeResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    "{\"code\":500,\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private void publishEvent(ChainEvent.EventType eventType, String chainCode,
                               String params, String errorMessage) {
        if (eventPublisher != null) {
            eventPublisher.publish(ChainEvent.builder()
                    .eventId(java.util.UUID.randomUUID().toString())
                    .eventType(eventType)
                    .chainId(chainCode)
                    .timestamp(System.currentTimeMillis())
                    .params(params)
                    .errorMessage(errorMessage)
                    .build());
        }
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
