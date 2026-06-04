package com.zestflow.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 外部调用载荷（试验场 / API 触发）— 存 app_log，Admin playground_record 只保留 invocationId 引用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvocationPayloadDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String invocationId;
    /** PLAYGROUND / SCHEDULE / API */
    private String sourceType;
    private String executionId;
    private String sceneCode;
    private String requestBody;
    private String responseBody;
    private String requestHeaders;
    private Long tenantId;
    private String appCode;
}
