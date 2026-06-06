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
}
