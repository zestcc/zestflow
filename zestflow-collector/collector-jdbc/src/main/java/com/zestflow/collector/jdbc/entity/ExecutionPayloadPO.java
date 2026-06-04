package com.zestflow.collector.jdbc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 执行载荷 PO — 链事件与外部调用（试验场/API）统一大字段表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("execution_payload")
public class ExecutionPayloadPO {

    /** 关联 ID：chain_event.event_id 或 invocation_id */
    private String refId;

    /** CHAIN_EVENT | INVOCATION */
    private String refType;

    private String executionId;
    /** PLAYGROUND / SCHEDULE / API（仅 INVOCATION） */
    private String sourceType;
    private String sceneCode;
    /** 入参 / 请求体 */
    private String params;
    /** 出参 / 响应体 */
    private String result;
    private String errorMessage;
    /** 扩展字段（如 request_headers JSON） */
    private String extra;
    private Long tenantId;
    private String appCode;
    private LocalDateTime createdAt;

    public static final String REF_CHAIN_EVENT = "CHAIN_EVENT";
    public static final String REF_INVOCATION = "INVOCATION";
}
