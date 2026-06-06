package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.ai.model.dto.AiTenantConfigSaveDTO;
import com.zestflow.admin.ai.model.entity.AiTenantConfigPO;
import com.zestflow.admin.ai.model.vo.AiConfigStatusVO;
import com.zestflow.admin.ai.model.vo.AiProviderVO;
import com.zestflow.admin.ai.model.vo.AiTenantConfigVO;
import com.zestflow.admin.ai.repository.AiTenantConfigMapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 租户 AI 配置服务
 */
@Service
@RequiredArgsConstructor
public class TenantAiConfigService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiTenantConfigMapper configMapper;
    private final AiProviderPresetRegistry presetRegistry;
    private final AiApiKeyCipher apiKeyCipher;
    private final AiProperties aiProperties;
    private final TenantAppContext tenantAppContext;

    public AiTenantConfigVO getTenantConfig(Long tenantId) {
        AiTenantConfigPO po = findByTenantId(tenantId);
        return toVo(po);
    }

    public AiTenantConfigVO saveTenantConfig(Long tenantId, AiTenantConfigSaveDTO dto) {
        if (dto.getPreset() != null) {
            presetRegistry.requireById(dto.getPreset());
        }
        AiTenantConfigPO existing = findByTenantId(tenantId);
        AiTenantConfigPO po = existing != null ? existing : new AiTenantConfigPO();
        if (existing == null) {
            po.setTenantId(tenantId);
        }
        if (dto.getEnabled() != null) {
            po.setEnabled(dto.getEnabled());
        }
        if (StringUtils.hasText(dto.getPreset())) {
            po.setPreset(dto.getPreset());
        }
        po.setBaseUrl(StringUtils.hasText(dto.getBaseUrl()) ? dto.getBaseUrl().trim() : null);
        if (StringUtils.hasText(dto.getModel())) {
            po.setModel(dto.getModel().trim());
        }
        if (StringUtils.hasText(dto.getApiKey())) {
            po.setApiKeyEnc(apiKeyCipher.encrypt(dto.getApiKey().trim()));
        }
        if (dto.getAllowedPresets() != null) {
            try {
                po.setAllowedPresets(MAPPER.writeValueAsString(dto.getAllowedPresets()));
            } catch (Exception e) {
                throw new BizException(ErrorCode.VALIDATION_ERROR);
            }
        }
        if (existing == null) {
            if (po.getPreset() == null) {
                po.setPreset(aiProperties.getDefaultPreset());
            }
            if (po.getEnabled() == null) {
                po.setEnabled(false);
            }
            configMapper.insert(po);
        } else {
            configMapper.updateById(po);
        }
        return toVo(po);
    }

    public AiConfigStatusVO getConfigStatus(Long tenantId) {
        EffectiveAiConfig effective = resolveEffectiveConfig(tenantId);
        boolean tenantEnabled = isCopilotEnabledForTenant(tenantId);
        String displayName = presetRegistry.getById(effective.preset())
                .map(AiProviderPreset::getDisplayName)
                .orElse(effective.preset());
        return AiConfigStatusVO.builder()
                .globallyEnabled(aiProperties.isEnabled())
                .tenantEnabled(tenantEnabled)
                .copilotAvailable(aiProperties.isEnabled() && tenantEnabled && effective.ready())
                .preset(effective.preset())
                .model(effective.model())
                .presetDisplayName(displayName)
                .build();
    }

    public List<AiProviderVO> listProvidersForTenant(Long tenantId) {
        List<String> allowed = getAllowedPresetIds(tenantId);
        return presetRegistry.listPresets().stream()
                .filter(p -> allowed.isEmpty() || allowed.contains(p.getId()))
                .map(this::toProviderVo)
                .toList();
    }

    public boolean isCopilotEnabledForTenant(Long tenantId) {
        if (!aiProperties.isEnabled()) {
            return false;
        }
        AiTenantConfigPO po = findByTenantId(tenantId);
        return po != null && Boolean.TRUE.equals(po.getEnabled()) && resolveEffectiveConfig(tenantId).ready();
    }

    public EffectiveAiConfig resolveEffectiveConfig(Long tenantId) {
        AiTenantConfigPO po = findByTenantId(tenantId);
        String presetId = po != null && StringUtils.hasText(po.getPreset())
                ? po.getPreset() : aiProperties.getDefaultPreset();
        AiProviderPreset preset = presetRegistry.getById(presetId)
                .orElse(presetRegistry.getById(aiProperties.getDefaultPreset()).orElse(null));

        String baseUrl = po != null && StringUtils.hasText(po.getBaseUrl())
                ? po.getBaseUrl()
                : (preset != null ? preset.getBaseUrl() : null);
        String model = po != null && StringUtils.hasText(po.getModel())
                ? po.getModel()
                : (preset != null ? preset.getDefaultModel() : null);
        String apiKey = po != null && StringUtils.hasText(po.getApiKeyEnc())
                ? apiKeyCipher.decrypt(po.getApiKeyEnc())
                : resolveEnvApiKey(presetId, preset);

        boolean keyRequired = preset == null || preset.isApiKeyRequired();
        if (!keyRequired && !StringUtils.hasText(apiKey) && preset != null
                && StringUtils.hasText(preset.getApiKeyPlaceholder())) {
            apiKey = preset.getApiKeyPlaceholder();
        }
        boolean ready = StringUtils.hasText(baseUrl) && StringUtils.hasText(model)
                && (!keyRequired || StringUtils.hasText(apiKey));

        return new EffectiveAiConfig(presetId, baseUrl, model, apiKey, keyRequired, ready);
    }

    public EffectiveAiConfig resolveForTest(AiTenantConfigSaveDTO override) {
        String presetId = StringUtils.hasText(override.getPreset())
                ? override.getPreset() : aiProperties.getDefaultPreset();
        AiProviderPreset preset = presetRegistry.requireById(presetId);
        String baseUrl = StringUtils.hasText(override.getBaseUrl())
                ? override.getBaseUrl() : preset.getBaseUrl();
        String model = StringUtils.hasText(override.getModel())
                ? override.getModel() : preset.getDefaultModel();
        String apiKey = override.getApiKey();
        boolean ready = StringUtils.hasText(baseUrl) && StringUtils.hasText(model)
                && (!preset.isApiKeyRequired() || StringUtils.hasText(apiKey));
        return new EffectiveAiConfig(presetId, baseUrl, model, apiKey, preset.isApiKeyRequired(), ready);
    }

    /**
     * 从环境变量解析免费 API Key（按预设 ID）。
     */
    public String resolveEnvApiKey(String presetId, AiProviderPreset preset) {
        if (aiProperties.getEnvKeys() != null && StringUtils.hasText(presetId)) {
            String fromEnv = aiProperties.getEnvKeys().get(presetId);
            if (StringUtils.hasText(fromEnv)) {
                return fromEnv.trim();
            }
        }
        if (preset != null && !preset.isApiKeyRequired()
                && StringUtils.hasText(preset.getApiKeyPlaceholder())) {
            return preset.getApiKeyPlaceholder();
        }
        return null;
    }

    /**
     * 检测环境变量中首个可用的免费云端预设（优先级：硅基 > DeepSeek > Groq > 通义 > Gemini）。
     */
    public String detectEnvPresetId() {
        if (aiProperties.getEnvKeys() == null) {
            return null;
        }
        for (String id : List.of("siliconflow", "deepseek", "groq", "dashscope", "gemini", "github-models")) {
            String key = aiProperties.getEnvKeys().get(id);
            if (StringUtils.hasText(key) && presetRegistry.getById(id).isPresent()) {
                return id;
            }
        }
        return null;
    }

    public Long getCurrentTenantId() {
        return tenantAppContext.getCurrentTenantId();
    }

    private AiTenantConfigPO findByTenantId(Long tenantId) {
        return configMapper.selectOne(new LambdaQueryWrapper<AiTenantConfigPO>()
                .eq(AiTenantConfigPO::getTenantId, tenantId)
                .last("LIMIT 1"));
    }

    private List<String> getAllowedPresetIds(Long tenantId) {
        AiTenantConfigPO po = findByTenantId(tenantId);
        if (po == null || !StringUtils.hasText(po.getAllowedPresets())) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(po.getAllowedPresets(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private AiTenantConfigVO toVo(AiTenantConfigPO po) {
        if (po == null) {
            return AiTenantConfigVO.builder()
                    .enabled(false)
                    .preset(aiProperties.getDefaultPreset())
                    .apiKeyConfigured(false)
                    .build();
        }
        String masked = maskApiKey(po.getApiKeyEnc());
        List<String> allowed = Collections.emptyList();
        if (StringUtils.hasText(po.getAllowedPresets())) {
            try {
                allowed = MAPPER.readValue(po.getAllowedPresets(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                allowed = Collections.emptyList();
            }
        }
        return AiTenantConfigVO.builder()
                .enabled(po.getEnabled())
                .preset(po.getPreset())
                .baseUrl(po.getBaseUrl())
                .model(po.getModel())
                .apiKeyMasked(masked)
                .apiKeyConfigured(StringUtils.hasText(po.getApiKeyEnc()))
                .allowedPresets(allowed)
                .build();
    }

    private String maskApiKey(String apiKeyEnc) {
        if (!StringUtils.hasText(apiKeyEnc)) {
            return null;
        }
        try {
            String plain = apiKeyCipher.decrypt(apiKeyEnc);
            if (!StringUtils.hasText(plain)) {
                return "****";
            }
            if (plain.length() <= 8) {
                return "****";
            }
            return plain.substring(0, 4) + "****" + plain.substring(plain.length() - 4);
        } catch (Exception e) {
            return "****";
        }
    }

    private AiProviderVO toProviderVo(AiProviderPreset preset) {
        return AiProviderVO.builder()
                .id(preset.getId())
                .tier(preset.getTier())
                .displayName(preset.getDisplayName())
                .displayNameEn(preset.getDisplayNameEn())
                .region(preset.getRegion())
                .baseUrl(preset.getBaseUrl())
                .defaultModel(preset.getDefaultModel())
                .models(preset.getModels())
                .apiKeyRequired(preset.isApiKeyRequired())
                .apiKeyPlaceholder(preset.getApiKeyPlaceholder())
                .docUrl(preset.getDocUrl())
                .tags(preset.getTags())
                .recommendedFor(preset.getRecommendedFor())
                .qualityTier(preset.getQualityTier())
                .notes(preset.getNotes())
                .build();
    }

    /** 租户生效的 LLM 连接参数 */
    public record EffectiveAiConfig(
            String preset,
            String baseUrl,
            String model,
            String apiKey,
            boolean apiKeyRequired,
            boolean ready
    ) {}
}
