package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiCopilotSessionSummaryVO {

    private Long sessionId;
    private String title;
    private String mode;
    private String lastModel;
    private Boolean success;
    private Integer latencyMs;
    private Integer messageCount;
    private Boolean hasPending;
    private String lastMessagePreview;
    private LocalDateTime createdAt;
}
