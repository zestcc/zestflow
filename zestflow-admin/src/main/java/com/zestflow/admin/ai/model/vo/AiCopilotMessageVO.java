package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiCopilotMessageVO {

    private Long id;
    private String role;
    private String content;
    private String reasoning;
    private LocalDateTime createdAt;
}
