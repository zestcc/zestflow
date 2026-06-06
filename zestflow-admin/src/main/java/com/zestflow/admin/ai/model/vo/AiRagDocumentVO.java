package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiRagDocumentVO {

    private Long id;
    private String title;
    private String appCode;
    private String content;
    private Boolean enabled;
    private Integer sortOrder;
    private String sourceType;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
