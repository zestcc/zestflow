package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiDiagnoseResponse {

    private String diagnosis;
    private String suggestion;
    private boolean stub;
    private Long sessionId;
    /** 建议打开的设计器路径，如 /design/DES001?appCode=demo-app */
    private String openDesignPath;
}
