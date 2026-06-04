package com.zestflow.common.exception;

import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import lombok.Getter;

import java.io.Serial;

/**
 * 链执行失败异常 — HTTP 边界（Mode 1/2/3）将引擎结构化失败还原为 Spring 可捕获异常，
 * 供 {@code @ControllerAdvice} 处理，避免错误被 {@code success:false} 吞没。
 */
@Getter
public class ChainExecutionException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String chainCode;
    private final String executionId;
    private final String failedNodeId;
    private final String errorCode;
    private final ChainExecuteResultDTO result;

    public ChainExecutionException(ChainExecuteResultDTO result) {
        super(resolveMessage(result));
        this.result = result;
        this.chainCode = result != null ? result.getChainCode() : null;
        this.executionId = result != null ? result.getInstanceId() : null;
        this.failedNodeId = result != null ? result.getFailedNodeId() : null;
        this.errorCode = result != null ? result.getErrorCode() : null;
    }

    public ChainExecutionException(ChainExecuteResultDTO result, Throwable cause) {
        super(500, resolveMessage(result));
        if (cause != null) {
            initCause(cause);
        }
        this.result = result;
        this.chainCode = result != null ? result.getChainCode() : null;
        this.executionId = result != null ? result.getInstanceId() : null;
        this.failedNodeId = result != null ? result.getFailedNodeId() : null;
        this.errorCode = result != null ? result.getErrorCode() : null;
    }

    private static String resolveMessage(ChainExecuteResultDTO result) {
        if (result == null) {
            return "链执行失败";
        }
        if (result.getErrorMessage() != null && !result.getErrorMessage().isBlank()) {
            return result.getErrorMessage();
        }
        return "链执行失败 chainCode=" + result.getChainCode();
    }
}
