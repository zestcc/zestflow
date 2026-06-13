package com.zestflow.admin.service.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.client.CollectorQueryAggregator;
import com.zestflow.common.protocol.ExecutionTrace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行轨迹 SSE 推送 — 轮询 Collector 聚合结果，对标日志页实时刷新（轻量替代 WebSocket）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionLiveStreamService {

    private static final long POLL_INTERVAL_MS = 2_000L;
    private static final long SSE_TIMEOUT_MS = 600_000L;

    private final CollectorQueryAggregator collectorQueryAggregator;
    private final ObjectMapper objectMapper;

    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(
            Math.min(4, Math.max(2, Runtime.getRuntime().availableProcessors())),
            r -> {
                Thread t = new Thread(r, "zestflow-log-live-stream");
                t.setDaemon(true);
                return t;
            });

    public SseEmitter stream(String executionId, String appCode) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicInteger lastFingerprint = new AtomicInteger(Integer.MIN_VALUE);

        streamExecutor.execute(() -> {
            try {
                sendJson(emitter, "connected", Map.of("executionId", executionId));
                while (true) {
                    ExecutionTrace trace = collectorQueryAggregator.getExecutionTrace(executionId, appCode);
                    if (trace != null) {
                        int fp = ExecutionTraceLiveSupport.fingerprint(trace);
                        if (fp != lastFingerprint.get()) {
                            lastFingerprint.set(fp);
                            sendJson(emitter, "trace", trace);
                        }
                        if (ExecutionTraceLiveSupport.isTerminal(trace)) {
                            sendJson(emitter, "done", Map.of("executionId", executionId));
                            emitter.complete();
                            return;
                        }
                    } else {
                        sendJson(emitter, "waiting", Map.of("executionId", executionId));
                    }
                    Thread.sleep(POLL_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.debug("执行轨迹 SSE 结束 executionId={}", executionId, e);
                try {
                    sendJson(emitter, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "stream error"));
                } catch (Exception ignored) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> log.debug("SSE 客户端断开 executionId={}", executionId));
        return emitter;
    }

    private void sendJson(SseEmitter emitter, String eventName, Object payload) throws Exception {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
    }
}
