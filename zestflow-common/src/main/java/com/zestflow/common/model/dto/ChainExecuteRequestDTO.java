package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Admin 向 Executor 发送的执行请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainExecuteRequestDTO {

    /** 链编码 */
    private String chainCode;

    /** 执行参数 */
    private Map<String, Object> params;

    /** 请求来源标识 */
    private String source;

    /** 超时时间（毫秒），覆盖链配置 */
    private Long timeoutMs;

    /** 链路追踪 ID */
    private String traceId;

    /** 幂等键 — 与 traceId 二选一；相同键在 TTL 内返回同一执行结果 */
    private String idempotencyKey;

    /** 请求 HTTP 头（Mode 1/2 透传至 ChainContext.headers） */
    private Map<String, String> headers;

    /**
     * 解析实际幂等键：优先 idempotencyKey，否则 traceId。
     */
    public String resolveIdempotencyKey() {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey.trim();
        }
        if (traceId != null && !traceId.isBlank()) {
            return traceId.trim();
        }
        return null;
    }
}
