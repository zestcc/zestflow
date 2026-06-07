package com.zestflow.mcp.export;

import com.zestflow.mcp.client.HttpApiClient;
import com.zestflow.mcp.config.McpRuntimeConfig;
import com.zestflow.mcp.io.ResourceLoader;
import com.zestflow.mcp.search.ProjectSourceSearcher;

import java.nio.file.Path;
import java.time.LocalDate;

/**
 * 导出 Cursor 任务包（Markdown），与 MCP Resources 同源。
 */
public class TaskPackageExporter {

    private static final String[] RULE_FILES = {
            "rules/component-development.md",
            "rules/chain-definition.md",
            "rules/anti-patterns.md"
    };

    private final McpRuntimeConfig config;
    private final HttpApiClient apiClient;

    public TaskPackageExporter(McpRuntimeConfig config, HttpApiClient apiClient) {
        this.config = config;
        this.apiClient = apiClient;
    }

    public String exportMarkdown(String appCode, boolean includeSampleSearch) throws Exception {
        String resolvedApp = appCode != null && !appCode.isBlank() ? appCode : config.appCode();
        StringBuilder md = new StringBuilder();
        md.append("# ZestFlow Dev Copilot 任务包\n\n");
        md.append("- appCode: `").append(resolvedApp).append("`\n");
        md.append("- 日期: ").append(LocalDate.now()).append("\n");
        md.append("- 说明: 将本文档 `@` 进 Cursor/Claude；推荐长期配置 `zestflow-mcp`（见 docs/MCP_SETUP.md）\n\n");

        md.append("## L0 硬约束\n\n");
        md.append("- 不得编造 componentId\n");
        md.append("- 链 JSON 必须 validate\n");
        md.append("- 禁止自动 publish/reload\n");
        md.append("- **源码由 IDE Apply 落盘，MCP 不写盘**\n\n");

        appendRules(md);
        appendProjectRules(md);
        appendComponents(md, resolvedApp);
        if (includeSampleSearch && config.projectRoot() != null) {
            appendSampleHits(md);
        }
        md.append("\n## 推荐 MCP Tools 调用顺序\n\n");
        md.append("1. `list_components`\n");
        md.append("2. `search_sources` / `read_project_file`\n");
        md.append("3. `scaffold_component`（仅返回文本）\n");
        md.append("4. IDE Apply 保存\n");
        md.append("5. `validate_chain`（若涉及链）\n");
        return md.toString();
    }

    private void appendRules(StringBuilder md) throws Exception {
        md.append("## L1 官方规范摘要\n\n");
        for (String file : RULE_FILES) {
            String text = ResourceLoader.readClasspath("zestflow/" + file);
            md.append("### ").append(file).append("\n\n");
            md.append(text.lines().limit(40).reduce((a, b) -> a + "\n" + b).orElse(""));
            md.append("\n\n");
        }
    }

    private void appendProjectRules(StringBuilder md) {
        Path root = config.projectRoot();
        if (root == null) {
            md.append("## L2 项目规则\n\n（Admin 导出无本地工程路径；请在 Executor 工程配置 `.zestflow/rules/project.md` 或使用 MCP）\n\n");
            return;
        }
        String projectRules = ResourceLoader.readProjectRules(root);
        md.append("## L2 项目规则\n\n");
        md.append(projectRules.isBlank()
                ? "（未配置 `.zestflow/rules/project.md`）\n\n"
                : projectRules + "\n\n");
    }

    private void appendComponents(StringBuilder md, String appCode) throws Exception {
        md.append("## 元件白名单\n\n");
        md.append("```json\n");
        md.append(apiClient.listComponents(appCode));
        md.append("\n```\n\n");
    }

    private void appendSampleHits(StringBuilder md) {
        md.append("## 参考源码（@ZestComponent 抽样）\n\n");
        try {
            String hits = new ProjectSourceSearcher().search(
                    config.projectRoot(), "@ZestComponent", "**/*.java", 5);
            md.append("```json\n").append(hits).append("\n```\n\n");
        } catch (Exception e) {
            md.append("_搜索失败: ").append(e.getMessage()).append("_\n\n");
        }
    }
}
