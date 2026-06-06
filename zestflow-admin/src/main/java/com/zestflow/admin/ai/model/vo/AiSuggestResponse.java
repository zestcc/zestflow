package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiSuggestResponse {

    private String proposedChainData;
    private String summary;
    private AiValidationVO validation;
    private Long sessionId;
    private int repairRounds;
}
