package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiComponentScaffoldResponse {

    private String fullJavaCode;
    private String summary;
    private List<String> checklist;
    private Long sessionId;
}
