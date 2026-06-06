package com.zestflow.admin.ai.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiExpressionSuggestRequest {

    private String appCode;
    private String designId;
    private String chainCode;
    private String currentExpression;
    private String userMessage;
    private String contextHint;
    private List<String> allowedComponents;
}
