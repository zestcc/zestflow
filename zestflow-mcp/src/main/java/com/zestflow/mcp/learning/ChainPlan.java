package com.zestflow.mcp.learning;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChainPlan(
        String feature,
        String featureLabel,
        String suggestedChainCode,
        List<PlanStep> steps,
        List<ComponentGap> gaps,
        List<String> matchedPatterns,
        String httpModeQuestion,
        String workflowNext,
        double estimatedAccuracyHint
) {
}
