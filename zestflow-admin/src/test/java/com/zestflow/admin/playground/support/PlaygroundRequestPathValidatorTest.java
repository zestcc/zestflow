package com.zestflow.admin.playground.support;

import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaygroundRequestPathValidatorTest {

    private static final List<String> TOMCAT_BASE = List.of("http://127.0.0.1:8081");

    @ParameterizedTest
    @ValueSource(strings = {
            "/execute",
            "/api/orders/handleApplyAfterSale",
            "/api/inventory/batch-empty"
    })
    void validate_acceptsRelativePaths(String path) {
        assertThatCode(() -> PlaygroundRequestPathValidator.validate(path, TOMCAT_BASE))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8081/api/inventory/batch-empty"
    })
    void validate_acceptsTomcatFullUrl(String path) {
        assertThatCode(() -> PlaygroundRequestPathValidator.validate(path, TOMCAT_BASE))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:20550/api/inventory/batch-empty",
            "http://localhost:8080/api/inventory/batch-empty",
            "http://evil.example/api/orders",
            "//api/orders",
            "/other/path"
    })
    void validate_rejectsUnsafePaths(String path) {
        assertThatThrownBy(() -> PlaygroundRequestPathValidator.validate(path, TOMCAT_BASE))
                .isInstanceOf(BizException.class);
    }

    @Test
    void validate_rejectsFullUrlWithoutTomcatBase() {
        assertThatThrownBy(() -> PlaygroundRequestPathValidator.validate(
                "http://127.0.0.1:8081/api/orders", List.of()))
                .isInstanceOf(BizException.class);
    }
}
