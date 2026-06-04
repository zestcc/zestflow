package com.zestflow.executor.route;

import com.zestflow.executor.http.ChainExecuteFacade;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;

import java.util.Optional;

/**
 * Mode 2 动态路由 — 低优先级 HandlerMapping，不覆盖已有 Spring Controller。
 */
@RequiredArgsConstructor
public class ChainRouteHandlerMapping extends AbstractHandlerMapping {

    private final ChainRouteRegistry registry;
    private final ChainExecuteFacade facade;

    {
        setOrder(LOWEST_PRECEDENCE - 50);
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        Optional<ChainRouteEntry> entry = registry.lookup(request.getMethod(), path);
        if (entry.isEmpty()) {
            return null;
        }
        ChainRouteEntry route = entry.get();
        ChainRouteHttpRequestHandler handler = new ChainRouteHttpRequestHandler(facade, route.getChainCode());
        return new HandlerExecutionChain(handler);
    }
}
