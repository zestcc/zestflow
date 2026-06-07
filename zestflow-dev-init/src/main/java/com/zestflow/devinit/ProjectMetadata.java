package com.zestflow.devinit;

/**
 * 从业务工程解析出的 Dev Copilot 默认值。
 */
public final class ProjectMetadata {

    private final String appCode;
    private final String executorUrl;

    public ProjectMetadata(String appCode, String executorUrl) {
        this.appCode = appCode;
        this.executorUrl = executorUrl;
    }

    public String appCode() {
        return appCode;
    }

    public String executorUrl() {
        return executorUrl;
    }
}
