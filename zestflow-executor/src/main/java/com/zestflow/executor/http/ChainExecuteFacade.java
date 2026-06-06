package com.zestflow.executor.http;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.protocol.ChainHttpResponseMode;
import com.zestflow.common.protocol.ChainHttpRouteConfig;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainKeyResolver;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.engine.ChainExecutionEngine;
import com.zestflow.executor.engine.ExecutionIdempotencyGuard;
import com.zestflow.executor.registry.ExecutorProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP 链执行门面 — Mode 1/2 统一入口：幂等 → 引擎 → 响应包装 / 失败策略。
 */
@Slf4j
public class ChainExecuteFacade {

    private final ChainExecutionEngine executionEngine;
    private final ChainManager chainManager;
    private final ExecutionIdempotencyGuard idempotencyGuard;
    private final ExecutorProperties executorProperties;
    private final ChainErrorHandlerInvoker errorHandlerInvoker;
    private final ChainKeyResolver chainKeyResolver;

    public ChainExecuteFacade(ChainExecutionEngine executionEngine,
                              ChainManager chainManager,
                              ExecutionIdempotencyGuard idempotencyGuard,
                              ExecutorProperties executorProperties,
                              ChainErrorHandlerInvoker errorHandlerInvoker,
                              ChainKeyResolver chainKeyResolver) {
        this.executionEngine = executionEngine;
        this.chainManager = chainManager;
        this.idempotencyGuard = idempotencyGuard;
        this.executorProperties = executorProperties;
        this.errorHandlerInvoker = errorHandlerInvoker;
        this.chainKeyResolver = chainKeyResolver;
    }

    public ResponseEntity<?> executeHttp(ChainExecuteRequestDTO request) {
        ChainExecuteResultDTO result = executeCore(request);
        ChainDefinition definition = result.getChainCode() != null
                ? chainManager.get(result.getChainCode()) : null;
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
        ChainExecuteResultDTO resolved = resolveRequest(request);
        if (resolved != null) {
            return resolved;
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

    /**
     * 解析 chainKey / chainCode，失败时返回 infrastructure 结果。
     */
    private ChainExecuteResultDTO resolveRequest(ChainExecuteRequestDTO request) {
        if (request == null) {
            return ChainExecuteResultDTO.builder()
                    .status(ChainConstants.CHAIN_FAILED)
                    .errorMessage("请求不能为空")
                    .costMs(0L)
                    .build();
        }
        if (StringUtils.hasText(request.getChainKey())) {
            ChainKeyResolver.ResolvedChainKey resolved = chainKeyResolver.resolveKey(request.getChainKey().trim());
            if (!resolved.isOk()) {
                return resolved.failure();
            }
            request.setChainCode(resolved.chainCode());
            return null;
        }
        if (!StringUtils.hasText(request.getChainCode())) {
            return ChainExecuteResultDTO.builder()
                    .status(ChainConstants.CHAIN_FAILED)
                    .errorMessage("chainCode 或 chainKey 不能为空")
                    .costMs(0L)
                    .build();
        }
        request.setChainCode(request.getChainCode().trim());
        return null;
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
