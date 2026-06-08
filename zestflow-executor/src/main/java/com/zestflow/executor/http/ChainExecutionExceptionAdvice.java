package com.zestflow.executor.http;

import com.zestflow.common.exception.ChainExecutionException;
import com.zestflow.common.util.ChainExecutionHttpStatus;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 链执行失败兜底 Advice — PROPAGATE 策略下将 {@link ChainExecutionException} 转为 HTTP 响应。
 * <p>
 * 业务方可定义更高优先级 {@code @Order} 的 Advice 覆盖或增强。
 */
@RestControllerAdvice
@ConditionalOnExpression("${zestflow.executor.execute-endpoint-enabled:false} || ${zestflow.executor.chain-route-enabled:false}")
public class ChainExecutionExceptionAdvice {

    @ExceptionHandler(ChainExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleChainExecution(ChainExecutionException ex) {
        ChainExecuteResultDTO result = ex.getResult();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        if (result != null) {
            body.put("chainCode", result.getChainCode());
            body.put("instanceId", result.getInstanceId());
            body.put("failedNodeId", result.getFailedNodeId());
            body.put("errorCode", result.getErrorCode());
            body.put("errorMessage", result.getErrorMessage());
            body.put("status", result.getStatus());
        } else {
            body.put("errorMessage", ex.getMessage());
        }
        return ResponseEntity.status(ChainExecutionHttpStatus.resolve(
                result != null ? result.getErrorCode() : null)).body(body);
    }
}
