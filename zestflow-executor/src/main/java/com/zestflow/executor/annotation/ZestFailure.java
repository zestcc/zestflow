package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 链失败上下文注入 — 用于 {@link ZestErrorHandler} 方法，注入 {@link com.zestflow.common.model.dto.ChainExecuteResultDTO}。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestFailure {
}
