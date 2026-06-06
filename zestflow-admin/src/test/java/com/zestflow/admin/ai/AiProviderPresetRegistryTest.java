package com.zestflow.admin.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderPresetRegistryTest {

    @Test
    void load_shouldContainTierAPresets() {
        AiProviderPresetRegistry registry = new AiProviderPresetRegistry();
        registry.load();

        assertThat(registry.getVersion()).isEqualTo("2026-06");
        List<AiProviderPreset> presets = registry.listPresets();
        assertThat(presets).hasSizeGreaterThanOrEqualTo(24);

        long tierA = presets.stream().filter(p -> "A".equals(p.getTier())).count();
        assertThat(tierA).isEqualTo(8);

        Optional<AiProviderPreset> deepseek = registry.getById("deepseek");
        assertThat(deepseek).isPresent();
        assertThat(deepseek.get().getDefaultModel()).isEqualTo("deepseek-chat");
        assertThat(deepseek.get().getBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(deepseek.get().isApiKeyRequired()).isTrue();

        Optional<AiProviderPreset> ollama = registry.getById("ollama");
        assertThat(ollama).isPresent();
        assertThat(ollama.get().isApiKeyRequired()).isFalse();

        Optional<AiProviderPreset> custom = registry.getById("custom");
        assertThat(custom).isPresent();
        assertThat(custom.get().getTier()).isEqualTo("B");
    }

    @Test
    void getById_unknown_returnsEmpty() {
        AiProviderPresetRegistry registry = new AiProviderPresetRegistry();
        registry.load();
        assertThat(registry.getById("nonexistent")).isEmpty();
    }
}
