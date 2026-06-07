package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.model.entity.DictDataPO;
import com.zestflow.admin.model.entity.DictTypePO;
import com.zestflow.admin.repository.DictDataMapper;
import com.zestflow.admin.repository.DictTypeMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
 * AI 提供商/模型：从字典级联加载（ai_provider + ai_model），首次启动自 yaml 种子写入字典。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiProviderDictLoader {

    public static final String TYPE_PROVIDER = "ai_provider";
    public static final String TYPE_MODEL = "ai_model";
    private static final long TEMPLATE_TENANT_ID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DictTypeMapper dictTypeMapper;
    private final DictDataMapper dictDataMapper;

    private String registryVersion = "unknown";

    @PostConstruct
    void seedFromYamlIfEmpty() {
        ensureDictTypes();
        try (InputStream in = new ClassPathResource("ai-providers.yaml").getInputStream()) {
            Yaml yaml = new Yaml();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                return;
            }
            Object versionObj = root.get("version");
            if (versionObj != null) {
                registryVersion = versionObj.toString();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> presetMap = (Map<String, Object>) root.get("presets");
            if (presetMap == null) {
                return;
            }
            int sort = 1;
            int inserted = 0;
            for (Map.Entry<String, Object> entry : presetMap.entrySet()) {
                AiProviderPreset preset = mapYamlPreset(entry.getKey(), entry.getValue());
                if (!providerExists(TEMPLATE_TENANT_ID, preset.getId())) {
                    insertProvider(TEMPLATE_TENANT_ID, preset, sort);
                    inserted++;
                }
                insertModels(TEMPLATE_TENANT_ID, preset);
                sort++;
            }
            log.info("AI 提供商 yaml 已同步字典 inserted={} total={} version={}",
                    inserted, presetMap.size(), registryVersion);
        } catch (Exception e) {
            log.error("AI 提供商字典种子失败", e);
        }
    }

    public String getRegistryVersion() {
        return registryVersion;
    }

    public List<AiProviderPreset> loadPresets(long tenantId) {
        List<DictDataPO> providers = dictDataMapper.selectList(
                new LambdaQueryWrapper<DictDataPO>()
                        .eq(DictDataPO::getTenantId, tenantId)
                        .eq(DictDataPO::getTypeCode, TYPE_PROVIDER)
                        .eq(DictDataPO::getStatus, 1)
                        .orderByAsc(DictDataPO::getSort));
        if (providers.isEmpty() && tenantId != TEMPLATE_TENANT_ID) {
            return loadPresets(TEMPLATE_TENANT_ID);
        }
        List<DictDataPO> allModels = dictDataMapper.selectList(
                new LambdaQueryWrapper<DictDataPO>()
                        .eq(DictDataPO::getTenantId, tenantId)
                        .eq(DictDataPO::getTypeCode, TYPE_MODEL)
                        .eq(DictDataPO::getStatus, 1)
                        .orderByAsc(DictDataPO::getSort)
                        .orderByDesc(DictDataPO::getDefaultFlag));
        if (allModels.isEmpty() && tenantId != TEMPLATE_TENANT_ID) {
            allModels = dictDataMapper.selectList(
                    new LambdaQueryWrapper<DictDataPO>()
                            .eq(DictDataPO::getTenantId, TEMPLATE_TENANT_ID)
                            .eq(DictDataPO::getTypeCode, TYPE_MODEL)
                            .eq(DictDataPO::getStatus, 1)
                            .orderByAsc(DictDataPO::getSort)
                            .orderByDesc(DictDataPO::getDefaultFlag));
        }

        Map<String, List<DictDataPO>> modelsByProvider = new LinkedHashMap<>();
        for (DictDataPO m : allModels) {
            if (m.getParentValue() == null) {
                continue;
            }
            modelsByProvider.computeIfAbsent(m.getParentValue(), k -> new ArrayList<>()).add(m);
        }

        List<AiProviderPreset> result = new ArrayList<>();
        for (DictDataPO row : providers) {
            result.add(toPreset(row, modelsByProvider.getOrDefault(row.getValue(), List.of())));
        }
        result.sort(Comparator
                .comparing((AiProviderPreset p) -> "A".equals(p.getTier()) ? 0 : 1)
                .thenComparing(AiProviderPreset::getDisplayName, Comparator.nullsLast(String::compareTo)));
        return Collections.unmodifiableList(result);
    }

    public Optional<AiProviderPreset> getById(long tenantId, String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return loadPresets(tenantId).stream()
                .filter(p -> id.equals(p.getId()))
                .findFirst();
    }

    private void ensureDictTypes() {
        ensureType(TYPE_PROVIDER, "AI 提供商");
        ensureType(TYPE_MODEL, "AI 模型");
    }

    private void ensureType(String code, String name) {
        DictTypePO exists = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<DictTypePO>()
                        .eq(DictTypePO::getTenantId, TEMPLATE_TENANT_ID)
                        .eq(DictTypePO::getCode, code));
        if (exists != null) {
            return;
        }
        DictTypePO po = new DictTypePO();
        po.setCode(code);
        po.setName(name);
        po.setStatus(1);
        po.setSort(100);
        po.setTenantId(TEMPLATE_TENANT_ID);
        dictTypeMapper.insert(po);
    }

    private void insertProvider(long tenantId, AiProviderPreset preset, int sort) {
        DictDataPO po = new DictDataPO();
        po.setTypeCode(TYPE_PROVIDER);
        po.setValue(preset.getId());
        po.setLabel(preset.getDisplayName());
        po.setSort(sort);
        po.setStatus(1);
        po.setDefaultFlag(sort == 1 ? 1 : 0);
        po.setTenantId(tenantId);
        po.setTagType("A".equals(preset.getTier()) ? "primary" : "info");
        po.setExtra(buildProviderExtra(preset));
        dictDataMapper.insert(po);
    }

    private void insertModels(long tenantId, AiProviderPreset preset) {
        int i = 1;
        for (String model : preset.getModels()) {
            String storageValue = modelStorageValue(preset.getId(), model);
            Long exists = dictDataMapper.selectCount(
                    new LambdaQueryWrapper<DictDataPO>()
                            .eq(DictDataPO::getTenantId, tenantId)
                            .eq(DictDataPO::getTypeCode, TYPE_MODEL)
                            .eq(DictDataPO::getValue, storageValue));
            if (exists != null && exists > 0) {
                continue;
            }
            DictDataPO po = new DictDataPO();
            po.setTypeCode(TYPE_MODEL);
            po.setParentTypeCode(TYPE_PROVIDER);
            po.setParentValue(preset.getId());
            po.setValue(storageValue);
            po.setLabel(model);
            po.setSort(i++);
            po.setStatus(1);
            po.setDefaultFlag(model.equals(preset.getDefaultModel()) ? 1 : 0);
            po.setTenantId(tenantId);
            dictDataMapper.insert(po);
        }
    }

    private boolean providerExists(long tenantId, String providerId) {
        Long count = dictDataMapper.selectCount(
                new LambdaQueryWrapper<DictDataPO>()
                        .eq(DictDataPO::getTenantId, tenantId)
                        .eq(DictDataPO::getTypeCode, TYPE_PROVIDER)
                        .eq(DictDataPO::getValue, providerId));
        return count != null && count > 0;
    }

    /** 字典 value 需租户内唯一；同名校型跨提供商用 providerId::model 区分，label 仍为 API 模型名。 */
    private static String modelStorageValue(String providerId, String model) {
        return providerId + "::" + model;
    }

    private static String modelDisplayName(DictDataPO row) {
        if (row.getLabel() != null && !row.getLabel().isBlank()) {
            return row.getLabel();
        }
        String value = row.getValue();
        int sep = value != null ? value.indexOf("::") : -1;
        return sep >= 0 ? value.substring(sep + 2) : value;
    }

    private AiProviderPreset toPreset(DictDataPO row, List<DictDataPO> models) {
        AiProviderPreset preset = new AiProviderPreset();
        preset.setId(row.getValue());
        preset.setDisplayName(row.getLabel());
        applyExtra(preset, row.getExtra());
        List<String> modelNames = new ArrayList<>();
        String defaultModel = preset.getDefaultModel();
        for (DictDataPO m : models) {
            String modelName = modelDisplayName(m);
            modelNames.add(modelName);
            if (m.getDefaultFlag() != null && m.getDefaultFlag() == 1) {
                defaultModel = modelName;
            }
        }
        preset.setModels(modelNames);
        if (defaultModel != null) {
            preset.setDefaultModel(defaultModel);
        } else if (!modelNames.isEmpty()) {
            preset.setDefaultModel(modelNames.get(0));
        }
        return preset;
    }

    private static String buildProviderExtra(AiProviderPreset preset) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tier", preset.getTier());
            map.put("region", preset.getRegion());
            map.put("displayNameEn", preset.getDisplayNameEn());
            map.put("baseUrl", preset.getBaseUrl());
            map.put("defaultModel", preset.getDefaultModel());
            map.put("apiKeyRequired", preset.isApiKeyRequired());
            map.put("apiKeyPlaceholder", preset.getApiKeyPlaceholder());
            map.put("docUrl", preset.getDocUrl());
            map.put("tags", preset.getTags());
            map.put("recommendedFor", preset.getRecommendedFor());
            map.put("qualityTier", preset.getQualityTier());
            map.put("notes", preset.getNotes());
            map.put("deprecated", preset.getDeprecated());
            map.put("successor", preset.getSuccessor());
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    private static void applyExtra(AiProviderPreset preset, String extra) {
        if (extra == null || extra.isBlank()) {
            return;
        }
        try {
            Map<String, Object> map = MAPPER.readValue(extra, new TypeReference<>() {});
            if (map.get("tier") != null) preset.setTier(map.get("tier").toString());
            if (map.get("region") != null) preset.setRegion(map.get("region").toString());
            if (map.get("displayNameEn") != null) preset.setDisplayNameEn(map.get("displayNameEn").toString());
            if (map.get("baseUrl") != null) preset.setBaseUrl(map.get("baseUrl").toString());
            if (map.get("defaultModel") != null) preset.setDefaultModel(map.get("defaultModel").toString());
            Object apiKeyRequired = map.get("apiKeyRequired");
            if (apiKeyRequired instanceof Boolean b) {
                preset.setApiKeyRequired(b);
            }
            if (map.get("apiKeyPlaceholder") != null) {
                preset.setApiKeyPlaceholder(map.get("apiKeyPlaceholder").toString());
            }
            if (map.get("docUrl") != null) preset.setDocUrl(map.get("docUrl").toString());
            if (map.get("qualityTier") != null) preset.setQualityTier(map.get("qualityTier").toString());
            if (map.get("notes") != null) preset.setNotes(map.get("notes").toString());
            if (map.get("successor") != null) preset.setSuccessor(map.get("successor").toString());
            if (map.get("tags") instanceof List<?> tags) {
                preset.setTags(tags.stream().map(Object::toString).toList());
            }
            if (map.get("recommendedFor") instanceof List<?> rec) {
                preset.setRecommendedFor(rec.stream().map(Object::toString).toList());
            }
            if (map.get("deprecated") instanceof Boolean d) {
                preset.setDeprecated(d);
            }
        } catch (Exception e) {
            log.warn("解析 AI 提供商 extra 失败 id={}", preset.getId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static AiProviderPreset mapYamlPreset(String id, Object raw) {
        Map<String, Object> map = (Map<String, Object>) raw;
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
