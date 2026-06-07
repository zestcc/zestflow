package com.zestflow.admin.ai.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiLearningEventSaveDTO {

    private Long sessionId;
    private String appCode;
    private String intent;
    private String feature;
    private String chainCode;
    private Integer httpMode;
    private List<String> reusedComponents;
    private List<String> createdComponents;
    private Boolean validatePassed;
    private Integer validateRounds;
    private Boolean adopted;
    private Boolean playgroundSuccess;
    private String userCorrection;
}
