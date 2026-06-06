package com.zestflow.admin.ai;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * RAG 检索：关键词 + 本地 TF-IDF 向量 + 可选 LLM Embedding 重排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRagService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s\\p{Punct}]+");

    private final AiProperties aiProperties;
    private final AiEmbeddingClient embeddingClient;
    private final TenantAiConfigService tenantAiConfigService;
    private final List<IndexedChunk> chunks = new ArrayList<>();
    private final AiTfIdfVectorizer tfIdf = new AiTfIdfVectorizer();
    private volatile boolean llmEmbeddingsReady;

    @PostConstruct
    void loadIndex() {
        if (!aiProperties.isRagEnabled()) {
            return;
        }
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:ai-rag/**/*.md");
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                splitIntoChunks(resource.getFilename(), text);
            }
            rebuildTfIdfIndex();
            log.info("AI RAG 索引已加载 chunks={} mode={}", chunks.size(), aiProperties.getRagMode());
        } catch (IOException e) {
            log.warn("AI RAG 索引加载失败", e);
        }
    }

    public List<String> retrieve(String query, int limit) {
        if (!aiProperties.isRagEnabled() || !StringUtils.hasText(query) || chunks.isEmpty()) {
            return List.of();
        }
        int max = limit > 0 ? limit : aiProperties.getRagMaxChunks();
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }

        String mode = normalizeMode(aiProperties.getRagMode());
        float[] queryVector = tfIdf.vectorize(query);

        List<ScoredChunk> ranked = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            IndexedChunk chunk = chunks.get(i);
            double keywordScore = keywordScore(chunk, tokens);
            double vectorScore = AiVectorMath.cosineSimilarity(queryVector, tfIdf.documentVector(i));
            double score = combineScore(mode, keywordScore, vectorScore);
            if (score > 0) {
                ranked.add(new ScoredChunk(chunk, score, i));
            }
        }

        ranked.sort(Comparator.comparingDouble((ScoredChunk sc) -> sc.score).reversed());

        if (aiProperties.isRagUseLlmEmbedding() && !ranked.isEmpty()) {
            rerankWithLlmEmbeddings(query, ranked);
            ranked.sort(Comparator.comparingDouble((ScoredChunk sc) -> sc.score).reversed());
        }

        return ranked.stream()
                .limit(max)
                .map(sc -> sc.chunk.source() + ": " + truncate(sc.chunk.text()))
                .toList();
    }

    public String retrievalMode() {
        if (!aiProperties.isRagEnabled()) {
            return "disabled";
        }
        String mode = normalizeMode(aiProperties.getRagMode());
        if (aiProperties.isRagUseLlmEmbedding()) {
            return mode + "+embedding";
        }
        return mode;
    }

    private void rerankWithLlmEmbeddings(String query, List<ScoredChunk> ranked) {
        try {
            TenantAiConfigService.EffectiveAiConfig config = tenantAiConfigService.resolveEffectiveConfig(
                    tenantAiConfigService.getCurrentTenantId());
            if (!config.ready()) {
                return;
            }
            ensureLlmEmbeddings(config);
            float[] queryEmbedding = embeddingClient.embed(query, buildEmbeddingOptions(config));
            if (queryEmbedding.length == 0) {
                return;
            }
            int candidateLimit = Math.min(ranked.size(), aiProperties.getRagEmbeddingCandidateLimit());
            for (int i = 0; i < candidateLimit; i++) {
                ScoredChunk sc = ranked.get(i);
                float[] chunkEmbedding = sc.chunk.llmEmbedding();
                if (chunkEmbedding == null || chunkEmbedding.length == 0) {
                    continue;
                }
                double embedScore = AiVectorMath.cosineSimilarity(queryEmbedding, chunkEmbedding);
                sc.score = sc.score * 0.55 + embedScore * 0.45;
            }
        } catch (Exception e) {
            log.debug("LLM Embedding 重排跳过: {}", e.getMessage());
        }
    }

    private synchronized void ensureLlmEmbeddings(TenantAiConfigService.EffectiveAiConfig config) {
        if (llmEmbeddingsReady) {
            return;
        }
        List<String> texts = chunks.stream().map(c -> c.text).toList();
        List<float[]> vectors = embeddingClient.embedBatch(texts, buildEmbeddingOptions(config));
        for (int i = 0; i < chunks.size() && i < vectors.size(); i++) {
            IndexedChunk old = chunks.get(i);
            chunks.set(i, new IndexedChunk(old.source, old.text, vectors.get(i)));
        }
        llmEmbeddingsReady = true;
        log.info("AI RAG LLM Embedding 索引已构建 chunks={}", chunks.size());
    }

    private AiChatClient.AiChatOptions buildEmbeddingOptions(TenantAiConfigService.EffectiveAiConfig config) {
        String model = StringUtils.hasText(aiProperties.getRagEmbeddingModel())
                ? aiProperties.getRagEmbeddingModel()
                : config.model();
        return new AiChatClient.AiChatOptions(
                config.baseUrl(),
                config.apiKey(),
                model,
                aiProperties.getTimeoutMs(),
                aiProperties.getMaxTokens(),
                aiProperties.getTemperature(),
                false
        );
    }

    private static double combineScore(String mode, double keywordScore, double vectorScore) {
        return switch (mode) {
            case "keyword" -> keywordScore;
            case "vector" -> vectorScore;
            default -> keywordScore * 0.35 + vectorScore * 0.65;
        };
    }

    private static String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return "hybrid";
        }
        String lower = mode.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "keyword", "vector", "hybrid" -> lower;
            default -> "hybrid";
        };
    }

    private void rebuildTfIdfIndex() {
        tfIdf.fit(chunks.stream().map(c -> c.text).toList());
    }

    private void splitIntoChunks(String source, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        String[] parts = text.split("(?m)^## ");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() < 40) {
                continue;
            }
            if (!trimmed.startsWith("#")) {
                trimmed = "## " + trimmed;
            }
            chunks.add(new IndexedChunk(source == null ? "doc" : source, trimmed, null));
        }
    }

    private static double keywordScore(IndexedChunk chunk, List<String> tokens) {
        String lower = chunk.text().toLowerCase(Locale.ROOT);
        double s = 0;
        for (String token : tokens) {
            if (lower.contains(token)) {
                s += token.length() >= 4 ? 3 : 1;
            }
        }
        return s;
    }

    private static List<String> tokenize(String query) {
        List<String> out = new ArrayList<>();
        for (String raw : TOKEN_SPLIT.split(query.toLowerCase(Locale.ROOT))) {
            if (raw.length() >= 2) {
                out.add(raw);
            }
        }
        return out;
    }

    private static String truncate(String text) {
        if (text.length() <= 800) {
            return text;
        }
        return text.substring(0, 800) + "...";
    }

    record IndexedChunk(String source, String text, float[] llmEmbedding) {}
    static final class ScoredChunk {
        final IndexedChunk chunk;
        double score;
        final int index;

        ScoredChunk(IndexedChunk chunk, double score, int index) {
            this.chunk = chunk;
            this.score = score;
            this.index = index;
        }
    }
}
