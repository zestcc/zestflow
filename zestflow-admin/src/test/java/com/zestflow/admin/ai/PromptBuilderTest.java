package com.zestflow.admin.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    @Test
    void buildSystemPrompt_explain_shouldIncludeAllowedComponents() {
        List<String> components = List.of("deductStock", "notifyUser");
        String system = promptBuilder.buildSystemPrompt("explain", components);

        assertThat(system).contains("deductStock");
        assertThat(system).contains("notifyUser");
        assertThat(system).contains("ZestFlow");
    }

    @Test
    void buildSystemPrompt_suggest_shouldRequireJsonOutput() {
        String system = promptBuilder.buildSystemPrompt("suggest", List.of("payOrder"));

        assertThat(system).contains("payOrder");
        assertThat(system).contains("chainData");
        assertThat(system).contains("Aviator");
    }

    @Test
    void buildSystemPrompt_fixErrors_shouldMentionValidation() {
        String system = promptBuilder.buildSystemPrompt("fix-errors", List.of("retryPay"));

        assertThat(system).contains("retryPay");
        assertThat(system).contains("校验错误");
    }

    @Test
    void buildUserPrompt_fixErrors_shouldListValidationErrors() {
        String user = promptBuilder.buildUserPrompt(
                "fix-errors",
                "修复链",
                "{\"nodes\":[]}",
                List.of("节点 n1 缺少 componentId", "边 e1 目标不存在"));

        assertThat(user).contains("节点 n1 缺少 componentId");
        assertThat(user).contains("边 e1 目标不存在");
        assertThat(user).contains("修复");
    }

    @Test
    void buildExpressionUserPrompt_shouldIncludeCurrentExpression() {
        String user = promptBuilder.buildExpressionUserPrompt(
                "加上空值判断",
                "amount > 0",
                "支付节点");

        assertThat(user).contains("amount > 0");
        assertThat(user).contains("支付节点");
        assertThat(user).contains("expression");
    }

    @Test
    void buildDiagnosePrompts_shouldTargetJsonDiagnosis() {
        String system = promptBuilder.buildSystemPrompt("diagnose", null);
        String user = promptBuilder.buildDiagnoseUserPrompt("库存不足", "NODE_FAILED node=deductStock");

        assertThat(system).contains("diagnosis");
        assertThat(user).contains("库存不足");
        assertThat(user).contains("deductStock");
    }
}
