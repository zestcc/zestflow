package com.zestflow.admin.ai;

import java.util.List;

/**
 * OpenAI 兼容 LLM 客户端抽象
 */
public interface AiChatClient {

    /**
     * 发送聊天补全请求
     *
     * @param messages 消息列表
     * @param options  连接与生成参数
     * @return 助手回复文本
     */
    String chat(List<ChatMessage> messages, AiChatOptions options);

    /** 聊天消息 */
    record ChatMessage(String role, String content) {}

    /** 聊天选项 */
    record AiChatOptions(
            String baseUrl,
            String apiKey,
            String model,
            int timeoutMs,
            int maxTokens,
            double temperature,
            boolean jsonMode
    ) {}
}
