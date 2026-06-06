package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiExpressionSuggestResponse {

    private String expression;
    private String explanation;
    private Long sessionId;
}
