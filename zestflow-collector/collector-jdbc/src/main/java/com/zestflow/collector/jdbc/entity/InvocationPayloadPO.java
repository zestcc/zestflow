package com.zestflow.collector.jdbc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 外部调用载荷 PO — 对应 invocation_payload 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("invocation_payload")
public class InvocationPayloadPO {

    private String invocationId;
    private String sourceType;
    private String executionId;
    private String sceneCode;
    private String requestBody;
    private String responseBody;
    private String requestHeaders;
    private Long tenantId;
    private String appCode;
    private LocalDateTime createdAt;
}
