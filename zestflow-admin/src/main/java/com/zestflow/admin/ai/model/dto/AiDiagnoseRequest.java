package com.zestflow.admin.ai.model.dto;

import lombok.Data;

@Data
public class AiDiagnoseRequest {

    private String appCode;
    private String chainCode;
    /** 执行追踪 ID（与 traceId 二选一） */
    private String executionId;
    private String traceId;
    private String errorSummary;
    private String designId;
}
