package com.zestflow.admin.ai;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Copilot Prompt 构建器
 */
@Component
public class PromptBuilder {

    public String buildSystemPrompt(String mode, List<String> allowedComponents) {
        String componentList = formatComponents(allowedComponents);
        return switch (mode) {
            case "explain" -> buildExplainSystem(componentList);
            case "suggest", "generate", "modify" -> buildSuggestSystem(componentList);
            case "fix-errors" -> buildFixErrorsSystem(componentList);
            case "expression" -> buildExpressionSystem(componentList);
            case "diagnose" -> buildDiagnoseSystem();
            default -> buildSuggestSystem(componentList);
        };
    }

    public String buildDiagnoseUserPrompt(String errorSummary, String traceSummary) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(errorSummary)) {
            sb.append("错误摘要：\n").append(errorSummary).append("\n\n");
        }
        sb.append("执行轨迹摘要：\n").append(nullToEmpty(traceSummary)).append("\n\n");
        sb.append("请输出 JSON：{\"diagnosis\":\"根因分析（中文）\",\"suggestion\":\"修复建议（中文，可含具体节点/表达式）\"}");
        return sb.toString();
    }

    public String buildUserPrompt(String mode, String userMessage, String chainData, List<String> validationErrors) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户请求：").append(nullToEmpty(userMessage)).append("\n\n");
        if (StringUtils.hasText(chainData)) {
            sb.append("当前链定义 JSON：\n").append(chainData).append("\n\n");
        }
        if (validationErrors != null && !validationErrors.isEmpty()) {
            sb.append("校验错误（请修复）：\n");
            for (String err : validationErrors) {
                sb.append("- ").append(err).append("\n");
            }
            sb.append("\n");
        }
        sb.append(switch (mode) {
            case "explain" -> "请用中文解释上述链的业务流程、节点职责与数据流向。";
            case "expression" -> "请生成或修正 Aviator 表达式，并简要说明。";
            case "fix-errors" -> "请根据校验错误修复链定义，仅输出 JSON。";
            case "modify" -> "请在现有链基础上按用户描述修改，输出完整 chainData JSON。";
            default -> "请根据用户描述生成完整 chainData JSON。"
                    + "自行对标业界成熟方案并结合知识库验收标准拆步，禁止单节点黑盒。";
        });
        return sb.toString();
    }

    public String buildQualityRetryUserPrompt(String userMessage, String chainData, String critique) {
        StringBuilder sb = new StringBuilder();
        sb.append("【质量复检未通过】").append(critique).append("\n\n");
        sb.append("用户原始需求：").append(nullToEmpty(userMessage)).append("\n\n");
        if (StringUtils.hasText(chainData)) {
            sb.append("你上一版过于简化的 chainData：\n").append(chainData).append("\n\n");
        }
        sb.append("请重新思考并输出完整 JSON：对标业界主路径扩写，满足验收标准，90% happy path 可试跑。");
        return sb.toString();
    }

    public String buildExpressionUserPrompt(String userMessage, String currentExpression, String contextHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户需求：").append(nullToEmpty(userMessage)).append("\n");
        if (StringUtils.hasText(currentExpression)) {
            sb.append("当前表达式：").append(currentExpression).append("\n");
        }
        if (StringUtils.hasText(contextHint)) {
            sb.append("上下文提示：").append(contextHint).append("\n");
        }
        sb.append("请输出 JSON：{\"expression\":\"...\",\"explanation\":\"...\"}");
        return sb.toString();
    }

    private String buildExplainSystem(String componentList) {
        return """
                你是 ZestFlow 编排助手，帮助开发人员理解链定义。
                规则：
                1. 只能引用已注册元件：%s
                2. 用中文清晰说明节点顺序、分支条件与上下文数据流
                3. 不要编造未出现的 componentId
                4. 不要给出发布或 reload 指令
                """.formatted(componentList);
    }

    private String buildSuggestSystem(String componentList) {
        return AiGenerationAcceptance.PROMPT_ANCHOR + """

                你是 ZestFlow 链编排 Copilot（通用），输出须符合 ChainDefinitionDTO schema。
                1. 只能使用 allowedComponents：%s
                2. 条件边 Aviator；chainCtx.get(ctx, 'key')
                3. 仅输出 JSON：{"chainData":{...},"summary":"..."}
                4. chainData.config.lifecycle 必须为 production（禁止 bootstrap 占位链）
                5. 优先调用 Admin API /ai/delivery/patterns + /ai/chains/compose 按 Pattern 实例化，再 validate
                6. 生成前检索知识库 ai-generation-acceptance；未完成 validate_delivery(passed=true) 禁止宣称完成
                """.formatted(componentList);
    }

    private String buildFixErrorsSystem(String componentList) {
        return """
                你是 ZestFlow 链修复助手。根据校验错误修正 chainData。
                规则：
                1. 只能使用 componentId：%s
                2. 仅输出 JSON：{"chainData":{...},"summary":"..."}
                3. 修复所有列出的校验错误，不要引入新的非法 componentId
                """.formatted(componentList);
    }

    private String buildExpressionSystem(String componentList) {
        return """
                你是 ZestFlow Aviator 表达式助手。
                规则：
                1. 可用元件上下文参考：%s
                2. 上下文读取：chainCtx.get(ctx, 'key')
                3. 仅输出 JSON：{"expression":"...","explanation":"..."}
                4. 表达式需可嵌入链条件边
                """.formatted(componentList);
    }

    private String buildDiagnoseSystem() {
        return """
                你是 ZestFlow 执行日志诊断助手，根据链执行轨迹定位失败根因。
                规则：
                1. 结合 NODE_FAILED / CHAIN_FAILED 事件与 errorMessage 分析
                2. 指出最可能出错的节点、表达式或入参问题
                3. 修复建议应可落地（跳转设计器修改链/表达式/参数）
                4. 仅输出 JSON：{"diagnosis":"...","suggestion":"..."}
                5. 不要建议自动发布或 reload
                """;
    }

    private static String formatComponents(List<String> allowedComponents) {
        if (allowedComponents == null || allowedComponents.isEmpty()) {
            return "（暂无元件列表，请勿编造 componentId）";
        }
        return allowedComponents.stream().collect(Collectors.joining(", "));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
