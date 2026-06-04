package com.zestflow.executor.http;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.ComponentRef;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.lifecycle.LifecycleExecutor;
import com.zestflow.executor.scanner.ComponentScanner;
import com.zestflow.executor.scanner.ComponentScanner.ComponentMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 链失败 errorHandler 元件调用器。
 */
@Slf4j
@RequiredArgsConstructor
public class ChainErrorHandlerInvoker {

    private static final List<ComponentRef> ERROR_HANDLER_RESOLVERS = List.of(
            new ComponentRef("zestParamResolver", null),
            new ComponentRef("zestFailureResolver", null),
            new ComponentRef("zestResultResolver", null),
            new ComponentRef("parameterNameResolver", null),
            new ComponentRef("contextTypeResolver", null)
    );

    private final ComponentScanner componentScanner;
    private final LifecycleExecutor lifecycleExecutor;

    public Object invoke(String errorHandlerId, ChainExecuteResultDTO failure, Map<String, Object> params,
                         Map<String, String> headers) {
        if (errorHandlerId == null || errorHandlerId.isBlank()) {
            return null;
        }
        ComponentMeta meta = componentScanner.getComponent(errorHandlerId);
        if (meta == null) {
            log.warn("errorHandler 元件未找到 id={}", errorHandlerId);
            return null;
        }
        ChainContext ctx = new ChainContext(
                failure != null ? failure.getInstanceId() : "error-handler",
                failure != null ? failure.getChainCode() : "unknown",
                params != null ? params : Map.of());
        if (headers != null) {
            headers.forEach(ctx::setHeader);
        }
        ctx.setMetadata(ChainConstants.META_CHAIN_FAILURE_RESULT, failure);
        return lifecycleExecutor.invokeMethod(meta.getExecuteMethod(), meta.getTargetBean(), ctx, null,
                ERROR_HANDLER_RESOLVERS, "defaultParamValidator");
    }
}
