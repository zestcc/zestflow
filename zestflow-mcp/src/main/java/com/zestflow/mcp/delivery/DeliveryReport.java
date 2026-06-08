package com.zestflow.mcp.delivery;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code validate_delivery} 结构化报告。
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DeliveryReport(
        boolean passed,
        double score,
        int chainKeyCount,
        List<String> blocking,
        List<String> warnings,
        List<String> nextActions
) {
}
