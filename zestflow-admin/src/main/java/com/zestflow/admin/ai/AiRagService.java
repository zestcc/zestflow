package com.zestflow.admin.ai;

import com.zestflow.admin.config.AiPlatformConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 检索：平台 classpath 知识 + 租户 DB/目录文档，hybrid 向量检索。
 */
@Slf4j
@Service
public class AiRagService {

    private final AiPlatformConfig aiPlatformConfig;
    private final AiEmbeddingClient embeddingClient;
    private final TenantAiConfigService tenantAiConfigService;
    private final AiRagDocumentService ragDocumentService;

    private final AiRagIndexEngine globalIndex = new AiRagIndexEngine();
    private final Map<Long, AiRagIndexEngine> tenantIndexes = new ConcurrentHashMap<>();

    public AiRagService(AiPlatformConfig aiPlatformConfig,
                        AiEmbeddingClient embeddingClient,
                        TenantAiConfigService tenantAiConfigService,
                        @Lazy AiRagDocumentService ragDocumentService) {
        this.aiPlatformConfig = aiPlatformConfig;
        this.embeddingClient = embeddingClient;
        this.tenantAiConfigService = tenantAiConfigService;
        this.ragDocumentService = ragDocumentService;
    }

    @PostConstruct
    void loadGlobalIndex() {
        if (!aiPlatformConfig.isRagEnabled()) {
            return;
        }
        List<IndexedChunk> chunks = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:ai-rag/**/*.md");
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                chunks.addAll(AiRagIndexEngine.splitMarkdown(resource.getFilename(), text));
            }
            globalIndex.rebuild(chunks);
            log.info("AI RAG 平台索引已加载 chunks={} mode={}", chunks.size(), aiPlatformConfig.getRagMode());
        } catch (IOException e) {
            log.warn("AI RAG 平台索引加载失败", e);
        }
    }

    public List<String> retrieve(Long tenantId, String appCode, String query, int limit) {
        if (!aiPlatformConfig.isRagEnabled() || !StringUtils.hasText(query)) {
            return List.of();
        }
        int max = limit > 0 ? limit : aiPlatformConfig.getRagMaxChunks();
        String mode = aiPlatformConfig.getRagMode();

        List<RankedHit> hits = new ArrayList<>();
        appendHits(hits, globalIndex.search(query, mode, max, aiPlatformConfig, embeddingClient, tenantAiConfigService), "platform");

        if (tenantId != null) {
            AiRagIndexEngine tenantIndex = tenantIndexes.computeIfAbsent(tenantId,
                    id -> buildTenantIndex(id, appCode));
            appendHits(hits, tenantIndex.search(query, mode, max, aiPlatformConfig, embeddingClient, tenantAiConfigService), "tenant");
        }

        return hits.stream()
                .sorted(Comparator.comparingDouble(RankedHit::score).reversed())
                .limit(max)
                .map(RankedHit::text)
                .toList();
    }

    /** 兼容旧签名：按当前租户检索 */
    public List<String> retrieve(String query, int limit) {
        return retrieve(tenantAiConfigService.getCurrentTenantId(), null, query, limit);
    }

    public void invalidateTenantIndex(Long tenantId) {
        if (tenantId != null) {
            tenantIndexes.remove(tenantId);
        }
    }

    public void warmTenantIndex(Long tenantId) {
        if (tenantId != null) {
            tenantIndexes.put(tenantId, buildTenantIndex(tenantId, null));
        }
    }

    public String retrievalMode() {
        if (!aiPlatformConfig.isRagEnabled()) {
            return "disabled";
        }
        String mode = normalizeMode(aiPlatformConfig.getRagMode());
        if (aiPlatformConfig.isRagUseLlmEmbedding()) {
            return mode + "+embedding";
        }
        return mode;
    }

    public int globalChunkCount() {
        return globalIndex.chunkCount();
    }

    public int tenantChunkCount(Long tenantId) {
        AiRagIndexEngine engine = tenantIndexes.get(tenantId);
        return engine == null ? 0 : engine.chunkCount();
    }

    private AiRagIndexEngine buildTenantIndex(Long tenantId, String appCode) {
        AiRagIndexEngine engine = new AiRagIndexEngine();
        engine.rebuild(ragDocumentService.loadTenantChunks(tenantId, appCode));
        log.info("AI RAG 租户索引已构建 tenantId={} chunks={}", tenantId, engine.chunkCount());
        return engine;
    }

    private static void appendHits(List<RankedHit> target, List<String> snippets, String scope) {
        double weight = "tenant".equals(scope) ? 1.05 : 1.0;
        for (int i = 0; i < snippets.size(); i++) {
            target.add(new RankedHit(snippets.get(i), (snippets.size() - i) * weight));
        }
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

    public record IndexedChunk(String source, String text, float[] llmEmbedding) {}

    public static final class ScoredChunk {
        public final IndexedChunk chunk;
        public double score;
        public final int index;

        public ScoredChunk(IndexedChunk chunk, double score, int index) {
            this.chunk = chunk;
            this.score = score;
            this.index = index;
        }
    }

    private record RankedHit(String text, double score) {}
}
