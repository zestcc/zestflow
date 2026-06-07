package com.zestflow.executor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zestflow.executor.registry.ExecutorProperties;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 应用端链条 AI 知识库：学习事件、RAG 检索、自动蒸馏（存于 {@code dataDir/ai/}）。
 */
@Slf4j
public class ExecutorChainAiService {

    private static final double PROMOTION_THRESHOLD = 0.97;
    private static final String ACCEPTANCE_RESOURCE = "zestflow-ai/ai-generation-acceptance.md";
    private static final Pattern CHAIN_JSON_BLOCK = Pattern.compile("```json\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final Path aiRoot;
    private final Path eventsFile;
    private final Path patternsDir;
    private final Path indexFile;
    private final ObjectMapper mapper;

    @Setter
    private ChainDataValidator chainDataValidator;

    public ExecutorChainAiService(ExecutorProperties properties) {
        this.aiRoot = Path.of(properties.getDataDir(), "ai").toAbsolutePath().normalize();
        this.eventsFile = aiRoot.resolve("learning/events.jsonl");
        this.patternsDir = aiRoot.resolve("patterns");
        this.indexFile = patternsDir.resolve("index.json");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Map<String, Object> ragStatus() throws IOException {
        List<LearningEvent> events = readAllEvents();
        List<PatternDoc> patterns = listPatterns();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("storage", aiRoot.toString());
        out.put("eventsFile", eventsFile.toString());
        out.put("eventCount", events.size());
        out.put("patternCount", patterns.size());
        out.put("patterns", patterns.stream()
                .sorted(Comparator.comparingDouble(PatternDoc::confidence).reversed())
                .limit(20)
                .map(p -> Map.of(
                        "id", p.id(),
                        "title", p.title(),
                        "feature", p.feature() != null ? p.feature() : "",
                        "confidence", p.confidence(),
                        "sampleCount", p.sampleCount()))
                .toList());
        if (!events.isEmpty()) {
            LearningEvent last = events.get(events.size() - 1);
            out.put("lastEventAt", last.timestamp());
            out.put("lastFeature", last.feature());
        }
        return out;
    }

    public Map<String, Object> recordLearningEvent(Map<String, Object> body) throws IOException {
        LearningEvent draft = parseEvent(body);
        Optional<LearningEvent> duplicate = findRecentDuplicate(draft);
        if (duplicate.isPresent()) {
            LearningEvent existing = duplicate.get();
            GateResult gate = evaluate(existing);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("saved", existing);
            out.put("deduplicated", true);
            out.put("promotionEligible", gate.passed());
            out.put("promotionScore", gate.score());
            out.put("promotionReason", gate.reason());
            out.put("eventsFile", eventsFile.toString());
            return out;
        }

        if (draft.chainData() != null && !draft.chainData().isBlank() && chainDataValidator != null) {
            String code = draft.chainCode() != null && !draft.chainCode().isBlank()
                    ? draft.chainCode() : "draft-" + slug(draft.feature());
            if (!chainDataValidator.isValid(code, draft.chainData())) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("saved", draft);
                out.put("promotionEligible", false);
                out.put("promotionReason", "chainData 未通过 validate-definition，拒绝晋升/蒸馏");
                out.put("eventsFile", eventsFile.toString());
                Files.createDirectories(eventsFile.getParent());
                LearningEvent saved = persistEvent(draft);
                out.put("saved", saved);
                return out;
            }
        }

        LearningEvent saved = persistEvent(draft);
        GateResult gate = evaluate(saved);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", saved);
        out.put("promotionEligible", gate.passed());
        out.put("promotionScore", gate.score());
        out.put("promotionReason", gate.reason());
        out.put("eventsFile", eventsFile.toString());

        if (gate.passed()) {
            DistillResult distilled = distill(saved.feature());
            out.put("autoDistilled", true);
            out.put("distilledPatternCount", distilled.promotedCount());
            out.put("distilledPatterns", distilled.patternIds());
        }
        return out;
    }

    private LearningEvent persistEvent(LearningEvent draft) throws IOException {
        Files.createDirectories(eventsFile.getParent());
        LearningEvent saved = new LearningEvent(
                draft.id() != null ? draft.id() : UUID.randomUUID().toString(),
                draft.timestamp() != null ? draft.timestamp() : Instant.now(),
                draft.intent(), draft.feature(), draft.appCode(), draft.chainCode(),
                draft.httpMode(), draft.reusedComponents(), draft.createdComponents(),
                draft.validateRounds(), draft.validatePassed(), draft.adopted(),
                draft.playgroundSuccess(), draft.userCorrection(), draft.chainData(), draft.metadata());
        Files.writeString(eventsFile,
                mapper.writeValueAsString(saved) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        return saved;
    }

    public List<String> searchRag(String query, int limit) throws IOException {
        return searchRagChunks(query, limit).stream().map(RagChunk::text).toList();
    }

    public Map<String, Object> suggestChain(String userMessage, String chainCode, List<String> allowedComponents)
            throws IOException {
        String q = userMessage != null ? userMessage : "";
        List<RagChunk> chunks = searchRagChunks(q, 8);
        String proposed = null;
        String source = "executor-rag-empty";
        String summary = "未找到可复用的应用端 pattern，请补充学习事件或手动建链。";

        for (RagChunk chunk : chunks) {
            Optional<String> json = extractChainJson(chunk.text());
            if (json.isPresent()) {
                proposed = json.get();
                source = "executor-pattern:" + chunk.id();
                summary = "基于应用端蒸馏 pattern（" + chunk.id() + "）生成链草稿，请校验后采纳。";
                break;
            }
        }

        if (proposed == null && !chunks.isEmpty()) {
            summary = "检索到 " + chunks.size() + " 条 RAG 片段但无链 JSON；摘要："
                    + truncate(chunks.get(0).text().replace('\n', ' '), 200);
            source = "executor-rag-hints";
        }

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("valid", false);
        validation.put("errors", List.of());
        if (proposed != null && chainDataValidator != null) {
            String code = chainCode != null && !chainCode.isBlank() ? chainCode : "draft-suggest";
            boolean ok = chainDataValidator.isValid(code, proposed);
            validation.put("valid", ok);
            if (!ok) {
                validation.put("errors", List.of("应用端 validate-definition 未通过"));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("source", source);
        out.put("proposedChainData", proposed);
        out.put("validation", validation);
        out.put("ragSnippetCount", chunks.size());
        if (allowedComponents != null && !allowedComponents.isEmpty()) {
            out.put("allowedComponents", allowedComponents);
        }
        return out;
    }

    public Map<String, Object> distillPatterns(String featureFilter) throws IOException {
        DistillResult result = distill(featureFilter);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("promotedCount", result.promotedCount());
        out.put("skippedCount", result.skippedCount());
        out.put("patternIds", result.patternIds());
        return out;
    }

    private List<RagChunk> searchRagChunks(String query, int limit) throws IOException {
        int cap = Math.max(1, Math.min(limit, 10));
        List<RagChunk> chunks = new ArrayList<>();
        loadClasspathAcceptance().ifPresent(text ->
                chunks.add(new RagChunk("platform:acceptance", text, scoreText(text, query) + 1.0)));
        for (PatternDoc doc : listPatterns()) {
            double s = scoreText(doc.markdown(), query) + doc.confidence() * 0.5;
            if (matches(doc, query) || s > 0.35) {
                chunks.add(new RagChunk(doc.id(), doc.markdown(), s));
            }
        }
        return chunks.stream()
                .sorted(Comparator.comparingDouble(RagChunk::score).reversed())
                .limit(cap)
                .toList();
    }

    private static double scoreText(String text, String query) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        if (query == null || query.isBlank()) {
            return 0.1;
        }
        Set<String> qTokens = tokenize(query);
        if (qTokens.isEmpty()) {
            return 0.1;
        }
        Set<String> docTokens = tokenize(text);
        long hit = qTokens.stream().filter(docTokens::contains).count();
        return (double) hit / qTokens.size();
    }

    private static Set<String> tokenize(String text) {
        return Pattern.compile("[a-zA-Z0-9\\u4e00-\\u9fa5]+")
                .matcher(text.toLowerCase(Locale.ROOT))
                .results()
                .map(m -> m.group())
                .filter(t -> t.length() > 1)
                .collect(Collectors.toSet());
    }

    private Optional<LearningEvent> findRecentDuplicate(LearningEvent draft) throws IOException {
        String key = dedupKey(draft);
        List<LearningEvent> events = readAllEvents();
        for (int i = events.size() - 1; i >= 0 && i >= events.size() - 50; i--) {
            LearningEvent e = events.get(i);
            if (key.equals(dedupKey(e))) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    private static String dedupKey(LearningEvent e) {
        String raw = (e.intent() != null ? e.intent() : "")
                + "|" + (e.feature() != null ? e.feature() : "")
                + "|" + (e.chainCode() != null ? e.chainCode() : "")
                + "|" + sha256Short(e.chainData())
                + "|" + Boolean.TRUE.equals(e.adopted())
                + "|" + Boolean.TRUE.equals(e.playgroundSuccess());
        return raw;
    }

    private static String sha256Short(String s) {
        if (s == null || s.isBlank()) {
            return "-";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig, 0, 8);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private Optional<String> extractChainJson(String markdown) {
        if (markdown == null) {
            return Optional.empty();
        }
        Matcher m = CHAIN_JSON_BLOCK.matcher(markdown);
        while (m.find()) {
            String block = m.group(1).trim();
            if (block.contains("\"nodes\"") || block.contains("nodes")) {
                try {
                    JsonNode node = mapper.readTree(block);
                    if (node.has("nodes") || node.has("chainData")) {
                        if (node.has("chainData")) {
                            return Optional.of(node.get("chainData").toString());
                        }
                        return Optional.of(node.toString());
                    }
                } catch (Exception ignored) {
                    // try next block
                }
            }
        }
        return Optional.empty();
    }

    private DistillResult distill(String featureFilter) throws IOException {
        List<LearningEvent> events = readAllEvents();
        Map<String, List<LearningEvent>> grouped = new LinkedHashMap<>();
        for (LearningEvent e : events) {
            GateResult gate = evaluate(e);
            if (!gate.passed()) {
                continue;
            }
            if (e.chainData() != null && !e.chainData().isBlank() && chainDataValidator != null) {
                String code = e.chainCode() != null && !e.chainCode().isBlank()
                        ? e.chainCode() : "draft-" + slug(e.feature());
                if (!chainDataValidator.isValid(code, e.chainData())) {
                    continue;
                }
            }
            if (featureFilter != null && !featureFilter.isBlank()
                    && e.feature() != null
                    && !e.feature().toLowerCase(Locale.ROOT).contains(featureFilter.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String key = (e.feature() != null ? e.feature() : "unknown") + "|" + e.intent();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        List<String> created = new ArrayList<>();
        int skipped = events.size();
        for (Map.Entry<String, List<LearningEvent>> entry : grouped.entrySet()) {
            List<LearningEvent> samples = entry.getValue();
            skipped -= samples.size();
            LearningEvent best = samples.get(samples.size() - 1);
            double avg = samples.stream().mapToDouble(this::score).average().orElse(PROMOTION_THRESHOLD);
            String id = slug(best.feature()) + "-" + slug(best.intent()) + "-v" + samples.size();
            String markdown = renderPatternMarkdown(best, samples, avg);
            savePattern(new PatternDoc(id, best.feature() + " / " + best.intent(), best.feature(),
                    List.of(best.intent(), best.feature(), "distilled"), avg, samples.size(),
                    Instant.now(), markdown));
            created.add(id);
        }
        return new DistillResult(created.size(), skipped, created);
    }

    private String renderPatternMarkdown(LearningEvent best, List<LearningEvent> samples, double score) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Pattern: ").append(best.feature()).append("\n\n");
        sb.append("- intent: `").append(best.intent()).append("`\n");
        sb.append("- chainCode: `").append(best.chainCode() != null ? best.chainCode() : "-").append("`\n");
        sb.append("- confidence: ").append(String.format(Locale.ROOT, "%.3f", score)).append("\n");
        sb.append("- samples: ").append(samples.size()).append("\n");
        if (best.httpMode() != null) {
            sb.append("- httpMode: ").append(best.httpMode()).append("\n");
        }
        sb.append("\n## 验收规则\n");
        sb.append("符合 ai-generation-acceptance；应用端自动蒸馏，生成前 search RAG 复用。\n");
        if (best.chainData() != null && !best.chainData().isBlank()) {
            sb.append("\n## 链结构摘要\n```json\n");
            sb.append(truncate(best.chainData(), 4000));
            sb.append("\n```\n");
        }
        if (best.reusedComponents() != null && !best.reusedComponents().isEmpty()) {
            sb.append("\n## 复用元件\n");
            sb.append(best.reusedComponents().stream().map(c -> "- `" + c + "`").collect(Collectors.joining("\n")));
        }
        if (best.createdComponents() != null && !best.createdComponents().isEmpty()) {
            sb.append("\n## 新建元件\n");
            sb.append(best.createdComponents().stream().map(c -> "- `" + c + "`").collect(Collectors.joining("\n")));
        }
        sb.append("\n\n## 用户修正\n");
        sb.append(best.userCorrection() != null && !best.userCorrection().isBlank() ? best.userCorrection() : "无");
        return sb.toString();
    }

    private void savePattern(PatternDoc doc) throws IOException {
        Files.createDirectories(patternsDir);
        Files.writeString(patternsDir.resolve(doc.id() + ".md"), doc.markdown(), StandardCharsets.UTF_8);
        PatternIndex index = readIndex();
        index.entries.removeIf(e -> e.id.equals(doc.id()));
        index.entries.add(new IndexEntry(doc.id(), doc.title(), doc.feature(), doc.confidence(),
                doc.sampleCount(), doc.updatedAt(), doc.id() + ".md"));
        mapper.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), index);
    }

    private List<PatternDoc> listPatterns() throws IOException {
        PatternIndex index = readIndex();
        List<PatternDoc> docs = new ArrayList<>();
        for (IndexEntry entry : index.entries) {
            Path md = patternsDir.resolve(entry.filename);
            if (!Files.isRegularFile(md)) {
                continue;
            }
            docs.add(new PatternDoc(entry.id, entry.title, entry.feature, List.of(),
                    entry.confidence, entry.sampleCount, entry.updatedAt,
                    Files.readString(md, StandardCharsets.UTF_8)));
        }
        return docs;
    }

    private PatternIndex readIndex() throws IOException {
        if (!Files.isRegularFile(indexFile)) {
            return new PatternIndex(new ArrayList<>());
        }
        return mapper.readValue(indexFile.toFile(), PatternIndex.class);
    }

    private List<LearningEvent> readAllEvents() throws IOException {
        if (!Files.isRegularFile(eventsFile)) {
            return List.of();
        }
        List<LearningEvent> list = new ArrayList<>();
        for (String line : Files.readAllLines(eventsFile, StandardCharsets.UTF_8)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            list.add(mapper.readValue(line, LearningEvent.class));
        }
        return list;
    }

    private LearningEvent parseEvent(Map<String, Object> body) throws IOException {
        JsonNode node = mapper.valueToTree(body);
        return mapper.treeToValue(node, LearningEvent.class);
    }

    private GateResult evaluate(LearningEvent event) {
        if (!Boolean.TRUE.equals(event.validatePassed())) {
            return GateResult.reject("validate 未通过");
        }
        if (event.validateRounds() != null && event.validateRounds() > 2) {
            return GateResult.reject("validate 修复轮次 > 2");
        }
        if (!Boolean.TRUE.equals(event.adopted()) && !Boolean.TRUE.equals(event.playgroundSuccess())) {
            return GateResult.reject("未采纳且 Playground 未成功");
        }
        if (event.intent() == null || event.intent().isBlank()) {
            return GateResult.reject("缺少 intent");
        }
        if (event.feature() == null || event.feature().isBlank()) {
            return GateResult.reject("缺少 feature");
        }
        double s = score(event);
        if (s < PROMOTION_THRESHOLD) {
            return GateResult.reject("置信分不足");
        }
        return GateResult.accept(s);
    }

    private double score(LearningEvent event) {
        double s = 0.70;
        if (Boolean.TRUE.equals(event.validatePassed())) {
            s += 0.12;
        }
        if (event.validateRounds() != null && event.validateRounds() <= 1) {
            s += 0.05;
        } else if (event.validateRounds() != null && event.validateRounds() == 2) {
            s += 0.02;
        }
        if (Boolean.TRUE.equals(event.adopted())) {
            s += 0.08;
        }
        if (Boolean.TRUE.equals(event.playgroundSuccess())) {
            s += 0.05;
        }
        if (event.userCorrection() == null || event.userCorrection().isBlank()) {
            s += 0.03;
        }
        if (event.httpMode() != null && event.httpMode() >= 1 && event.httpMode() <= 3) {
            s += 0.02;
        }
        return Math.min(1.0, s);
    }

    private Optional<String> loadClasspathAcceptance() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(ACCEPTANCE_RESOURCE)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static boolean matches(PatternDoc p, String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (q.isBlank()) {
            return true;
        }
        return contains(p.title(), q) || contains(p.feature(), q) || contains(p.markdown(), q);
    }

    private static boolean contains(String text, String q) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String slug(String s) {
        if (s == null || s.isBlank()) {
            return "unknown";
        }
        return s.replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase(Locale.ROOT);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n...";
    }

    public record LearningEvent(
            String id, Instant timestamp, String intent, String feature, String appCode,
            String chainCode, Integer httpMode, List<String> reusedComponents,
            List<String> createdComponents, Integer validateRounds, Boolean validatePassed,
            Boolean adopted, Boolean playgroundSuccess, String userCorrection,
            String chainData, Map<String, Object> metadata) {
    }

    private record RagChunk(String id, String text, double score) {
    }

    private record PatternDoc(String id, String title, String feature, List<String> tags,
                              double confidence, int sampleCount, Instant updatedAt, String markdown) {
    }

    private record DistillResult(int promotedCount, int skippedCount, List<String> patternIds) {
    }

    private record GateResult(boolean passed, double score, String reason) {
        static GateResult accept(double score) {
            return new GateResult(true, score, "ok");
        }

        static GateResult reject(String reason) {
            return new GateResult(false, 0, reason);
        }
    }

    static class PatternIndex {
        public List<IndexEntry> entries = new ArrayList<>();
        PatternIndex() {
        }
        PatternIndex(List<IndexEntry> entries) {
            this.entries = entries;
        }
    }

    static class IndexEntry {
        public String id;
        public String title;
        public String feature;
        public double confidence;
        public int sampleCount;
        public Instant updatedAt;
        public String filename;

        IndexEntry() {
        }

        IndexEntry(String id, String title, String feature, double confidence,
                   int sampleCount, Instant updatedAt, String filename) {
            this.id = id;
            this.title = title;
            this.feature = feature;
            this.confidence = confidence;
            this.sampleCount = sampleCount;
            this.updatedAt = updatedAt;
            this.filename = filename;
        }
    }
}
