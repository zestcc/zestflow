package com.zestflow.admin.ai;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从 classpath 加载 ai-providers.yaml，提供预设查询
 */
@Slf4j
@Component
public class AiProviderPresetRegistry {

    private final Map<String, AiProviderPreset> presets = new LinkedHashMap<>();
    private String version = "unknown";

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("ai-providers.yaml").getInputStream()) {
            Yaml yaml = new Yaml();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                log.warn("ai-providers.yaml 为空");
                return;
            }
            Object versionObj = root.get("version");
            if (versionObj != null) {
                version = versionObj.toString();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> presetMap = (Map<String, Object>) root.get("presets");
            if (presetMap == null) {
                return;
            }
            for (Map.Entry<String, Object> entry : presetMap.entrySet()) {
                AiProviderPreset preset = mapPreset(entry.getKey(), entry.getValue());
                presets.put(entry.getKey(), preset);
            }
            log.info("已加载 AI 提供商预设 {} 个，version={}", presets.size(), version);
        } catch (Exception e) {
            log.error("加载 ai-providers.yaml 失败", e);
            throw new IllegalStateException("无法加载 ai-providers.yaml", e);
        }
    }

    public String getVersion() {
        return version;
    }

    public List<AiProviderPreset> listPresets() {
        List<AiProviderPreset> list = new ArrayList<>(presets.values());
        list.sort(Comparator
                .comparing((AiProviderPreset p) -> "A".equals(p.getTier()) ? 0 : 1)
                .thenComparing(AiProviderPreset::getDisplayName, Comparator.nullsLast(String::compareTo)));
        return Collections.unmodifiableList(list);
    }

    public Optional<AiProviderPreset> getById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(presets.get(id));
    }

    public AiProviderPreset requireById(String id) {
        return getById(id).orElseThrow(() -> new BizException(ErrorCode.AI_PRESET_NOT_FOUND));
    }

    @SuppressWarnings("unchecked")
    private AiProviderPreset mapPreset(String id, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalStateException("预设 " + id + " 格式无效");
        }
        AiProviderPreset preset = new AiProviderPreset();
        preset.setId(id);
        preset.setTier(stringVal(map.get("tier")));
        preset.setDisplayName(stringVal(map.get("displayName")));
        preset.setDisplayNameEn(stringVal(map.get("displayNameEn")));
        preset.setRegion(stringVal(map.get("region")));
        preset.setBaseUrl(stringVal(map.get("baseUrl")));
        preset.setDefaultModel(stringVal(map.get("defaultModel")));
        preset.setModels(stringList(map.get("models")));
        Object apiKeyRequired = map.get("apiKeyRequired");
        preset.setApiKeyRequired(apiKeyRequired == null || Boolean.TRUE.equals(apiKeyRequired));
        preset.setApiKeyPlaceholder(stringVal(map.get("apiKeyPlaceholder")));
        preset.setDocUrl(stringVal(map.get("docUrl")));
        preset.setTags(stringList(map.get("tags")));
        preset.setRecommendedFor(stringList(map.get("recommendedFor")));
        preset.setQualityTier(stringVal(map.get("qualityTier")));
        preset.setNotes(stringVal(map.get("notes")));
        Object deprecated = map.get("deprecated");
        if (deprecated instanceof Boolean b) {
            preset.setDeprecated(b);
        }
        preset.setSuccessor(stringVal(map.get("successor")));
        return preset;
    }

    private static String stringVal(Object o) {
        return o == null ? null : o.toString();
    }

    private static List<String> stringList(Object o) {
        if (!(o instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(item.toString());
            }
        }
        return out;
    }
}
