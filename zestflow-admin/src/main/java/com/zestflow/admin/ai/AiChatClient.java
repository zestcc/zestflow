package com.zestflow.admin.ai;

import java.util.List;
import java.util.function.Consumer;

/**
 * OpenAI 兼容 LLM 客户端抽象
 */
public interface AiChatClient {

    /**
     * 发送聊天补全请求（阻塞）
     */
    String chat(List<ChatMessage> messages, AiChatOptions options);

    /**
     * 流式聊天补全；回调增量；返回完整拼接文本（content，不含 reasoning 前缀）
     */
    default String chatStream(List<ChatMessage> messages, AiChatOptions options, StreamHandlers handlers) {
        return chat(messages, options);
    }

    /** 流式增量处理器 */
    interface StreamHandlers {
        default void onReasoningDelta(String delta) { }
        default void onContentDelta(String delta) { }
        static StreamHandlers of(Consumer<String> reasoning, Consumer<String> content) {
            return new StreamHandlers() {
                @Override public void onReasoningDelta(String delta) {
                    if (reasoning != null && delta != null && !delta.isEmpty()) {
                        reasoning.accept(delta);
                    }
                }
                @Override public void onContentDelta(String delta) {
                    if (content != null && delta != null && !delta.isEmpty()) {
                        content.accept(delta);
                    }
                }
            };
        }
    }

    record ChatMessage(String role, String content) {}

    record AiChatOptions(
            String baseUrl,
            String apiKey,
            String model,
            int timeoutMs,
            int maxTokens,
            double temperature,
            boolean jsonMode,
            boolean stream
    ) {
        public AiChatOptions(String baseUrl, String apiKey, String model,
                             int timeoutMs, int maxTokens, double temperature, boolean jsonMode) {
            this(baseUrl, apiKey, model, timeoutMs, maxTokens, temperature, jsonMode, false);
        }
    }
}
