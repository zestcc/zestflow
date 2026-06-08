package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.vo.AiExplainResponse;
import com.zestflow.admin.ai.model.vo.AiSuggestResponse;

/**
 * Copilot SSE 事件下沉接口（对标 ChatGPT/Cursor 流式 UX）
 */
public interface AiCopilotStreamSink {

    /** 流水线步骤：RAG / 校验 / 修复 */
    void progress(String step);

    /** 模型 reasoning 增量 */
    void reasoningDelta(String delta);

    /** 正文/JSON 增量 */
    void contentDelta(String delta);

    /** suggest 完成 */
    void suggestDone(AiSuggestResponse response);

    /** explain 完成 */
    void explainDone(AiExplainResponse response);

    /** 失败 */
    void error(String message);

    /** 同步调用时空实现 */
    static AiCopilotStreamSink noop() {
        return new AiCopilotStreamSink() {
            @Override public void progress(String step) { }
            @Override public void reasoningDelta(String delta) { }
            @Override public void contentDelta(String delta) { }
            @Override public void suggestDone(AiSuggestResponse response) { }
            @Override public void explainDone(AiExplainResponse response) { }
            @Override public void error(String message) { }
        };
    }
}
