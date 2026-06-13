package com.zestflow.admin.service.log;

import com.zestflow.admin.config.JwtUnauthorizedEntryPoint;
import com.zestflow.admin.util.JwtUtils;
import com.zestflow.common.constant.AdminApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手 JWT 校验 — 支持 query {@code access_token} 或 Authorization 头。
 */
@Component
@RequiredArgsConstructor
public class LogLiveStreamWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token) || !jwtUtils.validateToken(token)) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put("executionId", extractExecutionId(request.getURI().getPath()));
        attributes.put("appCode", UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("appCode"));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private String resolveToken(ServerHttpRequest request) {
        String queryToken = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("access_token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        String auth = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private String extractExecutionId(String path) {
        String prefix = AdminApiPaths.of("/logs/executions/");
        String suffix = "/ws";
        if (path == null || !path.startsWith(prefix) || !path.endsWith(suffix)) {
            return null;
        }
        return path.substring(prefix.length(), path.length() - suffix.length());
    }
}
