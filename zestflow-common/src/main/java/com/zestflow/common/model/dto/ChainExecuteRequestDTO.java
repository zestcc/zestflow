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
}
