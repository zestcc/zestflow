package com.zestflow.admin.ai.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiTenantConfigSaveDTO {

    private Boolean enabled;
    private String preset;
    private String baseUrl;
    private String apiKey;
    private String model;
    private List<String> allowedPresets;
}
