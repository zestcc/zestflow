package com.zestflow.admin.ai.model.dto;

import lombok.Data;

@Data
public class AiSessionFeedbackDTO {

    /** 1采纳 0拒绝 */
    private Integer adopted;
}
