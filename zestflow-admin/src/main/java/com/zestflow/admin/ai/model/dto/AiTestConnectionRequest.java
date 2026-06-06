package com.zestflow.admin.ai.model.dto;

import lombok.Data;

@Data
public class AiTestConnectionRequest {

    private String preset;
    private String baseUrl;
    private String apiKey;
    private String model;
}
