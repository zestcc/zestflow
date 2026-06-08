package com.zestflow.admin.ai.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiSuggestRequest {

    private String designId;
    private String chainCode;
    private String appCode;
    private String currentChainData;
    private String userMessage;
    /** generate | modify | fix-errors */
    private String mode;
    private List<String> allowedComponents;
    /** 续接已有会话（同设计上下文） */
    private Long sessionId;
    /** 画布 graph JSON（可选，辅助理解拓扑） */
    private String graphData;
}
