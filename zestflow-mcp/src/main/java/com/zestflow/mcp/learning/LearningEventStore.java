package com.zestflow.mcp.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 项目级学习事件存储 — {@code .zestflow/learning/events.jsonl}。
 */
public class LearningEventStore {

    private final Path eventsFile;
    private final ObjectMapper mapper;

    public LearningEventStore(Path projectRoot) {
        this.eventsFile = projectRoot.resolve(".zestflow/learning/events.jsonl");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public LearningEvent append(LearningEvent draft) throws IOException {
        Files.createDirectories(eventsFile.getParent());
        LearningEvent event = new LearningEvent(
                draft.id() != null ? draft.id() : UUID.randomUUID().toString(),
                draft.timestamp() != null ? draft.timestamp() : Instant.now(),
                draft.intent(),
                draft.feature(),
                draft.appCode(),
                draft.chainCode(),
                draft.httpMode(),
                draft.reusedComponents(),
                draft.createdComponents(),
                draft.validateRounds(),
                draft.validatePassed(),
                draft.adopted(),
                draft.playgroundSuccess(),
                draft.userCorrection(),
                draft.chainData(),
                draft.metadata());
        String line = mapper.writeValueAsString(event);
        Files.writeString(eventsFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return event;
    }

    public List<LearningEvent> readAll() throws IOException {
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

    public Path eventsFile() {
        return eventsFile;
    }
}
