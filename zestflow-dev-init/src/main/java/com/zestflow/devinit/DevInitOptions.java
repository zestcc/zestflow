package com.zestflow.devinit;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * {@code --init-dev} 选项。
 */
public final class DevInitOptions {

    public enum IdeTarget {
        CURSOR,
        VSCODE,
        CLAUDE,
        /** Claude Code CLI：项目根 {@code .mcp.json}（可提交 Git） */
        CLAUDE_CODE,
        /** Windsurf Cascade：全局 {@code ~/.codeium/windsurf/mcp_config.json} 示例 */
        WINDSURF;

        public static IdeTarget parse(String raw) {
            if (Strings.isBlank(raw)) {
                return CURSOR;
            }
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if ("cursor".equals(value)) {
                return CURSOR;
            }
            if ("vscode".equals(value) || "vs-code".equals(value)) {
                return VSCODE;
            }
            if ("cline".equals(value)) {
                return VSCODE;
            }
            if ("claude".equals(value) || "claude-desktop".equals(value)) {
                return CLAUDE;
            }
            if ("claude-code".equals(value) || "claudecode".equals(value)) {
                return CLAUDE_CODE;
            }
            if ("windsurf".equals(value) || "codeium".equals(value)) {
                return WINDSURF;
            }
            if ("all".equals(value)) {
                throw new IllegalArgumentException("use parseAll for 'all'");
            }
            throw new IllegalArgumentException(
                    "未知 --ide: " + raw + "（可选 cursor|vscode|cline|claude|claude-code|windsurf|all）");
        }

        public static Set<IdeTarget> parseAll(String raw) {
            if (Strings.isBlank(raw) || "all".equalsIgnoreCase(raw)) {
                return EnumSet.of(CURSOR, VSCODE, CLAUDE, CLAUDE_CODE, WINDSURF);
            }
            return EnumSet.of(parse(raw));
        }
    }

    private final String appCode;
    private final String executorUrl;
    private final ComponentizationMode componentization;
    private final String componentPackage;
    private final HttpExposureMode httpMode;
    private final Set<IdeTarget> ides;
    private final boolean force;
    private final boolean appendGitignore;
    private final boolean bootstrapConfig;

    public DevInitOptions(
            String appCode,
            String executorUrl,
            ComponentizationMode componentization,
            String componentPackage,
            HttpExposureMode httpMode,
            Set<IdeTarget> ides,
            boolean force,
            boolean appendGitignore,
            boolean bootstrapConfig) {
        this.appCode = appCode;
        this.executorUrl = executorUrl;
        this.componentization = componentization;
        this.componentPackage = componentPackage;
        this.httpMode = httpMode == null ? HttpExposureMode.MODE3 : httpMode;
        this.ides = ides == null ? Collections.<IdeTarget>emptySet() : ides;
        this.force = force;
        this.appendGitignore = appendGitignore;
        this.bootstrapConfig = bootstrapConfig;
    }

    public String appCode() {
        return appCode;
    }

    public String executorUrl() {
        return executorUrl;
    }

    public ComponentizationMode componentization() {
        return componentization;
    }

    public String componentPackage() {
        return componentPackage;
    }

    public HttpExposureMode httpMode() {
        return httpMode;
    }

    public Set<IdeTarget> ides() {
        return ides;
    }

    public boolean force() {
        return force;
    }

    public boolean appendGitignore() {
        return appendGitignore;
    }

    public boolean bootstrapConfig() {
        return bootstrapConfig;
    }
}
