package com.zestflow.admin.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleEmbeddingClientTest {

    private final OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient();

    @Test
    void parseEmbeddings_shouldReadVectors() {
        String json = """
                {
                  "data": [
                    {"index": 0, "embedding": [0.1, 0.2, 0.3]},
                    {"index": 1, "embedding": [0.4, 0.5]}
                  ]
                }
                """;
        List<float[]> vectors = OpenAiCompatibleEmbeddingClient.parseEmbeddings(json, 2);
        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(vectors.get(1)).containsExactly(0.4f, 0.5f);
    }
}
