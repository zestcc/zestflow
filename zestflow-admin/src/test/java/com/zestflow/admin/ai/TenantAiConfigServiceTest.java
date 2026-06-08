package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.ai.model.dto.AiTenantConfigSaveDTO;
import com.zestflow.admin.ai.model.entity.AiTenantConfigPO;
import com.zestflow.admin.ai.model.vo.AiTenantConfigVO;
import com.zestflow.admin.ai.repository.AiTenantConfigMapper;
import com.zestflow.admin.config.AiPlatformConfig;
import com.zestflow.admin.service.TenantAppContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantAiConfigServiceTest {

    @Mock private AiTenantConfigMapper configMapper;
    @Mock private AiProviderPresetRegistry presetRegistry;
    @Mock private AiApiKeyCipher apiKeyCipher;
    @Mock private TenantAppContext tenantAppContext;

    private AiPlatformConfig aiPlatformConfig;
    private TenantAiConfigService service;

    @BeforeEach
    void setUp() {
        AiProperties yaml = new AiProperties();
        yaml.setEnabled(true);
        yaml.setDefaultPreset("deepseek");
        aiPlatformConfig = AiPlatformConfigTestFixtures.fromYaml(yaml);
        service = new TenantAiConfigService(
                configMapper, presetRegistry, apiKeyCipher, aiPlatformConfig, tenantAppContext);

        AiProviderPreset deepseek = new AiProviderPreset();
        deepseek.setId("deepseek");
        deepseek.setBaseUrl("https://api.deepseek.com");
        deepseek.setDefaultModel("deepseek-chat");
        deepseek.setApiKeyRequired(true);
        lenient().when(presetRegistry.getById("deepseek")).thenReturn(Optional.of(deepseek));
        lenient().when(presetRegistry.requireById("deepseek")).thenReturn(deepseek);
        lenient().when(apiKeyCipher.encrypt("sk-secret")).thenReturn("enc-value");
        lenient().when(apiKeyCipher.decrypt("enc-value")).thenReturn("sk-secret");
    }

    @Test
    void saveTenantConfig_insertNew_shouldEncryptApiKey() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AiTenantConfigSaveDTO dto = new AiTenantConfigSaveDTO();
        dto.setEnabled(true);
        dto.setPreset("deepseek");
        dto.setApiKey("sk-secret");

        AiTenantConfigVO vo = service.saveTenantConfig(1L, dto);

        ArgumentCaptor<AiTenantConfigPO> captor = ArgumentCaptor.forClass(AiTenantConfigPO.class);
        verify(configMapper).insert(captor.capture());
        assertThat(captor.getValue().getApiKeyEnc()).isEqualTo("enc-value");
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(vo.isApiKeyConfigured()).isTrue();
    }

    @Test
    void resolveEffectiveConfig_shouldUsePresetDefaults() {
        AiTenantConfigPO po = new AiTenantConfigPO();
        po.setTenantId(1L);
        po.setEnabled(true);
        po.setPreset("deepseek");
        po.setApiKeyEnc("enc-value");
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(po);

        TenantAiConfigService.EffectiveAiConfig config = service.resolveEffectiveConfig(1L);

        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.model()).isEqualTo("deepseek-chat");
        assertThat(config.apiKey()).isEqualTo("sk-secret");
        assertThat(config.ready()).isTrue();
    }

    @Test
    void isCopilotEnabledForTenant_whenDisabledGlobally_returnsFalse() {
        AiProperties yaml = new AiProperties();
        yaml.setEnabled(false);
        aiPlatformConfig = AiPlatformConfigTestFixtures.fromYaml(yaml);
        service = new TenantAiConfigService(
                configMapper, presetRegistry, apiKeyCipher, aiPlatformConfig, tenantAppContext);

        assertThat(service.isCopilotEnabledForTenant(1L)).isFalse();
    }

    @Test
    void resolveForTest_blankApiKey_shouldUseSavedKey() {
        AiTenantConfigPO po = new AiTenantConfigPO();
        po.setTenantId(1L);
        po.setEnabled(true);
        po.setPreset("deepseek");
        po.setApiKeyEnc("enc-value");
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(po);

        AiTenantConfigSaveDTO override = new AiTenantConfigSaveDTO();
        override.setPreset("deepseek");
        override.setModel("deepseek-v4-flash");

        TenantAiConfigService.EffectiveAiConfig config = service.resolveForTest(1L, override);

        assertThat(config.apiKey()).isEqualTo("sk-secret");
        assertThat(config.model()).isEqualTo("deepseek-v4-flash");
        assertThat(config.ready()).isTrue();
    }

    @Test
    void getTenantConfig_shouldMaskApiKey() {
        AiTenantConfigPO po = new AiTenantConfigPO();
        po.setEnabled(true);
        po.setPreset("deepseek");
        po.setApiKeyEnc("enc-value");
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(po);

        AiTenantConfigVO vo = service.getTenantConfig(1L);

        assertThat(vo.getApiKeyMasked()).contains("****");
        assertThat(vo.isApiKeyConfigured()).isTrue();
    }
}
