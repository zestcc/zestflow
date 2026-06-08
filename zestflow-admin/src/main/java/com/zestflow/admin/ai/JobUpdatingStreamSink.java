package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.vo.AiExplainResponse;
import com.zestflow.admin.ai.model.vo.AiSuggestResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 将流式事件同步写入 Job 表，供轮询/断线续跑
 */
class JobUpdatingStreamSink implements AiCopilotStreamSink {

    private final AiCopilotJobService jobService;
    private final Long jobId;
    private final SseEmitter sseEmitter;

    JobUpdatingStreamSink(AiCopilotJobService jobService, Long jobId) {
        this(jobService, jobId, null);
    }

    JobUpdatingStreamSink(AiCopilotJobService jobService, Long jobId, SseEmitter sseEmitter) {
        this.jobService = jobService;
        this.jobId = jobId;
        this.sseEmitter = sseEmitter;
    }

    @Override
    public void progress(String step) {
        jobService.updateProgress(jobId, step);
        if (sseEmitter != null) {
            new SseCopilotStreamSink(sseEmitter).progress(step);
        }
    }

    @Override
    public void reasoningDelta(String delta) {
        jobService.appendReasoning(jobId, delta);
        if (sseEmitter != null) {
            new SseCopilotStreamSink(sseEmitter).reasoningDelta(delta);
        }
    }

    @Override
    public void contentDelta(String delta) {
        if (sseEmitter != null) {
            new SseCopilotStreamSink(sseEmitter).contentDelta(delta);
        }
    }

    @Override
    public void suggestDone(AiSuggestResponse response) {
        if (sseEmitter != null) {
            new SseCopilotStreamSink(sseEmitter).suggestDone(response);
        }
    }

    @Override
    public void explainDone(AiExplainResponse response) {
        if (sseEmitter != null) {
            new SseCopilotStreamSink(sseEmitter).explainDone(response);
        }
    }

    @Override
    public void error(String message) {
        if (sseEmitter != null) {
            new SseCopilotStreamSink(sseEmitter).error(message);
        }
    }
}
