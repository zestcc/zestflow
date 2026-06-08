package com.zestflow.admin.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.ai.model.vo.AiExplainResponse;
import com.zestflow.admin.ai.model.vo.AiSuggestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 Copilot 事件写入 SSE（text/event-stream）
 */
@Slf4j
public class SseCopilotStreamSink implements AiCopilotStreamSink {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SseEmitter emitter;

    public SseCopilotStreamSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void progress(String step) {
        send("progress", Map.of("step", step));
    }

    @Override
    public void reasoningDelta(String delta) {
        send("reasoning", Map.of("delta", delta));
    }

    @Override
    public void contentDelta(String delta) {
        send("content", Map.of("delta", delta));
    }

    @Override
    public void suggestDone(AiSuggestResponse response) {
        send("done", response);
    }

    @Override
    public void explainDone(AiExplainResponse response) {
        send("done", response);
    }

    @Override
    public void error(String message) {
        send("error", Map.of("message", message != null ? message : "unknown"));
    }

    private void send(String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException e) {
            log.debug("SSE 发送失败 event={}", eventName, e);
        }
    }
}
