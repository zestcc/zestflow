package com.zestflow.executor.http;

import com.zestflow.common.constant.ChainExecutionErrorCodes;
import com.zestflow.common.exception.ChainExecutionException;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.protocol.ChainFailurePolicy;
import com.zestflow.common.protocol.ChainHttpResponseMode;
import com.zestflow.common.protocol.ChainHttpRouteConfig;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 {@link ChainExecuteResultDTO} 转为 Spring MVC {@link ResponseEntity}，支持 BODY / DETAIL 与失败策略。
 */
public final class ChainHttpResponseWriter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChainHttpResponseWriter.class);

    private ChainHttpResponseWriter() {
    }

    public static ResponseEntity<?> success(ChainExecuteResultDTO result, ChainHttpRouteConfig routeConfig,
                                            ChainHttpResponseMode responseMode) {
        if (responseMode == ChainHttpResponseMode.DETAIL) {
            return ResponseEntity.ok(result);
        }
        return bodyResponse(result.getFinalReturnValue(), routeConfig != null ? routeConfig.getProduces() : null);
    }

    public static ResponseEntity<?> handleFailure(ChainExecuteResultDTO result, ChainHttpRouteConfig routeConfig,
                                                  ChainFailurePolicy policy, ChainErrorHandlerInvoker errorHandlerInvoker,
                                                  ChainExecuteRequestContext requestContext) {
        if (ChainExecutionErrorCodes.isInfrastructureError(result.getErrorCode())) {
            throw new ChainExecutionException(result);
        }
        ChainFailurePolicy effective = resolveFailurePolicy(policy, routeConfig);
        return switch (effective) {
            case PROPAGATE -> throw new ChainExecutionException(result);
            case ERROR_HANDLER -> invokeErrorHandler(result, routeConfig, errorHandlerInvoker, requestContext);
            case WRAPPED -> ResponseEntity.ok(wrappedFailure(result));
        };
    }

    private static ChainFailurePolicy resolveFailurePolicy(ChainFailurePolicy global,
                                                           ChainHttpRouteConfig routeConfig) {
        if (routeConfig != null && routeConfig.getFailurePolicy() != null) {
            return routeConfig.getFailurePolicy();
        }
        return global != null ? global : ChainFailurePolicy.PROPAGATE;
    }

    private static ResponseEntity<?> invokeErrorHandler(ChainExecuteResultDTO result, ChainHttpRouteConfig routeConfig,
                                                        ChainErrorHandlerInvoker invoker,
                                                        ChainExecuteRequestContext ctx) {
        String handlerId = routeConfig != null ? routeConfig.getErrorHandler() : null;
        Object body = invoker.invoke(handlerId, result,
                ctx != null ? ctx.params() : null,
                ctx != null ? ctx.headers() : null);
        if (body == null) {
            log.warn("errorHandler 未配置或返回空 handlerId={}，降级为 WRAPPED", handlerId);
            return ResponseEntity.ok(wrappedFailure(result));
        }
        if (body instanceof ResponseEntity<?> re) {
            return re;
        }
        return bodyResponse(body, routeConfig != null ? routeConfig.getProduces() : null);
    }

    public static ResponseEntity<?> bodyResponse(Object body, String produces) {
        if (body instanceof ResponseEntity<?> re) {
            return re;
        }
        if (produces != null && !produces.isBlank()) {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(produces)).body(body);
        }
        if (body instanceof String || body == null) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.ok(body);
    }

    public static Map<String, Object> wrappedFailure(ChainExecuteResultDTO result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", false);
        resp.put("instanceId", result.getInstanceId());
        resp.put("chainCode", result.getChainCode());
        resp.put("status", result.getStatus());
        resp.put("costMs", result.getCostMs());
        resp.put("failedNodeId", result.getFailedNodeId());
        resp.put("errorCode", result.getErrorCode());
        resp.put("errorMessage", result.getErrorMessage());
        return resp;
    }

    /**
     * 请求上下文快照 — errorHandler 需要原始 params/headers。
     */
    public record ChainExecuteRequestContext(Map<String, Object> params, Map<String, String> headers) {
    }
}
