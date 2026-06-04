package com.zestflow.executor.expression;

import com.zestflow.executor.context.ChainContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AviatorExpressionEvaluatorTest {

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
    void normalizeStripsGroovyPrefix() {
        assertThat(AviatorExpressionEvaluator.normalizeExpression("groovy: 1 == 1")).isEqualTo("1 == 1");
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
                .isInstanceOf(IllegalArgumentException.class);
    }
}
