package com.zestflow.admin.playground.support;

import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaygroundRequestPathValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/execute",
            "/api/orders/handleApplyAfterSale",
            "/api/playground/payment"
    })
    void validate_acceptsAllowedPaths(String path) {
        assertThatCode(() -> PlaygroundRequestPathValidator.validate(path))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8081/api/orders",
            "https://evil.example/api",
            "//api/orders",
            "/other/path",
            "/api/../admin",
            "/api/user@host"
    })
    void validate_rejectsUnsafePaths(String path) {
        assertThatThrownBy(() -> PlaygroundRequestPathValidator.validate(path))
                .isInstanceOf(BizException.class);
    }

    @Test
    void validate_rejectsBlankPath() {
        assertThatThrownBy(() -> PlaygroundRequestPathValidator.validate("  "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void validate_rejectsPathWithoutLeadingSlash() {
        assertThatThrownBy(() -> PlaygroundRequestPathValidator.validate("api/orders"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("/");
    }
}
