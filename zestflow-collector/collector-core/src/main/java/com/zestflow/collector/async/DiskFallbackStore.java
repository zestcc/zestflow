package com.zestflow.collector.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.model.dto.ChainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 磁盘降级存储 — JSONL 格式（对标 Logstash persistent queue / Fluent Bit storage.type filesystem）。
 */
public class DiskFallbackStore {

    private static final Logger log = LoggerFactory.getLogger(DiskFallbackStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path spoolDir;
    private final Path archiveDir;
    private final Object writeLock = new Object();

    public DiskFallbackStore(String baseDir) {
        this.spoolDir = Paths.get(baseDir, "spool");
        this.archiveDir = Paths.get(baseDir, "archive");
    }

    public void append(ChainEvent event) {
        if (event == null) {
            return;
        }
        synchronized (writeLock) {
            try {
                Files.createDirectories(spoolDir);
                Path file = spoolDir.resolve("events.jsonl");
                String line = MAPPER.writeValueAsString(event) + System.lineSeparator();
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.error("磁盘降级写入失败 eventId={}", event.getEventId(), e);
            }
        }
    }

    public SpoolBatch pollBatch(int maxSize) {
        synchronized (writeLock) {
            try {
                if (!Files.exists(spoolDir)) {
                    return SpoolBatch.empty();
                }
                try (Stream<Path> files = Files.list(spoolDir)) {
                    List<Path> jsonlFiles = files
                            .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                            .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                            .toList();
                    if (jsonlFiles.isEmpty()) {
                        return SpoolBatch.empty();
                    }
                    Path file = jsonlFiles.get(0);
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    if (lines.isEmpty()) {
                        Files.deleteIfExists(file);
                        return SpoolBatch.empty();
                    }
                    int limit = Math.min(maxSize, lines.size());
                    List<ChainEvent> events = new ArrayList<>(limit);
                    for (int i = 0; i < limit; i++) {
                        String line = lines.get(i).trim();
                        if (line.isEmpty()) {
                            continue;
                        }
                        events.add(MAPPER.readValue(line, ChainEvent.class));
                    }
                    return new SpoolBatch(file, lines, events);
                }
            } catch (IOException e) {
                log.error("读取磁盘降级文件失败", e);
                return SpoolBatch.empty();
            }
        }
    }

    public void acknowledge(SpoolBatch batch) {
        if (batch == null || batch.file() == null || batch.events().isEmpty()) {
            return;
        }
        synchronized (writeLock) {
            try {
                Files.createDirectories(archiveDir);
                List<String> remaining = batch.lines().subList(batch.events().size(), batch.lines().size());
                if (remaining.isEmpty()) {
                    Path archived = archiveDir.resolve(batch.file().getFileName().toString().replace(".jsonl",
                            "-" + System.currentTimeMillis() + ".jsonl"));
                    Files.move(batch.file(), archived, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.write(batch.file(), remaining, StandardCharsets.UTF_8,
                            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                }
            } catch (IOException e) {
                log.error("确认磁盘降级批次失败 file={}", batch.file(), e);
            }
        }
    }

    public int countPendingEvents() {
        synchronized (writeLock) {
            try {
                if (!Files.exists(spoolDir)) {
                    return 0;
                }
                int count = 0;
                try (Stream<Path> files = Files.list(spoolDir)) {
                    List<Path> jsonlFiles = files
                            .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                            .toList();
                    for (Path file : jsonlFiles) {
                        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                            count += (int) lines.filter(line -> !line.isBlank()).count();
                        }
                    }
                }
                return count;
            } catch (IOException e) {
                log.debug("统计磁盘降级待回放事件失败", e);
                return 0;
            }
        }
    }

    public record SpoolBatch(Path file, List<String> lines, List<ChainEvent> events) {
        static SpoolBatch empty() {
            return new SpoolBatch(null, List.of(), List.of());
        }

        boolean isEmpty() {
            return events == null || events.isEmpty();
        }
    }
}
