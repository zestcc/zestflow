package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AiLearningEventVO {

    private Long id;
    private String appCode;
    private String intent;
    private String feature;
    private String chainCode;
    private Integer httpMode;
    private Boolean validatePassed;
    private Integer validateRounds;
    private Boolean adopted;
    private Boolean playgroundSuccess;
    private BigDecimal promotionScore;
    private Boolean promotionEligible;
    private String userCorrection;
    private Boolean promotedToRag;
    private LocalDateTime createdAt;
}
