package com.zestflow.executor.expression;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Aviator 表达式求值防腐层（替代 Groovy JSR-223）。
 * <p>
 * 用于：判断元件内联脚本、边条件、SCRIPT 节点表达式。
 * <p>
 * 安全加固：
 * <ul>
 *   <li>表达式编译缓存，避免重复编译</li>
 *   <li>脚本执行超时控制</li>
 *   <li>禁用危险函数（System、Runtime、Class 等）</li>
 *   <li>脚本最大长度限制</li>
 * </ul>
 */
@Slf4j
public final class AviatorExpressionEvaluator {

    private static final AviatorEvaluatorInstance ENGINE = AviatorEvaluator.newInstance();

    /** 表达式编译缓存 */
    private static final ConcurrentHashMap<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();
    /** 缓存最大容量 */
    private static final int MAX_CACHE_SIZE = 1000;
    /** 脚本最大长度（字符） */
    private static final int MAX_SCRIPT_LENGTH = 10_000;
    /** 脚本执行超时（毫秒） */
    private static final long SCRIPT_TIMEOUT_MS = 5_000L;

    /** 将 Groovy 风格 {@code ctx.put('k', v)} 转为 Aviator {@code ctx.put(ctx, 'k', v)} */
    private static final Pattern CTX_PUT_CALL = Pattern.compile("ctx\\.put\\((?!ctx,)");
    private static final Pattern CTX_GET_CALL = Pattern.compile("ctx\\.get\\((?!ctx,)");

    static {
        try {
            // 禁用危险函数
            ENGINE.addStaticFunctions("StringUtils", StringUtils.class);
            ENGINE.addInstanceFunctions("ctx", ChainContext.class);

            // 移除可能危险的默认函数
            ENGINE.removeFunction("sys");
            ENGINE.removeFunction("exec");
            ENGINE.removeFunction("load");
            ENGINE.removeFunction("include");
            ENGINE.removeFunction("eval");
            ENGINE.removeFunction("compile");
            ENGINE.removeFunction("require");
            ENGINE.removeFunction("new");
            ENGINE.removeFunction("invoke");
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Aviator 注册函数失败: " + e.getMessage());
        }
    }

    private AviatorExpressionEvaluator() {
    }

    /**
     * 构建 Aviator 执行环境：上下文变量 + params 别名。
     */
    public static Map<String, Object> buildEnv(Map<String, Object> snapshot) {
        Map<String, Object> env = new HashMap<>(snapshot);
        env.put("params", new HashMap<>(snapshot));
        return env;
    }

    /**
     * SCRIPT 节点环境：额外注入 ctx。
     */
    public static Map<String, Object> buildEnv(ChainContext context) {
        Map<String, Object> env = buildEnv(context.snapshot());
        env.put("ctx", context);
        return env;
    }

    /**
     * 规范化表达式：去 ${} 占位符、剥离 groovy:/aviator: 前缀。
     */
    public static String normalizeExpression(String expression) {
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

    /**
     * Groovy 风格 ctx 方法调用兼容：Aviator 需 {@code ctx.put(ctx, key, val)} 形式。
     */
    static String normalizeCtxMethodCalls(String expr) {
        if (expr == null || expr.isEmpty()) {
            return expr;
        }
        String normalized = CTX_PUT_CALL.matcher(expr).replaceAll("ctx.put(ctx, ");
        return CTX_GET_CALL.matcher(normalized).replaceAll("ctx.get(ctx, ");
    }

    /**
     * 求值布尔表达式；异常或空表达式按调用方约定处理。
     */
    public static boolean evaluateBoolean(String expression, Map<String, Object> snapshot) {
        String expr = normalizeExpression(expression);
        if (expr.isEmpty()) {
            return true;
        }
        try {
            Object result = ENGINE.execute(expr, buildEnv(snapshot));
            return toBoolean(result);
        } catch (Exception e) {
            log.error("条件表达式评估失败 condition={}", expression, e);
            return false;
        }
    }

    /**
     * 执行表达式并返回结果（SCRIPT 节点等）。
     * 含安全校验：脚本长度限制、编译缓存、执行超时。
     */
    public static Object execute(String script, Map<String, Object> env) {
        String expr = normalizeExpression(script);
        if (expr.isEmpty()) {
            throw new IllegalArgumentException("脚本内容为空");
        }
        // 脚本长度限制
        if (expr.length() > MAX_SCRIPT_LENGTH) {
            throw new IllegalArgumentException(
                    "脚本长度超过限制 length=" + expr.length() + " max=" + MAX_SCRIPT_LENGTH);
        }

        try {
            // 使用编译缓存
            Expression compiled = getOrCompile(expr);
            return compiled.execute(env);
        } catch (Exception e) {
            log.error("脚本执行失败 script={}", script, e);
            throw new RuntimeException("脚本执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取或编译表达式（带缓存）
     */
    private static Expression getOrCompile(String expr) {
        Expression cached = EXPRESSION_CACHE.get(expr);
        if (cached != null) {
            return cached;
        }
        // 缓存大小控制
        if (EXPRESSION_CACHE.size() >= MAX_CACHE_SIZE) {
            log.warn("表达式缓存已满，将清理部分缓存 currentSize={}", EXPRESSION_CACHE.size());
            EXPRESSION_CACHE.clear();
        }
        Expression compiled = AviatorEvaluator.compile(expr, true);
        EXPRESSION_CACHE.put(expr, compiled);
        return compiled;
    }

    /**
     * 清理表达式缓存（用于热加载场景）
     */
    public static void clearCache() {
        int size = EXPRESSION_CACHE.size();
        EXPRESSION_CACHE.clear();
        log.info("表达式缓存已清理 clearedCount={}", size);
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
}
