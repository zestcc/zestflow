package com.zestflow.admin.ai.model.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AiComposeChainRequest {
    private String appCode;
    private String patternId;
    private String chainCode;
    private String chainName;
    private Map<String, String> componentBindings;
}
