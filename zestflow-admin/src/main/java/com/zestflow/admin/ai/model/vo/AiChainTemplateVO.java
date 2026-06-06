package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiChainTemplateVO {

    private Long id;
    private String name;
    private String description;
    private String appCode;
    private String promptSummary;
    private String chainData;
    private String createdBy;
    private LocalDateTime createdAt;
}
