package com.zestflow.executor.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.exception.ChainExecutionException;
import com.zestflow.executor.http.ChainExecuteFacade;
import com.zestflow.executor.http.ChainHttpResponseWriter;
import com.zestflow.executor.route.ChainRouteEntry;
import com.zestflow.executor.route.ChainRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Netty 进程内转发 Spring MVC 业务接口（同 JVM，不经 Tomcat HTTP）。
 * <p>
 * 优先级：已有 {@code @RequestMapping} Controller &gt; Mode 2 链路由（{@link ChainRouteRegistry}）。
 */
@Slf4j
public class NettyMvcDispatcher {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final List<String> FRAMEWORK_PREFIXES = List.of(
            "/api/chains", "/api/designs", "/api/components", "/api/endpoints", "/api/playground");

    private final RequestMappingHandlerMapping mapping;
    private final RequestMappingHandlerAdapter adapter;
    private final List<String> scanPackages;
    private final ChainRouteRegistry chainRouteRegistry;
    private final ChainExecuteFacade chainExecuteFacade;

    public NettyMvcDispatcher(RequestMappingHandlerMapping mapping,
                                RequestMappingHandlerAdapter adapter,
                                List<String> scanPackages) {
        this(mapping, adapter, scanPackages, null, null);
    }

    public NettyMvcDispatcher(RequestMappingHandlerMapping mapping,
                                RequestMappingHandlerAdapter adapter,
                                List<String> scanPackages,
                                ChainRouteRegistry chainRouteRegistry,
                                ChainExecuteFacade chainExecuteFacade) {
        this.mapping = mapping;
        this.adapter = adapter;
        this.scanPackages = scanPackages != null ? scanPackages : List.of();
        this.chainRouteRegistry = chainRouteRegistry;
        this.chainExecuteFacade = chainExecuteFacade;
    }

    /**
     * @param httpMethod GET/POST/PUT/DELETE
     * @param uri        含可选 query，如 /api/orders/handle?id=1
     * @param body       JSON 请求体，GET 可为 null
     */
    public DispatchResult dispatch(String httpMethod, String uri, String body) throws Exception {
        if (mapping == null || adapter == null) {
            return tryChainRoute(httpMethod, uri, body);
        }
        String pathOnly = stripQuery(uri);
        if (!pathOnly.startsWith("/api/") || isFrameworkPath(pathOnly)) {
            return DispatchResult.notFound();
        }

        MockHttpServletRequest request = buildServletRequest(httpMethod, uri, body);

        HandlerExecutionChain chain = mapping.getHandler(request);
        if (chain != null && chain.getHandler() instanceof HandlerMethod handlerMethod) {
            if (!scanPackages.isEmpty()) {
                String fullName = handlerMethod.getBeanType().getName();
                boolean matched = scanPackages.stream().anyMatch(fullName::startsWith);
                if (!matched) {
                    log.warn("业务 API 不在 scan-packages 白名单内 path={} handler={}", pathOnly, fullName);
                    return DispatchResult.forbidden("业务接口不在允许范围内");
                }
            }

            MockHttpServletResponse response = new MockHttpServletResponse();
            adapter.handle(request, response, handlerMethod);

            int status = response.getStatus();
            String content = response.getContentAsString(StandardCharsets.UTF_8);
            if (content == null) {
                content = "";
            }
            log.info("Netty MVC 转发完成 method={} uri={} status={}", httpMethod, uri, status);
            return new DispatchResult(status, content, true);
        }

        return tryChainRoute(httpMethod, uri, body);
    }

    private DispatchResult tryChainRoute(String httpMethod, String uri, String body) throws Exception {
        if (chainRouteRegistry == null || chainExecuteFacade == null) {
            return DispatchResult.notFound();
        }
        String pathOnly = stripQuery(uri);
        Optional<ChainRouteEntry> route = chainRouteRegistry.lookup(httpMethod, pathOnly);
        if (route.isEmpty()) {
            return DispatchResult.notFound();
        }

        MockHttpServletRequest request = buildServletRequest(httpMethod, uri, body);
        try {
            ResponseEntity<?> entity = chainExecuteFacade.executeHttpRoute(request, route.get().getChainCode());
            return toDispatchResult(entity);
        } catch (ChainExecutionException ex) {
            byte[] bytes = JSON.writeValueAsBytes(ChainHttpResponseWriter.wrappedFailure(ex.getResult()));
            String content = new String(bytes, StandardCharsets.UTF_8);
            log.info("Netty 链路由执行失败 method={} uri={} chainCode={}", httpMethod, uri,
                    route.get().getChainCode());
            return new DispatchResult(500, content, true);
        }
    }

    private static DispatchResult toDispatchResult(ResponseEntity<?> entity) throws Exception {
        int status = entity.getStatusCode().value();
        Object responseBody = entity.getBody();
        if (responseBody == null) {
            return new DispatchResult(status, "", true);
        }
        if (responseBody instanceof byte[] bytes) {
            return new DispatchResult(status, new String(bytes, StandardCharsets.UTF_8), true);
        }
        if (responseBody instanceof String text) {
            return new DispatchResult(status, text, true);
        }
        return new DispatchResult(status, JSON.writeValueAsString(responseBody), true);
    }

    private static MockHttpServletRequest buildServletRequest(String httpMethod, String uri, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, uri);
        if (body != null && !body.isEmpty()) {
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            request.setContentType("application/json;charset=UTF-8");
        }
        return request;
    }

    static boolean isFrameworkPath(String path) {
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    static boolean isDispatchableBusinessPath(String uri) {
        String path = stripQuery(uri);
        return path.startsWith("/api/") && !isFrameworkPath(path);
    }

    private static String stripQuery(String uri) {
        int idx = uri.indexOf('?');
        return idx >= 0 ? uri.substring(0, idx) : uri;
    }

    public record DispatchResult(int httpStatus, String body, boolean handled) {

        public static DispatchResult notFound() {
            return new DispatchResult(404, "", false);
        }

        public static DispatchResult forbidden(String message) {
            return new DispatchResult(403,
                    "{\"code\":403,\"message\":\"" + message.replace("\"", "'") + "\"}", true);
        }
    }
}
