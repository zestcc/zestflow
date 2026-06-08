package com.zestflow.executor.ai;

import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * Executor 侧 AI 配置（LLM suggest + Hybrid RAG），对标 Admin {@code zestflow.ai.*} 子集。
 */
@Data
public class ExecutorAiProperties {

    /** 是否启用 LLM chains/suggest（须配置 base-url + model） */
    private boolean llmEnabled = false;

    /** OpenAI 兼容 API 根地址，如 http://localhost:11434/v1 */
    private String baseUrl = "http://localhost:11434/v1";

    private String apiKey = "";

    /** Chat 模型 */
    private String model = "llama3.2";

    /** Embedding 模型（Ollama: nomic-embed-text 等） */
    private String embeddingModel = "nomic-embed-text";

    private double temperature = 0.2;

    private int maxTokens = 4096;

    private int timeoutMs = 60_000;

    /** 质量门禁 / validate 修复最大轮次 */
    private int repairMaxRounds = 2;

    /** keyword | vector | hybrid */
    private String ragMode = "hybrid";

    /** hybrid 模式下是否用 LLM embedding 重排（须 llmReady） */
    private boolean ragUseEmbedding = true;

    private int ragEmbeddingCandidateLimit = 12;

    private int ragMaxChunks = 8;

    /** LLM 失败或无配置时是否回落 pattern 抽取 */
    private boolean patternFallbackEnabled = true;

    public boolean llmReady() {
        return llmEnabled && StringUtils.hasText(baseUrl) && StringUtils.hasText(model);
    }

    public boolean embeddingReady() {
        return llmReady() && ragUseEmbedding && StringUtils.hasText(embeddingModel);
    }
}
