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
 * 轻量 RAG：索引 classpath 下 ai-rag 文档片段，按关键词检索注入 Copilot Prompt。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRagService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s\\p{Punct}]+");

    private final AiProperties aiProperties;
    private final List<RagChunk> chunks = new ArrayList<>();

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
            log.info("AI RAG 索引已加载 chunks={}", chunks.size());
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

        return chunks.stream()
                .map(c -> new ScoredChunk(c, score(c, tokens)))
                .filter(sc -> sc.score > 0)
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
                .limit(max)
                .map(sc -> sc.chunk.source() + ": " + truncate(sc.chunk.text()))
                .toList();
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
            chunks.add(new RagChunk(source == null ? "doc" : source, trimmed));
        }
    }

    private static int score(RagChunk chunk, List<String> tokens) {
        String lower = chunk.text().toLowerCase(Locale.ROOT);
        int s = 0;
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

    record RagChunk(String source, String text) {}
    record ScoredChunk(RagChunk chunk, int score) {}
}
