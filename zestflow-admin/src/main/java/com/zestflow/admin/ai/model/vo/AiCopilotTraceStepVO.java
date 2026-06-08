package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiCopilotTraceStepVO {

    private Long id;
    private Long sessionId;
    private Long jobId;
    private String stepType;
    private String stepName;
    private String status;
    private Integer latencyMs;
    private Integer tokenEstimate;
    private String detailJson;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
