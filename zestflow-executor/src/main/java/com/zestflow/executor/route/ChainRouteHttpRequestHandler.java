package com.zestflow.executor.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.exception.ChainExecutionException;
import com.zestflow.common.util.ChainExecutionHttpStatus;
import com.zestflow.executor.http.ChainExecuteFacade;
import com.zestflow.executor.http.ChainHttpResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Mode 2 链路由请求处理器 — 委托 {@link ChainExecuteFacade} 并写回 HTTP 响应。
 */
@RequiredArgsConstructor
public class ChainRouteHttpRequestHandler implements HttpRequestHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChainExecuteFacade facade;
    private final String chainCode;

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            ResponseEntity<?> entity = facade.executeHttpRoute(request, chainCode);
            writeResponse(response, entity);
        } catch (ChainExecutionException ex) {
            writeFailureResponse(response, ex);
        }
    }

    static void writeFailureResponse(HttpServletResponse response, ChainExecutionException ex) throws IOException {
        int status = ChainExecutionHttpStatus.resolve(
                ex.getResult() != null ? ex.getResult().getErrorCode() : null);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(JSON.writeValueAsBytes(ChainHttpResponseWriter.wrappedFailure(ex.getResult())));
    }

    static void writeResponse(HttpServletResponse response, ResponseEntity<?> entity) throws IOException {
        response.setStatus(entity.getStatusCode().value());
        entity.getHeaders().forEach((name, values) ->
                values.forEach(value -> response.addHeader(name, value)));
        Object body = entity.getBody();
        if (body == null) {
            return;
        }
        if (body instanceof byte[] bytes) {
            response.getOutputStream().write(bytes);
            return;
        }
        if (body instanceof String text) {
            if (response.getContentType() == null) {
                response.setContentType("text/plain;charset=UTF-8");
            }
            response.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (response.getContentType() == null) {
            response.setContentType("application/json;charset=UTF-8");
        }
        response.getOutputStream().write(JSON.writeValueAsBytes(body));
    }
}
