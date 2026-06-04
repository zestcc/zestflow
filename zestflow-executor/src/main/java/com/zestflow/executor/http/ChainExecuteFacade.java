package com.zestflow.executor.http;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.protocol.ChainHttpResponseMode;
import com.zestflow.common.protocol.ChainHttpRouteConfig;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.engine.ChainExecutionEngine;
import com.zestflow.executor.engine.ExecutionIdempotencyGuard;
import com.zestflow.executor.registry.ExecutorProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP 链执行门面 — Mode 1/2 统一入口：幂等 → 引擎 → 响应包装 / 失败策略。
 */
@Slf4j
@RequiredArgsConstructor
public class ChainExecuteFacade {

    private final ChainExecutionEngine executionEngine;
    private final ChainManager chainManager;
    private final ExecutionIdempotencyGuard idempotencyGuard;
    private final ExecutorProperties executorProperties;
    private final ChainErrorHandlerInvoker errorHandlerInvoker;

    public ResponseEntity<?> executeHttp(ChainExecuteRequestDTO request) {
        ChainExecuteResultDTO result = executeCore(request);
        ChainDefinition definition = chainManager.get(result.getChainCode());
        ChainHttpRouteConfig routeConfig = definition != null
                ? ChainHttpRouteConfig.fromExtraConfig(definition.getExtraConfig()) : null;
        ChainHttpResponseMode mode = resolveResponseMode(request);
        ChainHttpResponseWriter.ChainExecuteRequestContext ctx = new ChainHttpResponseWriter.ChainExecuteRequestContext(
                request.getParams(), request.getHeaders());

        if (result.isSuccess()) {
            return ChainHttpResponseWriter.success(result, routeConfig, mode);
        }
        log.warn("链执行失败 chainCode={} error={}", result.getChainCode(), result.getErrorMessage());
        return ChainHttpResponseWriter.handleFailure(result, routeConfig,
                executorProperties.getExecuteFailurePolicy(), errorHandlerInvoker, ctx);
    }

    public ResponseEntity<?> executeHttpRoute(HttpServletRequest request, String chainCode) throws IOException {
        ChainExecuteRequestDTO dto = HttpChainRequestAdapter.fromServlet(request, chainCode);
        return executeHttp(dto);
    }

    public ChainExecuteResultDTO executeCore(ChainExecuteRequestDTO request) {
        return executeCore(request, new Object[0]);
    }

    public ChainExecuteResultDTO executeCore(ChainExecuteRequestDTO request, Object... typedArgs) {
        if (request == null || request.getChainCode() == null || request.getChainCode().isBlank()) {
            return ChainExecuteResultDTO.builder()
                    .status(ChainConstants.CHAIN_FAILED)
                    .errorMessage("chainCode 不能为空")
                    .build();
        }
        String chainCode = request.getChainCode().trim();
        if (!executorProperties.isIdempotencyEnabled()) {
            return runEngine(chainCode, request, typedArgs);
        }
        return idempotencyGuard.execute(
                request.resolveIdempotencyKey(),
                executorProperties.getIdempotencyTtlMs(),
                executorProperties.getIdempotencyWaitMs(),
                () -> runEngine(chainCode, request, typedArgs));
    }

    private ChainExecuteResultDTO runEngine(String chainCode, ChainExecuteRequestDTO request, Object... typedArgs) {
        Map<String, Object> params = request.getParams();
        Map<String, String> headers = request.getHeaders();
        if (typedArgs == null || typedArgs.length == 0) {
            return executionEngine.execute(chainCode, params, headers);
        }
        return executionEngine.execute(chainCode, params, headers, typedArgs);
    }

    private ChainHttpResponseMode resolveResponseMode(ChainExecuteRequestDTO request) {
        Map<String, String> headers = request.getHeaders();
        if (headers != null) {
            String headerMode = headers.get("X-Response-Mode");
            if (headerMode != null && "DETAIL".equalsIgnoreCase(headerMode.trim())) {
                return ChainHttpResponseMode.DETAIL;
            }
        }
        Map<String, Object> params = request.getParams();
        if (params != null) {
            Object detail = params.get("_responseMode");
            if ("DETAIL".equalsIgnoreCase(String.valueOf(detail))) {
                return ChainHttpResponseMode.DETAIL;
            }
        }
        return executorProperties.getExecuteResponseMode();
    }
}
