package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiCopilotTraceSessionRowVO {

    private Long sessionId;
    private String title;
    private String mode;
    private String appCode;
    private String designId;
    private Integer stepCount;
    private Integer totalLatencyMs;
    private Boolean success;
    private LocalDateTime createdAt;
}
