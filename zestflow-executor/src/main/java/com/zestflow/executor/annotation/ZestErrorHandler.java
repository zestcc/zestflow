package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 链 HTTP 失败处理元件 — 在 Mode 1/2 链失败且策略为 ERROR_HANDLER 时调用，生成对外错误响应体。
 * <pre>{@code
 * @ZestComponent("ota")
 * public class OtaErrorHandler {
 *     @ZestErrorHandler("otaErrorHandler")
 *     public String handle(@ZestFailure ChainExecuteResultDTO failure, ChainContext ctx) {
 *         return "<Error>" + failure.getErrorMessage() + "</Error>";
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestErrorHandler {

    String value() default "";

    String name() default "";

    String description() default "";
}
