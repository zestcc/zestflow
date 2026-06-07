package com.zestflow.admin.ai;

import com.zestflow.admin.service.TenantAppContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderPresetRegistryTest {

    @Mock private AiProviderDictLoader dictLoader;
    @Mock private TenantAppContext tenantAppContext;

    private AiProviderPresetRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AiProviderPresetRegistry(dictLoader, tenantAppContext);
        when(tenantAppContext.getCurrentTenantId()).thenReturn(1L);
    }

    @Test
    void listPresets_delegatesToDictLoader() {
        AiProviderPreset deepseek = new AiProviderPreset();
        deepseek.setId("deepseek");
        deepseek.setDisplayName("DeepSeek");
        deepseek.setTier("A");
        deepseek.setDefaultModel("deepseek-chat");
        deepseek.setModels(List.of("deepseek-chat"));
        when(dictLoader.loadPresets(1L)).thenReturn(List.of(deepseek));
        when(dictLoader.getById(1L, "deepseek")).thenReturn(Optional.of(deepseek));
        when(dictLoader.getById(1L, "unknown")).thenReturn(Optional.empty());

        assertThat(registry.listPresets()).hasSize(1);
        assertThat(registry.getById("deepseek")).isPresent();
        assertThat(registry.getById("unknown")).isEmpty();
    }

    @Test
    void getById_found() {
        AiProviderPreset preset = new AiProviderPreset();
        preset.setId("ollama");
        when(dictLoader.getById(1L, "ollama")).thenReturn(Optional.of(preset));

        assertThat(registry.getById("ollama")).isPresent();
    }
}
