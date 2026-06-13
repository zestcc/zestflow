package com.zestflow.executor.expression;

/**
 * Aviator 表达式求值失败（语法错误、超时、安全校验未通过等）。
 */
public class ExpressionEvaluationException extends RuntimeException {

    public ExpressionEvaluationException(String message) {
        super(message);
    }

    public ExpressionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
