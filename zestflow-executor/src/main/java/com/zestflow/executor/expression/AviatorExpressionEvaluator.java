package com.zestflow.executor.expression;

import com.zestflow.executor.context.ChainContext;

import java.util.Map;

/**
 * Aviator 表达式求值防腐层（替代 Groovy JSR-223）。
 * <p>
 * 用于：判断元件内联脚本、边条件、SCRIPT 节点表达式、While 循环条件。
 * <p>
 * 安全加固：
 * <ul>
 *   <li>表达式编译 LRU 缓存</li>
 *   <li>独立线程池 + {@link java.util.concurrent.Future#get(long, TimeUnit)} 执行超时</li>
 *   <li>Aviator {@link com.googlecode.aviator.Options#MAX_LOOP_COUNT} 循环上限</li>
 *   <li>禁用危险内置函数 + 静态模式黑名单</li>
 *   <li>脚本最大长度限制（可配置 {@code zestflow.executor.expression.max-script-length}）</li>
 * </ul>
 * <p>
 * Spring 环境下由 {@link AviatorExpressionConfigurer} 注入 {@link ExecutorExpressionProperties}；
 * 单元测试可调用 {@link #configure(ExecutorExpressionProperties)}。
 */
public final class AviatorExpressionEvaluator {

    private static volatile AviatorExpressionRuntime runtime = AviatorExpressionRuntime.defaults();

    private AviatorExpressionEvaluator() {
    }

    /**
     * 应用配置（Spring 启动或测试用）。
     */
    public static void configure(ExecutorExpressionProperties properties) {
        runtime = new AviatorExpressionRuntime(properties);
    }

    /**
     * 重置为默认配置（测试 teardown）。
     */
    public static void resetToDefaults() {
        runtime = AviatorExpressionRuntime.defaults();
    }

    public static Map<String, Object> buildEnv(Map<String, Object> snapshot) {
        return runtime.buildEnv(snapshot);
    }

    public static Map<String, Object> buildEnv(ChainContext context) {
        return runtime.buildEnv(context);
    }

    public static String normalizeExpression(String expression) {
        return runtime.normalizeExpression(expression);
    }

    static String normalizeCtxMethodCalls(String expr) {
        return runtime.normalizeCtxMethodCalls(expr);
    }

    /**
     * 求值布尔表达式；异常或空表达式按 fail-closed（默认 false）处理。
     */
    public static boolean evaluateBoolean(String expression, Map<String, Object> snapshot) {
        return runtime.evaluateBoolean(expression, snapshot);
    }

    /**
     * 执行表达式并返回结果（SCRIPT 节点等）。
     */
    public static Object execute(String script, Map<String, Object> env) {
        return runtime.execute(script, env);
    }

    /**
     * 清理表达式编译缓存（链热加载时可调用）。
     */
    public static void clearCache() {
        runtime.clearCache();
    }

    /**
     * 当前是否配置了链热加载清缓存（供 ChainLoader 读取）。
     */
    public static boolean isClearCacheOnChainReloadEnabled() {
        return runtime.getProperties().isClearCacheOnChainReload();
    }
}
