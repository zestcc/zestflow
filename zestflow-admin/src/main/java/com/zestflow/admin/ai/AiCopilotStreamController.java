package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.dto.AiExplainRequest;
import com.zestflow.admin.ai.model.dto.AiSuggestRequest;
import com.zestflow.admin.config.AiPlatformConfig;
import com.zestflow.admin.service.TenantAppContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Copilot SSE 流式 API（对标 OpenAI/Cursor streaming）
 */
@Slf4j
@RestController
@RequestMapping("/ai/stream")
@RequiredArgsConstructor
public class AiCopilotStreamController {

    private final AiCopilotPipeline pipeline;
    private final AiPlatformConfig aiPlatformConfig;
    private final TenantAppContext tenantAppContext;

    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(
            Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors())),
            r -> {
                Thread t = new Thread(r, "zestflow-ai-copilot-stream");
                t.setDaemon(true);
                return t;
            });

    @PostMapping(value = "/design/suggest", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter suggestStream(@RequestBody AiSuggestRequest request) {
        requireAppEditor(request.getAppCode());
        return runStream(emitter -> pipeline.suggest(request, new SseCopilotStreamSink(emitter)));
    }

    @PostMapping(value = "/design/explain", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter explainStream(@RequestBody AiExplainRequest request) {
        requireAppEditor(request.getAppCode());
        return runStream(emitter -> pipeline.explain(request, new SseCopilotStreamSink(emitter)));
    }

    private SseEmitter runStream(StreamTask task) {
        long timeout = Math.max(60_000L, aiPlatformConfig.getSseTimeoutMs());
        SseEmitter emitter = new SseEmitter(timeout);
        AiCopilotRequestContext ctx = new AiCopilotRequestContext();
        streamExecutor.execute(() -> {
            ctx.apply();
            try {
                task.run(emitter);
                emitter.complete();
            } catch (Exception e) {
                log.warn("Copilot SSE 任务失败", e);
                try {
                    new SseCopilotStreamSink(emitter).error(e.getMessage());
                } catch (Exception ignored) {
                    // ignore
                }
                emitter.completeWithError(e);
            } finally {
                ctx.clear();
            }
        });
        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> log.debug("SSE 客户端断开", ex));
        return emitter;
    }

    private void requireAppEditor(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return;
        }
        if (!tenantAppContext.hasEditPermission(appCode)) {
            throw new com.zestflow.common.exception.BizException(
                    com.zestflow.admin.constant.ErrorCode.PERMISSION_DENIED);
        }
    }

    @FunctionalInterface
    private interface StreamTask {
        void run(SseEmitter emitter) throws Exception;
    }
}
