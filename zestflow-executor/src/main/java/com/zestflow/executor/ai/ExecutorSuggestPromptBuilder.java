package com.zestflow.executor.ai;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Executor suggest Prompt（对齐 Admin {@link com.zestflow.admin.ai.PromptBuilder}）。
 */
public final class ExecutorSuggestPromptBuilder {

    private static final String PROMPT_ANCHOR = """
            【唯一规则】所有 AI 生成须站在验收标准：多思考、对标市面成熟方案/框架/设计，结合内部 RAG 生成，90%% happy path 开箱可跑。
            生成前检索 RAG；禁止单节点黑盒；不达标自行扩写。高置信结果须沉淀并自动蒸馏进 RAG，供后续检索复用。
            """;

    private ExecutorSuggestPromptBuilder() {
    }

    public static String buildSystemPrompt(List<String> allowedComponents) {
        return PROMPT_ANCHOR + """

                你是 ZestFlow 链编排 Copilot（应用端 Executor），输出须符合 ChainDefinitionDTO schema。
                1. 只能使用 allowedComponents：%s
                2. 输出 JSON：{"summary":"中文摘要","chainData":{ "nodes":[...], "edges":[...] }}
                3. nodes 须含 START/END 或等效结构；复杂业务须含 CONDITION 分叉与失败路径
                4. 每节点单一职责，禁止单节点黑盒
                5. 仅输出 JSON，不要 markdown 包裹
                """.formatted(formatComponents(allowedComponents));
    }

    public static String buildUserPrompt(String userMessage, List<String> ragSnippets) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户请求：").append(nullToEmpty(userMessage)).append("\n\n");
        if (ragSnippets != null && !ragSnippets.isEmpty()) {
            sb.append("【应用端 RAG 参考】\n");
            for (int i = 0; i < ragSnippets.size(); i++) {
                sb.append("--- snippet ").append(i + 1).append(" ---\n");
                sb.append(truncate(ragSnippets.get(i), 1200)).append("\n");
            }
            sb.append("\n");
        }
        sb.append("请根据用户描述生成完整 chainData JSON，对标业界成熟方案拆步。");
        return sb.toString();
    }

    public static String buildQualityRetryPrompt(String userMessage, String chainData, String critique) {
        StringBuilder sb = new StringBuilder();
        sb.append("【质量复检未通过】").append(critique).append("\n\n");
        sb.append("用户原始需求：").append(nullToEmpty(userMessage)).append("\n\n");
        if (StringUtils.hasText(chainData)) {
            sb.append("上一版 chainData：\n").append(chainData).append("\n\n");
        }
        sb.append("请重新输出 JSON：对标主路径扩写，满足验收标准。");
        return sb.toString();
    }

    public static String buildFixErrorsPrompt(String userMessage, String chainData, List<String> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户请求：").append(nullToEmpty(userMessage)).append("\n\n");
        if (StringUtils.hasText(chainData)) {
            sb.append("当前链定义：\n").append(chainData).append("\n\n");
        }
        if (errors != null && !errors.isEmpty()) {
            sb.append("校验错误（请修复）：\n");
            for (String err : errors) {
                sb.append("- ").append(err).append("\n");
            }
        }
        sb.append("\n请修复并输出完整 JSON。");
        return sb.toString();
    }

    private static String formatComponents(List<String> allowedComponents) {
        if (allowedComponents == null || allowedComponents.isEmpty()) {
            return "（未限制，优先复用 RAG 中出现的 componentId）";
        }
        return allowedComponents.stream().map(c -> "`" + c + "`").collect(Collectors.joining(", "));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
