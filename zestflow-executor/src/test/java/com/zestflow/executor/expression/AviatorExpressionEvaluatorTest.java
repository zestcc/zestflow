package com.zestflow.executor.expression;

import com.zestflow.executor.context.ChainContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AviatorExpressionEvaluatorTest {

    @BeforeEach
    void setUp() {
        AviatorExpressionEvaluator.resetToDefaults();
    }

    @AfterEach
    void tearDown() {
        AviatorExpressionEvaluator.resetToDefaults();
    }

    @Test
    void evaluateBooleanWithStringUtilsHasText() {
        assertThat(AviatorExpressionEvaluator.evaluateBoolean(
                "StringUtils.hasText(supplierType)",
                Map.of("supplierType", "OTA"))).isTrue();
    }

    @Test
    void evaluateBooleanMissingVariableReturnsFalse() {
        assertThat(AviatorExpressionEvaluator.evaluateBoolean(
                "StringUtils.hasText(supplierType)",
                Map.of())).isFalse();
    }

    @Test
    void evaluateBooleanFailOpenWhenConfigured() {
        ExecutorExpressionProperties props = new ExecutorExpressionProperties();
        props.setConditionFailOpen(true);
        AviatorExpressionEvaluator.configure(props);

        assertThat(AviatorExpressionEvaluator.evaluateBoolean(
                "!!! invalid @@@",
                Map.of())).isTrue();
    }

    @Test
    void normalizeStripsGroovyPrefix() {
        assertThat(AviatorExpressionEvaluator.normalizeExpression("groovy: 1 == 1")).isEqualTo("1 == 1");
    }

    @Test
    void normalizeCtxMethodCallsUsesChainCtxNamespace() {
        assertThat(AviatorExpressionEvaluator.normalizeCtxMethodCalls("ctx.get('price')"))
                .isEqualTo("chainCtx.get(ctx, 'price')");
        assertThat(AviatorExpressionEvaluator.normalizeCtxMethodCalls("ctx.put('k', 1)"))
                .isEqualTo("chainCtx.put(ctx, 'k', 1)");
    }

    @Test
    void executeScriptWithCtx() {
        Map<String, Object> env = AviatorExpressionEvaluator.buildEnv(Map.of("price", 100));
        Object result = AviatorExpressionEvaluator.execute(
                "let base = long(price); seq.map('discounted', base * 0.8)", env);
        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("discounted")).isEqualTo(80.0);
    }

    @Test
    void executeScriptWithCtxPutAndGet() {
        ChainContext context = new ChainContext("inst-1", "chain-1", Map.of("price", 100));
        Map<String, Object> env = AviatorExpressionEvaluator.buildEnv(context);
        Object result = AviatorExpressionEvaluator.execute(
                "let base = long(ctx.get('price')); ctx.put('discount', base * 0.8); seq.map('discounted', base * 0.8)",
                env);
        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("discounted")).isEqualTo(80.0);
        assertThat(context.get("discount")).isEqualTo(80.0);
    }

    @Test
    void executeEmptyScriptThrows() {
        assertThatThrownBy(() -> AviatorExpressionEvaluator.execute("", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class);
    }

    @Test
    void executeForbiddenPatternThrows() {
        assertThatThrownBy(() -> AviatorExpressionEvaluator.execute(
                "Runtime.getRuntime()", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("禁止");
    }

    @Test
    void executeExceedsMaxLengthThrows() {
        ExecutorExpressionProperties props = new ExecutorExpressionProperties();
        props.setMaxScriptLength(8);
        AviatorExpressionEvaluator.configure(props);

        assertThatThrownBy(() -> AviatorExpressionEvaluator.execute(
                "1 + 2 + 3 + 4 + 5", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("长度");
    }

    @Test
    void executeTimeoutThrows() {
        ExecutorExpressionProperties props = new ExecutorExpressionProperties();
        props.setTimeoutMs(100);
        props.setMaxLoopCount(1_000_000);
        AviatorExpressionEvaluator.configure(props);

        assertThatThrownBy(() -> AviatorExpressionEvaluator.execute(
                "while(true) { 1 }", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("超时");
    }

    @Test
    void executeExceedsMaxLoopCountThrows() {
        ExecutorExpressionProperties props = new ExecutorExpressionProperties();
        props.setMaxLoopCount(5);
        props.setTimeoutMs(30_000);
        AviatorExpressionEvaluator.configure(props);

        assertThatThrownBy(() -> AviatorExpressionEvaluator.execute(
                "while(true) { 1 }", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class);
    }

    @Test
    void clearCacheResetsCompiledExpressions() {
        AviatorExpressionEvaluator.execute("1 + 1", Map.of());
        AviatorExpressionEvaluator.clearCache();
        Object result = AviatorExpressionEvaluator.execute("2 + 2", Map.of());
        assertThat(result).isEqualTo(4L);
    }
}
