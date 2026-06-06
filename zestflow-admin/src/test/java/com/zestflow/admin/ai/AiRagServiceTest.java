package com.zestflow.admin.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiRagServiceTest {

    private final AiRagService ragService = new AiRagService(
            ragProperties(),
            mock(AiEmbeddingClient.class),
            mock(TenantAiConfigService.class)
    );

    @Test
    void retrieve_shouldFindAviatorSnippet() {
        ragService.loadIndex();
        var hits = ragService.retrieve("Aviator chainCtx 表达式", 3);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0)).containsIgnoringCase("chainCtx");
    }

    @Test
    void retrieve_vectorMode_shouldStillFindRelevantChunk() {
        AiProperties props = ragProperties();
        props.setRagMode("vector");
        AiRagService vectorRag = new AiRagService(
                props,
                mock(AiEmbeddingClient.class),
                mock(TenantAiConfigService.class)
        );
        vectorRag.loadIndex();
        var hits = vectorRag.retrieve("Aviator chainCtx 表达式", 3);
        assertThat(hits).isNotEmpty();
    }

    @Test
    void retrievalMode_shouldExposeHybridByDefault() {
        ragService.loadIndex();
        assertThat(ragService.retrievalMode()).isEqualTo("hybrid");
    }

    private static AiProperties ragProperties() {
        AiProperties props = new AiProperties();
        props.setRagEnabled(true);
        props.setRagMode("hybrid");
        props.setRagMaxChunks(3);
        return props;
    }
}
