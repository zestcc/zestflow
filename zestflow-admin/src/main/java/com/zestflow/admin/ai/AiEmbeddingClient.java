package com.zestflow.admin.ai;

import java.util.List;

/**
 * OpenAI 兼容 Embedding 客户端。
 */
public interface AiEmbeddingClient {

    float[] embed(String text, AiChatClient.AiChatOptions options);

    List<float[]> embedBatch(List<String> texts, AiChatClient.AiChatOptions options);
}
