package com.zestflow.mcp.learning;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanStep(
        String stepId,
        String componentId,
        String componentType,
        String nodeType,
        String action,
        String description,
        List<String> reads,
        List<String> writes,
        String reuseStatus
) {
}
