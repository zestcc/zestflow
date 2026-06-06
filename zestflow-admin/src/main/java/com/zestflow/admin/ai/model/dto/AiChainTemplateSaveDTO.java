package com.zestflow.admin.ai.model.dto;

import lombok.Data;

@Data
public class AiChainTemplateSaveDTO {

    private String name;
    private String description;
    private String appCode;
    private String promptSummary;
    private String chainData;
}
