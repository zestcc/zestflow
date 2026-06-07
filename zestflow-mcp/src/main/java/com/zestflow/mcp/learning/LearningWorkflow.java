package com.zestflow.mcp.learning;

/**
 * 意图关键字与用户话术映射（Agent 应优先识别意图再调用 Tool）。
 */
public final class LearningWorkflow {

    public static final String INSTRUCTIONS = """
            ## Chain-first 意图工作流
            
            | 用户意图（示例） | Tool | 顺序 |
            |------------------|------|------|
            | 开发/规划…链路、注册链 | plan_chain | 1 |
            | 生成元件 xxx | scaffold_component | 2（仅 gap） |
            | 组链/生成链定义 | compose_chain + validate_chain | 3 |
            | Mode1/2/3、HTTP暴露 | bind_http（见平台 Pattern http-three-mode） | 4 |
            | 生成 Playground 场景 | gen_playground_scene | 5 |
            | 验证/试跑 | validate_chain | 6 |
            | 完成/采纳/修正 | record_learning_event | 7 |
            | 总结沉淀 | distill_patterns | 8（高置信事件） |
            | 检索历史经验 | search_patterns | plan 前 |
            | 共享给团队 | share_pattern | Admin RAG import |
            
            **准确率目标 ≥97%**：必须通过 validate_chain；采纳或 Playground 成功后再 record_learning_event。
            """;

    private LearningWorkflow() {
    }
}
