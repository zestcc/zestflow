package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiTenantConfigVO {

    private Boolean enabled;
    private String preset;
    private String baseUrl;
    private String model;
    /** 脱敏后的 Key，如 sk-**** */
    private String apiKeyMasked;
    private boolean apiKeyConfigured;
    private List<String> allowedPresets;
}
