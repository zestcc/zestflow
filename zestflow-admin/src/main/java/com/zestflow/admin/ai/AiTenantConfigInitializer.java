package com.zestflow.admin.ai;

import com.zestflow.admin.config.AiPlatformConfig;
import com.zestflow.admin.ai.model.entity.AiTenantConfigPO;
import com.zestflow.admin.ai.repository.AiTenantConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 为默认租户初始化 AI 配置：优先使用环境变量中的免费 API Key，否则使用 Ollama 本地免费方案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTenantConfigInitializer implements ApplicationRunner {

    private static final long[] DEFAULT_TENANT_IDS = {1L, 2L};

    private final AiTenantConfigMapper configMapper;
    private final AiPlatformConfig aiPlatformConfig;
    private final AiProviderPresetRegistry presetRegistry;
    private final AiApiKeyCipher apiKeyCipher;
    private final TenantAiConfigService tenantAiConfigService;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!aiPlatformConfig.isEnabled() || !aiPlatformConfig.isTenantAutoInit()) {
            return;
        }
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            log.info("生产环境跳过 AI 租户自动初始化");
            return;
        }
        for (long tenantId : DEFAULT_TENANT_IDS) {
            initTenantIfAbsent(tenantId);
        }
    }

    private void initTenantIfAbsent(long tenantId) {
        Long count = configMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiTenantConfigPO>()
                        .eq(AiTenantConfigPO::getTenantId, tenantId));
        if (count != null && count > 0) {
            return;
        }

        String presetId = tenantAiConfigService.detectEnvPresetId();
        String apiKeyPlain = null;
        if (StringUtils.hasText(presetId)) {
            apiKeyPlain = aiPlatformConfig.getEnvKeys().get(presetId);
        } else {
            presetId = "ollama";
            apiKeyPlain = presetRegistry.getById("ollama")
                    .map(AiProviderPreset::getApiKeyPlaceholder)
                    .orElse("ollama");
        }

        AiProviderPreset preset = presetRegistry.getById(presetId).orElse(null);
        AiTenantConfigPO po = new AiTenantConfigPO();
        po.setTenantId(tenantId);
        po.setEnabled(true);
        po.setPreset(presetId);
        if (preset != null) {
            po.setModel(preset.getDefaultModel());
        }
        if (StringUtils.hasText(apiKeyPlain)) {
            po.setApiKeyEnc(apiKeyCipher.encrypt(apiKeyPlain.trim()));
        }
        try {
            configMapper.insert(po);
            log.info("AI 租户配置已自动初始化 tenantId={} preset={} source={}",
                    tenantId, presetId,
                    "ollama".equals(presetId) ? "local-free" : "env-key");
        } catch (DuplicateKeyException e) {
            log.debug("AI 租户配置已存在，跳过初始化 tenantId={}", tenantId);
        }
    }
}
