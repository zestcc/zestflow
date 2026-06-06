package com.zestflow.admin.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiRagServiceTest {

    private final AiProperties props = ragProperties();
    private final AiRagDocumentService docService = mock(AiRagDocumentService.class);
    private final AiRagService ragService = new AiRagService(
            props,
            mock(AiEmbeddingClient.class),
            mock(TenantAiConfigService.class),
            docService
    );

    @Test
    void loadGlobalIndex_shouldFindAviatorSnippet() {
        ragService.loadGlobalIndex();
        var hits = ragService.retrieve(1L, "demo-app", "Aviator chainCtx 表达式", 3);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0)).containsIgnoringCase("chainCtx");
    }

    @Test
    void retrievalMode_shouldExposeHybridByDefault() {
        ragService.loadGlobalIndex();
        assertThat(ragService.retrievalMode()).isEqualTo("hybrid");
    }

    private static AiProperties ragProperties() {
        AiProperties p = new AiProperties();
        p.setRagEnabled(true);
        p.setRagMode("hybrid");
        p.setRagMaxChunks(3);
        return p;
    }
}
