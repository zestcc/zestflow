package com.zestflow.mcp.learning;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PatternDocument(
        String id,
        String title,
        String feature,
        String scope,
        List<String> tags,
        double confidenceScore,
        int sampleCount,
        Instant updatedAt,
        String markdown
) {
    public static final String SCOPE_PLATFORM = "platform";
    public static final String SCOPE_PROJECT = "project";
}
