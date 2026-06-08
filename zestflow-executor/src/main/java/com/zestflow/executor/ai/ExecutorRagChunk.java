package com.zestflow.executor.ai;

/**
 * RAG 检索单元。
 */
public record ExecutorRagChunk(String id, String text, double score, float[] embedding) {

    ExecutorRagChunk(String id, String text, double score) {
        this(id, text, score, null);
    }

    ExecutorRagChunk withEmbedding(float[] embedding) {
        return new ExecutorRagChunk(id, text, score, embedding);
    }
}
