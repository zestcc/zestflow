package com.zestflow.admin.ai.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiExplainRequest {

    private String designId;
    private String chainCode;
    private String appCode;
    private String currentChainData;
    private List<String> allowedComponents;
    private String userMessage;
    /** 续接已有会话（同设计上下文） */
    private Long sessionId;
    private String graphData;
}
