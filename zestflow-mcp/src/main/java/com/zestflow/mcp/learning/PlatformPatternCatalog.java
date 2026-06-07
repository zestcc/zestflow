package com.zestflow.mcp.learning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 平台级 Pattern 库（L1）— JAR 内 {@code zestflow/patterns/platform/}，全员只读。
 */
public class PlatformPatternCatalog {

    private static final String INDEX = "zestflow/patterns/platform/index.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<PatternDocument> listAll() {
        List<PatternDocument> docs = new ArrayList<>();
        try {
            String indexJson = readClasspath(INDEX);
            List<IndexEntry> entries = MAPPER.readValue(indexJson, new TypeReference<>() {
            });
            for (IndexEntry entry : entries) {
                String md = readClasspath("zestflow/patterns/platform/" + entry.file());
                docs.add(new PatternDocument(
                        entry.id(),
                        entry.title(),
                        entry.feature(),
                        PatternDocument.SCOPE_PLATFORM,
                        entry.tags(),
                        entry.confidenceScore(),
                        entry.sampleCount(),
                        Instant.parse(entry.updatedAt()),
                        md));
            }
        } catch (Exception ignored) {
            // empty catalog
        }
        return docs;
    }

    private static String readClasspath(String path) throws IOException {
        try (var in = PlatformPatternCatalog.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record IndexEntry(
            String id,
            String title,
            String feature,
            List<String> tags,
            double confidenceScore,
            int sampleCount,
            String updatedAt,
            String file
    ) {
    }
}
