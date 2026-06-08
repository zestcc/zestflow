package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiSuggestResponse {

    private String proposedChainData;
    private String summary;
    /** LLM 思考/推理过程（中文） */
    private String reasoning;
    private AiValidationVO validation;
    private Long sessionId;
    private int repairRounds;
    /** 实际调用的模型名 */
    private String model;
    /** 生成流水线步骤摘要 */
    private List<String> progressSteps;
}
