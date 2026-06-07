package com.zestflow.mcp.learning;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * P2 蒸馏 — 将高置信 LearningEvent 聚合为项目 Pattern（对标 Mem0 / LangSmith 策展）。
 */
public class PatternDistiller {

    private final LearningEventStore eventStore;
    private final PatternStore patternStore;

    public PatternDistiller(LearningEventStore eventStore, PatternStore patternStore) {
        this.eventStore = eventStore;
        this.patternStore = patternStore;
    }

    public DistillResult distill(String featureFilter) throws Exception {
        List<LearningEvent> events = eventStore.readAll();
        Map<String, List<LearningEvent>> grouped = new LinkedHashMap<>();
        for (LearningEvent e : events) {
            AccuracyGate.GateResult gate = AccuracyGate.evaluate(e);
            if (!gate.passed()) {
                continue;
            }
            if (featureFilter != null && !featureFilter.isBlank()
                    && e.feature() != null
                    && !e.feature().toLowerCase(Locale.ROOT).contains(featureFilter.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String key = (e.feature() != null ? e.feature() : "unknown") + "|" + e.intent();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        List<PatternDocument> created = new ArrayList<>();
        int skipped = events.size();
        for (Map.Entry<String, List<LearningEvent>> entry : grouped.entrySet()) {
            List<LearningEvent> samples = entry.getValue();
            skipped -= samples.size();
            LearningEvent best = samples.get(samples.size() - 1);
            double avgScore = samples.stream().mapToDouble(AccuracyGate::score).average().orElse(0.97);
            String patternId = slug(best.feature()) + "-" + slug(best.intent()) + "-v" + samples.size();
            String markdown = renderMarkdown(best, samples, avgScore);
            PatternDocument doc = new PatternDocument(
                    patternId,
                    best.feature() + " / " + best.intent(),
                    best.feature(),
                    PatternDocument.SCOPE_PROJECT,
                    List.of(best.intent(), best.feature(), "distilled"),
                    avgScore,
                    samples.size(),
                    Instant.now(),
                    markdown);
            patternStore.save(doc);
            created.add(doc);
        }
        return new DistillResult(created.size(), skipped, created);
    }

    private static String renderMarkdown(LearningEvent best, List<LearningEvent> samples, double score) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Pattern: ").append(best.feature()).append("\n\n");
        sb.append("- intent: `").append(best.intent()).append("`\n");
        sb.append("- confidence: ").append(String.format(Locale.ROOT, "%.3f", score)).append("\n");
        sb.append("- samples: ").append(samples.size()).append("\n");
        if (best.httpMode() != null) {
            sb.append("- httpMode: ").append(best.httpMode()).append("\n");
        }
        sb.append("\n## 复用元件\n");
        if (best.reusedComponents() != null && !best.reusedComponents().isEmpty()) {
            sb.append(best.reusedComponents().stream().map(c -> "- `" + c + "`").collect(Collectors.joining("\n")));
        } else {
            sb.append("- （见 plan_chain 输出）\n");
        }
        sb.append("\n\n## 新建元件\n");
        if (best.createdComponents() != null && !best.createdComponents().isEmpty()) {
            sb.append(best.createdComponents().stream().map(c -> "- `" + c + "`").collect(Collectors.joining("\n")));
        } else {
            sb.append("- 无\n");
        }
        sb.append("\n\n## 用户修正\n");
        sb.append(best.userCorrection() != null && !best.userCorrection().isBlank()
                ? best.userCorrection() : "无");
        sb.append("\n\n## 工作流\n");
        sb.append("plan_chain → scaffold（gap）→ compose_chain → validate_chain → bind_http → gen_playground_scene → record_learning_event\n");
        return sb.toString();
    }

    private static String slug(String s) {
        if (s == null || s.isBlank()) {
            return "unknown";
        }
        return s.replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase(Locale.ROOT);
    }

    public record DistillResult(int promotedCount, int skippedCount, List<PatternDocument> patterns) {
    }
}
