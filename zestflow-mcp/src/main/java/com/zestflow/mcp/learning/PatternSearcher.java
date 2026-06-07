package com.zestflow.mcp.learning;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pattern 检索 — 平台 + 项目合并，供 plan_chain 注入经验。
 */
public class PatternSearcher {

    private final PlatformPatternCatalog platform;
    private final PatternStore projectStore;

    public PatternSearcher(Path projectRoot) {
        this.platform = new PlatformPatternCatalog();
        this.projectStore = new PatternStore(projectRoot);
    }

    public PatternSearcher(PlatformPatternCatalog platform, PatternStore projectStore) {
        this.platform = platform;
        this.projectStore = projectStore;
    }

    public List<PatternDocument> search(String query, int limit) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<PatternDocument> all = new ArrayList<>(platform.listAll());
        try {
            all.addAll(projectStore.listAll());
        } catch (Exception ignored) {
            // project patterns optional
        }
        return all.stream()
                .filter(p -> matches(p, q))
                .sorted(Comparator.comparingDouble(PatternDocument::confidenceScore).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    private static boolean matches(PatternDocument p, String q) {
        if (q.isBlank()) {
            return true;
        }
        if (contains(p.title(), q) || contains(p.feature(), q) || contains(p.markdown(), q)) {
            return true;
        }
        if (p.tags() != null) {
            for (String tag : p.tags()) {
                if (contains(tag, q)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(String text, String q) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(q);
    }
}
