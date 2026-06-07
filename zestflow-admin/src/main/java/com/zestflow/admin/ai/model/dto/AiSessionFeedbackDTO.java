package com.zestflow.admin.ai.model.dto;

import lombok.Data;

@Data
public class AiSessionFeedbackDTO {

    /** 1采纳 0拒绝 */
    private Integer adopted;

    /** 可选：同步 Chain-first 学习事件 */
    private String intent;
    private String feature;
    private Boolean validatePassed;
    private Integer validateRounds;
    private Boolean playgroundSuccess;
    private String userCorrection;
    private Integer httpMode;
    /** 采纳的 chainData JSON（沉淀到应用端知识库） */
    private String chainData;
}
