package com.zestflow.executor.ai;

/**
 * Executor AI 调用异常。
 */
public class ExecutorAiException extends RuntimeException {

    public ExecutorAiException(String message) {
        super(message);
    }

    public ExecutorAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
