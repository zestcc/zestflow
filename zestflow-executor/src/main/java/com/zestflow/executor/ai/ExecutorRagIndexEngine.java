package com.zestflow.executor.ai;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hybrid RAG 检索：关键词 + TF-IDF + 可选 OpenAI 兼容 Embedding 重排（对标 Admin {@link com.zestflow.admin.ai.AiRagIndexEngine}）。
 */
public final class ExecutorRagIndexEngine {

    private final ExecutorTfIdfVectorizer tfIdf = new ExecutorTfIdfVectorizer();

    public List<ExecutorRagChunk> search(List<ExecutorRagChunk> corpus,
                                         String query,
                                         ExecutorAiProperties props,
                                         ExecutorOpenAiClient openAiClient) {
        if (corpus == null || corpus.isEmpty() || !StringUtils.hasText(query)) {
            return List.of();
        }
        int cap = Math.max(1, Math.min(props.getRagMaxChunks(), 10));
        List<String> texts = corpus.stream().map(ExecutorRagChunk::text).toList();
        tfIdf.fit(texts);

        List<String> tokens = tokenize(query);
        float[] queryVector = tfIdf.vectorize(query);
        String mode = normalizeMode(props.getRagMode());

        List<Scored> ranked = new ArrayList<>(corpus.size());
        for (int i = 0; i < corpus.size(); i++) {
            ExecutorRagChunk chunk = corpus.get(i);
            double keywordScore = keywordScore(chunk.text(), tokens);
            double vectorScore = ExecutorVectorMath.cosineSimilarity(queryVector, tfIdf.documentVector(i));
            double score = combineScore(mode, keywordScore, vectorScore);
            if (score > 0) {
                ranked.add(new Scored(chunk, score, i));
            }
        }
        ranked.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());

        if (props.embeddingReady() && openAiClient != null && !ranked.isEmpty()) {
            rerankWithEmbeddings(query, ranked, props, openAiClient);
            ranked.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());
        }

        return ranked.stream()
                .limit(cap)
                .map(s -> new ExecutorRagChunk(s.chunk.id(), s.chunk.text(), s.score, s.chunk.embedding()))
                .toList();
    }

    private void rerankWithEmbeddings(String query,
                                        List<Scored> ranked,
                                        ExecutorAiProperties props,
                                        ExecutorOpenAiClient openAiClient) {
        try {
            int candidateLimit = Math.min(ranked.size(), props.getRagEmbeddingCandidateLimit());
            List<String> candidateTexts = new ArrayList<>(candidateLimit);
            for (int i = 0; i < candidateLimit; i++) {
                candidateTexts.add(ranked.get(i).chunk.text());
            }
            List<float[]> vectors = openAiClient.embedBatch(candidateTexts, props.getEmbeddingModel(), props);
            float[] queryEmbedding = openAiClient.embed(query, props.getEmbeddingModel(), props);
            if (queryEmbedding.length == 0) {
                return;
            }
            for (int i = 0; i < candidateLimit && i < vectors.size(); i++) {
                float[] chunkEmbedding = vectors.get(i);
                if (chunkEmbedding == null || chunkEmbedding.length == 0) {
                    continue;
                }
                double embedScore = ExecutorVectorMath.cosineSimilarity(queryEmbedding, chunkEmbedding);
                ranked.get(i).score = ranked.get(i).score * 0.55 + embedScore * 0.45;
            }
        } catch (Exception ignored) {
            // 重排失败不影响主检索
        }
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

    private static double keywordScore(String text, List<String> tokens) {
        String lower = text.toLowerCase(Locale.ROOT);
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
        Matcher matcher = Pattern.compile("[a-zA-Z0-9\\u4e00-\\u9fa5]+")
                .matcher(query.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                out.add(token);
            }
        }
        return out;
    }

    private static final class Scored {
        private final ExecutorRagChunk chunk;
        private double score;
        private final int index;

        private Scored(ExecutorRagChunk chunk, double score, int index) {
            this.chunk = chunk;
            this.score = score;
            this.index = index;
        }
    }
}
