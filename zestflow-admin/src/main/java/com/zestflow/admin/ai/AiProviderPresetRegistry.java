package com.zestflow.admin.ai;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * AI 提供商注册表：委托字典级联 {@link AiProviderDictLoader}，不再单独维护内存/yaml 缓存。
 */
@Component
@RequiredArgsConstructor
public class AiProviderPresetRegistry {

    private final AiProviderDictLoader dictLoader;
    private final TenantAppContext tenantAppContext;

    public String getVersion() {
        return dictLoader.getRegistryVersion();
    }

    public List<AiProviderPreset> listPresets() {
        return dictLoader.loadPresets(tenantAppContext.getCurrentTenantId());
    }

    public Optional<AiProviderPreset> getById(String id) {
        return dictLoader.getById(tenantAppContext.getCurrentTenantId(), id);
    }

    public AiProviderPreset requireById(String id) {
        return getById(id).orElseThrow(() -> new BizException(ErrorCode.AI_PRESET_NOT_FOUND));
    }
}
