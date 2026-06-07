package com.zestflow.mcp.learning;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * P1 学习事件 — 项目级原始信号（JSONL 存储）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearningEvent(
        String id,
        Instant timestamp,
        String intent,
        String feature,
        String appCode,
        String chainCode,
        Integer httpMode,
        List<String> reusedComponents,
        List<String> createdComponents,
        Integer validateRounds,
        Boolean validatePassed,
        Boolean adopted,
        Boolean playgroundSuccess,
        String userCorrection,
        String chainData,
        Map<String, Object> metadata
) {
}
