package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiProviderVO {

    private String id;
    private String tier;
    private String displayName;
    private String displayNameEn;
    private String region;
    private String baseUrl;
    private String defaultModel;
    private List<String> models;
    private boolean apiKeyRequired;
    private String apiKeyPlaceholder;
    private String docUrl;
    private List<String> tags;
    private List<String> recommendedFor;
    private String qualityTier;
    private String notes;
}
