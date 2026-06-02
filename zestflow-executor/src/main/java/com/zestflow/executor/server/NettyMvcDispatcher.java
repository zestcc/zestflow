package com.zestflow.executor.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Netty 进程内转发 Spring MVC 业务接口（同 JVM，不经 Tomcat HTTP）。
 * <p>
 * 供试验场 / Admin 代理访问 /api/orders 等演示 API；须配合 {@code scan-packages} 白名单。
 */
@Slf4j
public class NettyMvcDispatcher {

    private static final List<String> FRAMEWORK_PREFIXES = List.of(
            "/api/chains", "/api/designs", "/api/components", "/api/endpoints");

    private final RequestMappingHandlerMapping mapping;
    private final RequestMappingHandlerAdapter adapter;
    private final List<String> scanPackages;

    public NettyMvcDispatcher(RequestMappingHandlerMapping mapping,
                                RequestMappingHandlerAdapter adapter,
                                List<String> scanPackages) {
        this.mapping = mapping;
        this.adapter = adapter;
        this.scanPackages = scanPackages != null ? scanPackages : List.of();
    }

    /**
     * @param httpMethod GET/POST/PUT/DELETE
     * @param uri        含可选 query，如 /api/orders/handle?id=1
     * @param body       JSON 请求体，GET 可为 null
     */
    public DispatchResult dispatch(String httpMethod, String uri, String body) throws Exception {
        if (mapping == null || adapter == null) {
            return DispatchResult.notFound();
        }
        String pathOnly = stripQuery(uri);
        if (!pathOnly.startsWith("/api/") || isFrameworkPath(pathOnly)) {
            return DispatchResult.notFound();
        }

        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, uri);
        if (body != null && !body.isEmpty()) {
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            request.setContentType("application/json;charset=UTF-8");
        }

        HandlerExecutionChain chain = mapping.getHandler(request);
        if (chain == null || !(chain.getHandler() instanceof HandlerMethod handlerMethod)) {
            return DispatchResult.notFound();
        }

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
