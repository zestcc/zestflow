package com.zestflow.admin.config;

import com.zestflow.admin.service.log.ExecutionLiveStreamService;
import com.zestflow.common.constant.AdminApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 日志执行轨迹 WebSocket — 与 SSE 同数据源（Collector 轮询），可选替代传输层。
 */
@Configuration
@EnableWebSocket
@ConditionalOnProperty(prefix = "zestflow.admin.log-live-stream", name = "websocket-enabled", havingValue = "true")
@RequiredArgsConstructor
public class LogLiveStreamWebSocketConfig implements WebSocketConfigurer {

    private final ExecutionLiveStreamService executionLiveStreamService;
    private final LogLiveStreamWebSocketHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new LogLiveStreamWebSocketHandler(executionLiveStreamService),
                        AdminApiPaths.of("/logs/executions/*/ws"))
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    @RequiredArgsConstructor
    static class LogLiveStreamWebSocketHandler extends TextWebSocketHandler {

        private final ExecutionLiveStreamService executionLiveStreamService;

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            Map<String, Object> attrs = session.getAttributes();
            String executionId = (String) attrs.get("executionId");
            String appCode = (String) attrs.get("appCode");
            executionLiveStreamService.streamOverWebSocket(session, executionId, appCode);
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
}
