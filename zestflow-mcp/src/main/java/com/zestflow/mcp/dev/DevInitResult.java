package com.zestflow.mcp.dev;

import java.util.List;
import java.util.Map;

/**
 * {@code --init-dev} 执行结果。
 */
public record DevInitResult(List<String> created, List<String> skipped, Map<String, String> variables) {
}
