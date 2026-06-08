package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiCopilotJobVO {

    private Long jobId;
    private String jobType;
    private String status;
    private Long sessionId;
    private String progressStep;
    private String reasoning;
    private AiSuggestResponse suggestResult;
    private AiExplainResponse explainResult;
    private String errorMessage;
    private Integer latencyMs;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
