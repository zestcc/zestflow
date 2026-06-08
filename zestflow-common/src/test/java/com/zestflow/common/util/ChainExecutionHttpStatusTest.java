package com.zestflow.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChainExecutionHttpStatusTest {

    @Test
    void infrastructureErrorsReturn500() {
        assertThat(ChainExecutionHttpStatus.resolve("CHAIN_NOT_PUBLISHED")).isEqualTo(500);
    }

    @Test
    void businessForbiddenReturns403() {
        assertThat(ChainExecutionHttpStatus.resolve("ACCESS_DENIED")).isEqualTo(403);
        assertThat(ChainExecutionHttpStatus.resolve("FORBIDDEN")).isEqualTo(403);
    }

    @Test
    void businessNotFoundReturns404() {
        assertThat(ChainExecutionHttpStatus.resolve("BOOK_NOT_FOUND")).isEqualTo(404);
    }

    @Test
    void validationReturns400() {
        assertThat(ChainExecutionHttpStatus.resolve("VALIDATION_ERROR")).isEqualTo(400);
    }
}
