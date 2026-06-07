package com.zestflow.mcp.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 项目级 Pattern 库 — {@code .zestflow/patterns/*.md} + index.json。
 */
public class PatternStore {

    private final Path patternsDir;
    private final Path indexFile;
    private final ObjectMapper mapper;

    public PatternStore(Path projectRoot) {
        this.patternsDir = projectRoot.resolve(".zestflow/patterns");
        this.indexFile = patternsDir.resolve("index.json");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public PatternDocument save(PatternDocument doc) throws IOException {
        Files.createDirectories(patternsDir);
        String filename = doc.id() + ".md";
        Path mdFile = patternsDir.resolve(filename);
        Files.writeString(mdFile, doc.markdown(), StandardCharsets.UTF_8);

        PatternIndex index = readIndex();
        index.entries().removeIf(e -> e.id().equals(doc.id()));
        index.entries().add(new PatternIndexEntry(
                doc.id(), doc.title(), doc.feature(), doc.scope(),
                doc.tags(), doc.confidenceScore(), doc.sampleCount(),
                doc.updatedAt() != null ? doc.updatedAt() : Instant.now(),
                filename));
        writeIndex(index);
        return doc;
    }

    public List<PatternDocument> listAll() throws IOException {
        PatternIndex index = readIndex();
        List<PatternDocument> docs = new ArrayList<>();
        for (PatternIndexEntry entry : index.entries()) {
            loadEntry(entry).ifPresent(docs::add);
        }
        docs.sort(Comparator.comparing(PatternDocument::confidenceScore).reversed());
        return docs;
    }

    public Optional<PatternDocument> get(String id) throws IOException {
        return readIndex().entries().stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .flatMap(this::loadEntry);
    }

    private Optional<PatternDocument> loadEntry(PatternIndexEntry entry) {
        try {
            Path md = patternsDir.resolve(entry.filename());
            if (!Files.isRegularFile(md)) {
                return Optional.empty();
            }
            return Optional.of(new PatternDocument(
                    entry.id(), entry.title(), entry.feature(), entry.scope(),
                    entry.tags(), entry.confidenceScore(), entry.sampleCount(),
                    entry.updatedAt(), Files.readString(md, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private PatternIndex readIndex() throws IOException {
        if (!Files.isRegularFile(indexFile)) {
            return new PatternIndex(new ArrayList<>());
        }
        return mapper.readValue(Files.readString(indexFile, StandardCharsets.UTF_8), PatternIndex.class);
    }

    private void writeIndex(PatternIndex index) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), index);
    }

    public String exportForTeamImport(String patternId) throws IOException {
        PatternDocument doc = get(patternId).orElseThrow(() -> new IOException("Pattern 不存在: " + patternId));
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(MapExport.of(doc));
    }

    private record PatternIndex(List<PatternIndexEntry> entries) {
    }

    public record PatternIndexEntry(
            String id,
            String title,
            String feature,
            String scope,
            List<String> tags,
            double confidenceScore,
            int sampleCount,
            Instant updatedAt,
            String filename
    ) {
    }

    public record MapExport(String title, String appCode, String content, boolean enabled) {
        static MapExport of(PatternDocument doc) {
            return new MapExport(
                    "[Pattern] " + doc.title(),
                    null,
                    doc.markdown(),
                    true);
        }
    }
}
