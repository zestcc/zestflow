package com.zestflow.executor.expression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AviatorExpressionSecurityTest {

    @Test
    void rejectsJavaLangReference() {
        assertThatThrownBy(() -> AviatorExpressionSecurity.validate("java.lang.System.exit(0)", 1000))
                .isInstanceOf(ExpressionEvaluationException.class);
    }

    @Test
    void rejectsOverLengthExpression() {
        assertThatThrownBy(() -> AviatorExpressionSecurity.validate("123456789", 5))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("长度");
    }
}
