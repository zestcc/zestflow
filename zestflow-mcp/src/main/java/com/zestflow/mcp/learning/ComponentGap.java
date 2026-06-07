package com.zestflow.mcp.learning;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComponentGap(
        String componentId,
        String componentType,
        String action,
        String reason
) {
}
