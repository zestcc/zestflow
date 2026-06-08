package com.zestflow.executor.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 向量相似度（TF-IDF / Embedding 共用）。
 */
final class ExecutorVectorMath {

    private static final Pattern TOKEN = Pattern.compile("[a-zA-Z0-9\\u4e00-\\u9fa5]+");

    private ExecutorVectorMath() {
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
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens.toArray(new String[0]);
    }
}
