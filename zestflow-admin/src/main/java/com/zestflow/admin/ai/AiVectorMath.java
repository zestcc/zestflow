package com.zestflow.admin.ai;

import java.util.Locale;

/**
 * 向量相似度工具（TF-IDF / Embedding 共用）。
 */
final class AiVectorMath {

    private AiVectorMath() {
    }

    static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA <= 0 || normB <= 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    static String[] tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new String[0];
        }
        return text.toLowerCase(Locale.ROOT)
                .split("[\\s\\p{Punct}]+");
    }
}
