package com.zestflow.admin.ai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 提供商预设（来自 ai-providers.yaml）
 */
@Data
public class AiProviderPreset {

    private String id;
    private String tier;
    private String displayName;
    private String displayNameEn;
    private String region;
    private String baseUrl;
    private String defaultModel;
    private List<String> models = new ArrayList<>();
    private boolean apiKeyRequired = true;
    private String apiKeyPlaceholder;
    private String docUrl;
    private List<String> tags = new ArrayList<>();
    private List<String> recommendedFor = new ArrayList<>();
    private String qualityTier;
    private String notes;
    private Boolean deprecated;
    private String successor;
}
