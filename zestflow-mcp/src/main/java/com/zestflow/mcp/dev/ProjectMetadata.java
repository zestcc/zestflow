package com.zestflow.mcp.dev;

/**
 * 从业务工程解析出的 Dev Copilot 默认值。
 */
public record ProjectMetadata(String appCode, String executorUrl, String basePackage) {
}
