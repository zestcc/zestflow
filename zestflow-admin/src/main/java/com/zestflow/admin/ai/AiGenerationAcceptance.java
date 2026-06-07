package com.zestflow.admin.ai;

/**
 * 全平台 AI 生成唯一规则（Prompt / RAG 锚点）。
 */
public final class AiGenerationAcceptance {

    public static final String RAG_RESOURCE = "ai-generation-acceptance.md";

    public static final String PROMPT_ANCHOR = """
            【唯一规则】所有 AI 生成须站在验收标准：多思考、对标市面成熟方案/框架/设计，结合内部 RAG 生成，90%% happy path 开箱可跑。
            生成前检索 RAG；禁止单节点黑盒；不达标自行扩写。高置信结果须沉淀并自动蒸馏进 RAG，供后续检索复用。
            """;

    private AiGenerationAcceptance() {
    }
}
