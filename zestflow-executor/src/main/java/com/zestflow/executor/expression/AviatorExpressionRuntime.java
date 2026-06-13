package com.zestflow.executor.expression;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Aviator 表达式运行时（可配置实例）。
 */
@Slf4j
final class AviatorExpressionRuntime {

    private static final String CTX_FN_NAMESPACE = "chainCtx";
    private static final Pattern CTX_PUT_CALL = Pattern.compile("ctx\\.put\\((?!ctx,)");
    private static final Pattern CTX_GET_CALL = Pattern.compile("ctx\\.get\\((?!ctx,)");

    private static final AviatorEvaluatorInstance SHARED_ENGINE = createEngine();
    private static final ExecutorService SCRIPT_EXECUTOR = Executors.newCachedThreadPool(new ScriptThreadFactory());

    private final ExecutorExpressionProperties properties;
    private final Map<String, Expression> expressionCache;

    AviatorExpressionRuntime(ExecutorExpressionProperties properties) {
        this.properties = properties != null ? properties : new ExecutorExpressionProperties();
        this.expressionCache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Expression> eldest) {
                return size() > AviatorExpressionRuntime.this.properties.getMaxCacheSize();
            }
        });
        applyEngineOptions(this.properties);
    }

    static AviatorExpressionRuntime defaults() {
        return new AviatorExpressionRuntime(new ExecutorExpressionProperties());
    }

    Map<String, Object> buildEnv(Map<String, Object> snapshot) {
        Map<String, Object> env = new HashMap<>(snapshot);
        env.put("params", new HashMap<>(snapshot));
        return env;
    }

    Map<String, Object> buildEnv(ChainContext context) {
        Map<String, Object> env = buildEnv(context.snapshot());
        env.put("ctx", context);
        return env;
    }

    String normalizeExpression(String expression) {
        if (expression == null || expression.isEmpty()) {
            return "";
        }
        String expr = expression.trim();
        expr = expr.replaceAll("\\$\\{([^}]*)\\}", "$1");
        if (expr.regionMatches(true, 0, "groovy:", 0, 7)) {
            log.warn("表达式仍使用已废弃的 groovy: 前缀，请改为 aviator: 或纯 Aviator 语法 expr={}", expr);
            expr = expr.substring(7).trim();
        } else if (expr.regionMatches(true, 0, "aviator:", 0, 8)) {
            expr = expr.substring(8).trim();
        }
        return normalizeCtxMethodCalls(expr);
    }

    String normalizeCtxMethodCalls(String expr) {
        if (expr == null || expr.isEmpty()) {
            return expr;
        }
        String normalized = CTX_PUT_CALL.matcher(expr).replaceAll(CTX_FN_NAMESPACE + ".put(ctx, ");
        return CTX_GET_CALL.matcher(normalized).replaceAll(CTX_FN_NAMESPACE + ".get(ctx, ");
    }

    boolean evaluateBoolean(String expression, Map<String, Object> snapshot) {
        String expr = normalizeExpression(expression);
        if (expr.isEmpty()) {
            return true;
        }
        try {
            AviatorExpressionSecurity.validate(expr, properties.getMaxScriptLength());
            Object result = executeWithTimeout(() -> {
                Expression compiled = getOrCompile(expr);
                return compiled.execute(buildEnv(snapshot));
            });
            return toBoolean(result);
        } catch (ExpressionEvaluationException e) {
            log.error("条件表达式评估失败 condition={} reason={}", expression, e.getMessage());
            return properties.isConditionFailOpen();
        } catch (Exception e) {
            log.error("条件表达式评估失败 condition={}", expression, e);
            return properties.isConditionFailOpen();
        }
    }

    Object execute(String script, Map<String, Object> env) {
        String expr = normalizeExpression(script);
        if (expr.isEmpty()) {
            throw new ExpressionEvaluationException("脚本内容为空");
        }
        AviatorExpressionSecurity.validate(expr, properties.getMaxScriptLength());
        try {
            return executeWithTimeout(() -> getOrCompile(expr).execute(env));
        } catch (ExpressionEvaluationException e) {
            throw e;
        } catch (Exception e) {
            throw new ExpressionEvaluationException("脚本执行失败: " + e.getMessage(), e);
        }
    }

    void clearCache() {
        int size = expressionCache.size();
        expressionCache.clear();
        log.info("Aviator 表达式缓存已清理 clearedCount={}", size);
    }

    ExecutorExpressionProperties getProperties() {
        return properties;
    }

    private Object executeWithTimeout(Callable<Object> task) {
        long timeoutMs = properties.getTimeoutMs();
        if (timeoutMs <= 0) {
            try {
                return task.call();
            } catch (Exception e) {
                throw unwrapExecutionFailure(e);
            }
        }
        Future<Object> future = SCRIPT_EXECUTOR.submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ExpressionEvaluationException("表达式执行超时 timeoutMs=" + timeoutMs);
        } catch (ExecutionException e) {
            throw unwrapExecutionFailure(e.getCause() != null ? e.getCause() : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExpressionEvaluationException("表达式执行被中断", e);
        }
    }

    private ExpressionEvaluationException unwrapExecutionFailure(Throwable cause) {
        if (cause instanceof ExpressionEvaluationException ex) {
            return ex;
        }
        return new ExpressionEvaluationException(
                "脚本执行失败: " + (cause != null ? cause.getMessage() : "unknown"), cause);
    }

    private Expression getOrCompile(String expr) {
        Expression cached = expressionCache.get(expr);
        if (cached != null) {
            return cached;
        }
        Expression compiled = SHARED_ENGINE.compile(expr, true);
        expressionCache.put(expr, compiled);
        return compiled;
    }

    private static void applyEngineOptions(ExecutorExpressionProperties props) {
        SHARED_ENGINE.setOption(Options.MAX_LOOP_COUNT, props.getMaxLoopCount());
        SHARED_ENGINE.setOption(Options.TRACE_EVAL, false);
    }

    private static AviatorEvaluatorInstance createEngine() {
        AviatorEvaluatorInstance engine = AviatorEvaluator.newInstance();
        try {
            engine.addStaticFunctions("StringUtils", StringUtils.class);
            engine.addInstanceFunctions(CTX_FN_NAMESPACE, ChainContext.class);
            engine.removeFunction("sys");
            engine.removeFunction("exec");
            engine.removeFunction("load");
            engine.removeFunction("include");
            engine.removeFunction("eval");
            engine.removeFunction("compile");
            engine.removeFunction("require");
            engine.removeFunction("new");
            engine.removeFunction("invoke");
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Aviator 注册函数失败: " + e.getMessage());
        }
        return engine;
    }

    private static boolean toBoolean(Object result) {
        if (result == null) {
            return false;
        }
        if (result instanceof Boolean bool) {
            return bool;
        }
        return Boolean.TRUE.equals(result);
    }

    private static final class ScriptThreadFactory implements ThreadFactory {
        private static final AtomicInteger SEQ = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "zestflow-aviator-" + SEQ.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
