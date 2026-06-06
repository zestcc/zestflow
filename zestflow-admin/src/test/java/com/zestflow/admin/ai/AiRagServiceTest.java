package com.zestflow.admin.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRagServiceTest {

    private final AiRagService ragService = new AiRagService(ragProperties());

    @Test
    void retrieve_shouldFindAviatorSnippet() {
        ragService.loadIndex();
        var hits = ragService.retrieve("Aviator chainCtx 表达式", 3);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0)).containsIgnoringCase("chainCtx");
    }

    private static AiProperties ragProperties() {
        AiProperties props = new AiProperties();
        props.setRagEnabled(true);
        props.setRagMaxChunks(3);
        return props;
    }
}
