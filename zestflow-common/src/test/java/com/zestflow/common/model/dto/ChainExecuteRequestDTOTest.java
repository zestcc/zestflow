package com.zestflow.common.model.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChainExecuteRequestDTOTest {

    @Test
    void resolveIdempotencyKey_prefersIdempotencyKeyOverTraceId() {
        ChainExecuteRequestDTO dto = ChainExecuteRequestDTO.builder()
                .idempotencyKey("  key-a  ")
                .traceId("trace-b")
                .build();

        assertEquals("key-a", dto.resolveIdempotencyKey());
    }

    @Test
    void resolveIdempotencyKey_fallsBackToTraceId() {
        ChainExecuteRequestDTO dto = ChainExecuteRequestDTO.builder()
                .traceId(" trace-c ")
                .build();

        assertEquals("trace-c", dto.resolveIdempotencyKey());
    }

    @Test
    void resolveIdempotencyKey_returnsNullWhenBothBlank() {
        ChainExecuteRequestDTO dto = new ChainExecuteRequestDTO();

        assertNull(dto.resolveIdempotencyKey());
    }
}
