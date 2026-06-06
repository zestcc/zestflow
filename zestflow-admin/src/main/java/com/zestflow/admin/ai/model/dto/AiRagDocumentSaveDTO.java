package com.zestflow.admin.ai.model.dto;

import lombok.Data;

@Data
public class AiRagDocumentSaveDTO {

    private String title;
    private String appCode;
    private String content;
    private Boolean enabled;
    private Integer sortOrder;
}
