package com.zestflow.admin.ai.model.dto;

import lombok.Data;

@Data
public class AiCopilotSessionCreateDTO {

    private String appCode;
    private String designId;
    private String chainCode;
    private String title;
    private String mode;
}
