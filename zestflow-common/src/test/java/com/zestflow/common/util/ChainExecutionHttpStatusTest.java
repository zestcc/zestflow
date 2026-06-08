package com.zestflow.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChainExecutionHttpStatusTest {

    @Test
    void infrastructureErrorsReturn500() {
        assertEquals(500, ChainExecutionHttpStatus.resolve("CHAIN_NOT_PUBLISHED"));
    }

    @Test
    void businessForbiddenReturns403() {
        assertEquals(403, ChainExecutionHttpStatus.resolve("ACCESS_DENIED"));
        assertEquals(403, ChainExecutionHttpStatus.resolve("FORBIDDEN"));
    }

    @Test
    void businessNotFoundReturns404() {
        assertEquals(404, ChainExecutionHttpStatus.resolve("BOOK_NOT_FOUND"));
    }

    @Test
    void validationReturns400() {
        assertEquals(400, ChainExecutionHttpStatus.resolve("VALIDATION_ERROR"));
    }
}
