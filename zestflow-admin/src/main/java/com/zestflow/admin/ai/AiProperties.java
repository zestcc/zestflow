package com.zestflow.admin.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI Copilot 全局配置
 */
@Data
@ConfigurationProperties(prefix = "zestflow.ai")
public class AiProperties {

    /** 全局 Copilot 开关 */
    private boolean enabled = true;

    /** 默认提供商预设 ID */
    private String defaultPreset = "deepseek";

    /** LLM 请求超时（毫秒） */
    private int timeoutMs = 60_000;

    /** 最大输出 token */
    private int maxTokens = 4096;

    /** 采样温度 */
    private double temperature = 0.2;

    /** 是否对 Prompt 中的 PII 脱敏 */
    private boolean piiMask = true;

    /** Validator 失败后的最大修复轮次 */
    private int repairMaxRounds = 2;

    /** 启动时为默认租户写入 AI 配置（Ollama 或 env-keys 中的免费 Key） */
    private boolean tenantAutoInit = true;

    /** 预设 ID → 环境变量 API Key（租户未配置 Key 时的兜底） */
    private java.util.Map<String, String> envKeys = new java.util.HashMap<>();

    /** 是否启用轻量 RAG（classpath ai-rag 文档检索） */
    private boolean ragEnabled = true;

    /** RAG 注入 Prompt 的最大片段数 */
    private int ragMaxChunks = 3;
}
