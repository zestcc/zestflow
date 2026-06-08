package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiCopilotSessionDetailVO {

    private Long sessionId;
    private String title;
    private String mode;
    private String model;
    private List<AiCopilotMessageVO> messages;
    private String pendingChainData;
    private String pendingSummary;
    private AiValidationVO pendingValidation;
}
