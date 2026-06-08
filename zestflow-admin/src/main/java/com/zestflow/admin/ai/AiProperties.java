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

    /** 多轮对话注入的最大历史消息条数 */
    private int contextMaxMessages = 20;

    /** 多轮对话注入的最大字符总量 */
    private int contextMaxChars = 12_000;

    /** SSE 长连接超时（毫秒） */
    private int sseTimeoutMs = 180_000;

    /** 异步 Job 最大并发（线程池上限参考） */
    private int jobMaxConcurrent = 8;

    /** 会话列表默认条数上限 */
    private int sessionListMax = 50;

    /** 启动时为默认租户写入 AI 配置（Ollama 或 env-keys 中的免费 Key） */
    private boolean tenantAutoInit = true;

    /** 预设 ID → 环境变量 API Key（租户未配置 Key 时的兜底） */
    private java.util.Map<String, String> envKeys = new java.util.HashMap<>();

    /** 是否启用轻量 RAG（classpath ai-rag 文档检索） */
    private boolean ragEnabled = true;

    /** RAG 检索模式：keyword | vector | hybrid */
    private String ragMode = "hybrid";

    /** 是否使用 LLM Embedding API 对候选片段重排（需租户 AI 配置可用） */
    private boolean ragUseLlmEmbedding = false;

    /** Embedding 模型（留空则沿用租户 chat 模型） */
    private String ragEmbeddingModel = "";

    /** LLM Embedding 重排时的候选上限 */
    private int ragEmbeddingCandidateLimit = 12;

    /** RAG 注入 Prompt 的最大片段数 */
    private int ragMaxChunks = 5;

    /** 租户 RAG 文件目录（{dir}/{tenantId}/*.md） */
    private String ragTenantDataDir = "./data/ai-rag";

    /** 是否扫描租户 RAG 目录 */
    private boolean ragTenantFilesystemEnabled = true;

    /**
     * 学习事件是否自动晋升到租户 RAG（默认关闭；链条知识库主路径在应用端 Executor）。
     */
    private boolean tenantRagAutoPromote = false;

    /** 每租户 DB 文档上限 */
    private int ragTenantMaxDocuments = 200;

    /** 单文档最大字节 */
    private int ragTenantMaxContentBytes = 524_288;

    /** 默认月 Token 配额（0=不限，可被租户配置覆盖） */
    private int defaultMonthlyTokenQuota = 0;
}
