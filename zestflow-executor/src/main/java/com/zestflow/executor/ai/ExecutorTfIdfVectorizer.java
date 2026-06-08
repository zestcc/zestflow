package com.zestflow.executor.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 TF-IDF（无需外部 Embedding 服务）。
 */
final class ExecutorTfIdfVectorizer {

    private final List<String> vocabulary = new ArrayList<>();
    private final Map<String, Integer> termIndex = new HashMap<>();
    private final Map<String, Integer> docFreq = new HashMap<>();
    private final List<float[]> documentVectors = new ArrayList<>();
    private int documentCount;

    void fit(List<String> documents) {
        vocabulary.clear();
        termIndex.clear();
        docFreq.clear();
        documentVectors.clear();
        documentCount = documents == null ? 0 : documents.size();
        if (documentCount == 0) {
            return;
        }

        List<Map<String, Integer>> termCounts = new ArrayList<>();
        for (String doc : documents) {
            Map<String, Integer> counts = new HashMap<>();
            for (String token : ExecutorVectorMath.tokenize(doc)) {
                if (token.length() < 2) {
                    continue;
                }
                counts.merge(token, 1, Integer::sum);
            }
            for (String term : counts.keySet()) {
                docFreq.merge(term, 1, Integer::sum);
            }
            termCounts.add(counts);
        }

        docFreq.keySet().stream().sorted().forEach(term -> {
            termIndex.put(term, vocabulary.size());
            vocabulary.add(term);
        });

        for (Map<String, Integer> counts : termCounts) {
            documentVectors.add(toVector(counts));
        }
    }

    float[] vectorize(String text) {
        if (vocabulary.isEmpty()) {
            return new float[0];
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String token : ExecutorVectorMath.tokenize(text)) {
            if (token.length() < 2 || !termIndex.containsKey(token)) {
                continue;
            }
            counts.merge(token, 1, Integer::sum);
        }
        return toVector(counts);
    }

    float[] documentVector(int index) {
        if (index < 0 || index >= documentVectors.size()) {
            return new float[0];
        }
        return documentVectors.get(index);
    }

    private float[] toVector(Map<String, Integer> termCounts) {
        float[] vector = new float[vocabulary.size()];
        if (termCounts.isEmpty()) {
            return vector;
        }
        double maxTf = termCounts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        for (Map.Entry<String, Integer> e : termCounts.entrySet()) {
            Integer idx = termIndex.get(e.getKey());
            if (idx == null) {
                continue;
            }
            double tf = 0.5 + 0.5 * e.getValue() / maxTf;
            double idf = Math.log((documentCount + 1.0) / (docFreq.getOrDefault(e.getKey(), 0) + 1.0)) + 1.0;
            vector[idx] = (float) (tf * idf);
        }
        return vector;
    }
}
