package com.zestflow.mcp.learning;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaygroundSceneDraft(
        String sceneName,
        String chainCode,
        int httpMode,
        String requestMethod,
        String requestPath,
        String bodyTemplateJson,
        String hint
) {
}
