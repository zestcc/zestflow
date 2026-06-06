package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTestConnectionResponse {

    private boolean success;
    private long latencyMs;
    private String model;
    private String message;
}
