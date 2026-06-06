package com.zestflow.admin.ai;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 可重建的 RAG 索引（关键词 + TF-IDF + 可选 Embedding 重排）。
 */
final class AiRagIndexEngine {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s\\p{Punct}]+");

    private final List<AiRagService.IndexedChunk> chunks = new ArrayList<>();
    private final AiTfIdfVectorizer tfIdf = new AiTfIdfVectorizer();
    private volatile boolean llmEmbeddingsReady;

    void rebuild(List<AiRagService.IndexedChunk> sourceChunks) {
        chunks.clear();
        llmEmbeddingsReady = false;
        if (sourceChunks != null) {
            chunks.addAll(sourceChunks);
        }
        tfIdf.fit(chunks.stream().map(AiRagService.IndexedChunk::text).toList());
    }

    int chunkCount() {
        return chunks.size();
    }

    List<String> search(String query,
                          String ragMode,
                          int limit,
                          AiProperties aiProperties,
                          AiEmbeddingClient embeddingClient,
                          TenantAiConfigService tenantAiConfigService) {
        if (!StringUtils.hasText(query) || chunks.isEmpty()) {
            return List.of();
        }
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }

        String mode = normalizeMode(ragMode);
        float[] queryVector = tfIdf.vectorize(query);
        List<AiRagService.ScoredChunk> ranked = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            AiRagService.IndexedChunk chunk = chunks.get(i);
            double keywordScore = keywordScore(chunk, tokens);
            double vectorScore = AiVectorMath.cosineSimilarity(queryVector, tfIdf.documentVector(i));
            double score = combineScore(mode, keywordScore, vectorScore);
            if (score > 0) {
                ranked.add(new AiRagService.ScoredChunk(chunk, score, i));
            }
        }
        ranked.sort(Comparator.comparingDouble((AiRagService.ScoredChunk sc) -> sc.score).reversed());

        if (aiProperties.isRagUseLlmEmbedding() && !ranked.isEmpty()) {
            rerankWithLlmEmbeddings(query, ranked, aiProperties, embeddingClient, tenantAiConfigService);
            ranked.sort(Comparator.comparingDouble((AiRagService.ScoredChunk sc) -> sc.score).reversed());
        }

        return ranked.stream()
                .limit(limit)
                .map(sc -> sc.chunk.source() + ": " + truncate(sc.chunk.text()))
                .toList();
    }

    static List<AiRagService.IndexedChunk> splitMarkdown(String source, String text) {
        List<AiRagService.IndexedChunk> out = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return out;
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
            out.add(new AiRagService.IndexedChunk(source == null ? "doc" : source, trimmed, null));
        }
        return out;
    }

    private void rerankWithLlmEmbeddings(String query,
                                         List<AiRagService.ScoredChunk> ranked,
                                         AiProperties aiProperties,
                                         AiEmbeddingClient embeddingClient,
                                         TenantAiConfigService tenantAiConfigService) {
        try {
            TenantAiConfigService.EffectiveAiConfig config = tenantAiConfigService.resolveEffectiveConfig(
                    tenantAiConfigService.getCurrentTenantId());
            if (!config.ready()) {
                return;
            }
            ensureLlmEmbeddings(config, aiProperties, embeddingClient);
            float[] queryEmbedding = embeddingClient.embed(query, buildEmbeddingOptions(config, aiProperties));
            if (queryEmbedding.length == 0) {
                return;
            }
            int candidateLimit = Math.min(ranked.size(), aiProperties.getRagEmbeddingCandidateLimit());
            for (int i = 0; i < candidateLimit; i++) {
                AiRagService.ScoredChunk sc = ranked.get(i);
                float[] chunkEmbedding = sc.chunk.llmEmbedding();
                if (chunkEmbedding == null || chunkEmbedding.length == 0) {
                    continue;
                }
                double embedScore = AiVectorMath.cosineSimilarity(queryEmbedding, chunkEmbedding);
                sc.score = sc.score * 0.55 + embedScore * 0.45;
            }
        } catch (Exception ignored) {
            // 重排失败不影响主检索
        }
    }

    private synchronized void ensureLlmEmbeddings(TenantAiConfigService.EffectiveAiConfig config,
                                                  AiProperties aiProperties,
                                                  AiEmbeddingClient embeddingClient) {
        if (llmEmbeddingsReady) {
            return;
        }
        List<String> texts = chunks.stream().map(AiRagService.IndexedChunk::text).toList();
        List<float[]> vectors = embeddingClient.embedBatch(texts, buildEmbeddingOptions(config, aiProperties));
        for (int i = 0; i < chunks.size() && i < vectors.size(); i++) {
            AiRagService.IndexedChunk old = chunks.get(i);
            chunks.set(i, new AiRagService.IndexedChunk(old.source(), old.text(), vectors.get(i)));
        }
        llmEmbeddingsReady = true;
    }

    private static AiChatClient.AiChatOptions buildEmbeddingOptions(
            TenantAiConfigService.EffectiveAiConfig config, AiProperties aiProperties) {
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

    private static double keywordScore(AiRagService.IndexedChunk chunk, List<String> tokens) {
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
}
