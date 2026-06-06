package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiValidationVO {

    private boolean valid;
    private List<String> errors;
}
