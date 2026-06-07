package com.zestflow.admin.config;

import com.zestflow.admin.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 平台参数：sys_config（租户 1）优先，{@link AiProperties} 为 yaml 兜底。
 * envKeys / defaultPreset 仍仅来自 yaml。
 */
@Component
@RequiredArgsConstructor
public class AiPlatformConfig {

    private final PlatformConfigReader platformConfig;
    private final AiProperties yaml;

    public boolean isEnabled() {
        return platformConfig.getBoolean(SysConfigKeys.AI_ENABLED, yaml::isEnabled);
    }

    public int getTimeoutMs() {
        return platformConfig.getInt(SysConfigKeys.AI_TIMEOUT_MS, yaml::getTimeoutMs);
    }

    public int getMaxTokens() {
        return platformConfig.getInt(SysConfigKeys.AI_MAX_TOKENS, yaml::getMaxTokens);
    }

    public double getTemperature() {
        return platformConfig.getDouble(SysConfigKeys.AI_TEMPERATURE, yaml::getTemperature);
    }

    public boolean isPiiMask() {
        return platformConfig.getBoolean(SysConfigKeys.AI_PII_MASK, yaml::isPiiMask);
    }

    public int getRepairMaxRounds() {
        return platformConfig.getInt(SysConfigKeys.AI_REPAIR_MAX_ROUNDS, yaml::getRepairMaxRounds);
    }

    public boolean isTenantAutoInit() {
        return platformConfig.getBoolean(SysConfigKeys.AI_TENANT_AUTO_INIT, yaml::isTenantAutoInit);
    }

    public boolean isRagEnabled() {
        return platformConfig.getBoolean(SysConfigKeys.AI_RAG_ENABLED, yaml::isRagEnabled);
    }

    public String getRagMode() {
        return platformConfig.getString(SysConfigKeys.AI_RAG_MODE, yaml::getRagMode);
    }

    public boolean isRagUseLlmEmbedding() {
        return platformConfig.getBoolean(SysConfigKeys.AI_RAG_USE_LLM_EMBEDDING, yaml::isRagUseLlmEmbedding);
    }

    public String getRagEmbeddingModel() {
        return platformConfig.getString(SysConfigKeys.AI_RAG_EMBEDDING_MODEL, yaml::getRagEmbeddingModel);
    }

    public int getRagEmbeddingCandidateLimit() {
        return platformConfig.getInt(SysConfigKeys.AI_RAG_EMBEDDING_CANDIDATE_LIMIT, yaml::getRagEmbeddingCandidateLimit);
    }

    public int getRagMaxChunks() {
        return platformConfig.getInt(SysConfigKeys.AI_RAG_MAX_CHUNKS, yaml::getRagMaxChunks);
    }

    public String getRagTenantDataDir() {
        return platformConfig.getString(SysConfigKeys.AI_RAG_TENANT_DATA_DIR, yaml::getRagTenantDataDir);
    }

    public boolean isRagTenantFilesystemEnabled() {
        return platformConfig.getBoolean(SysConfigKeys.AI_RAG_TENANT_FILESYSTEM_ENABLED, yaml::isRagTenantFilesystemEnabled);
    }

    public int getRagTenantMaxDocuments() {
        return platformConfig.getInt(SysConfigKeys.AI_RAG_TENANT_MAX_DOCUMENTS, yaml::getRagTenantMaxDocuments);
    }

    public int getRagTenantMaxContentBytes() {
        return platformConfig.getInt(SysConfigKeys.AI_RAG_TENANT_MAX_CONTENT_BYTES, yaml::getRagTenantMaxContentBytes);
    }

    public int getDefaultMonthlyTokenQuota() {
        return platformConfig.getInt(SysConfigKeys.AI_DEFAULT_MONTHLY_TOKEN_QUOTA, yaml::getDefaultMonthlyTokenQuota);
    }

    public String getDefaultPreset() {
        return yaml.getDefaultPreset();
    }

    public Map<String, String> getEnvKeys() {
        return yaml.getEnvKeys();
    }
}
