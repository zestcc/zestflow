package com.zestflow.admin.service.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.client.CollectorQueryAggregator;
import com.zestflow.admin.config.LogLiveStreamProperties;
import com.zestflow.common.protocol.ExecutionTrace;
import com.zestflow.common.protocol.ExecutionTraceSupport;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行轨迹 SSE / WebSocket 推送 — 轮询 Collector 聚合结果，对标日志页实时刷新。
 */
@Slf4j
@Service
public class ExecutionLiveStreamService {

    private final CollectorQueryAggregator collectorQueryAggregator;
    private final ObjectMapper objectMapper;
    private final LogLiveStreamProperties properties;

    private final ExecutorService streamExecutor;

    public ExecutionLiveStreamService(CollectorQueryAggregator collectorQueryAggregator,
                                      ObjectMapper objectMapper,
                                      LogLiveStreamProperties properties) {
        this.collectorQueryAggregator = collectorQueryAggregator;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.streamExecutor = Executors.newFixedThreadPool(
                Math.max(1, properties.getPoolSize()),
                r -> {
                    Thread t = new Thread(r, "zestflow-log-live-stream");
                    t.setDaemon(true);
                    return t;
                });
    }

    public SseEmitter stream(String executionId, String appCode) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeoutMs());
        streamExecutor.execute(() -> pumpTraceEvents(executionId, appCode, new ExecutionTraceStreamCallback() {
            @Override
            public void send(String eventName, Object payload) throws Exception {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
            }

            @Override
            public void complete() {
                emitter.complete();
            }

            @Override
            public void completeWithError(Exception error) {
                emitter.completeWithError(error);
            }
        }));
        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> log.debug("SSE 客户端断开 executionId={}", executionId));
        return emitter;
    }

    /** WebSocket 传输 — 与 SSE 共用轮询引擎。 */
    public void streamOverWebSocket(WebSocketSession session, String executionId, String appCode) {
        streamExecutor.execute(() -> pumpTraceEvents(executionId, appCode, new ExecutionTraceStreamCallback() {
            @Override
            public void send(String eventName, Object payload) throws Exception {
                if (!session.isOpen()) {
                    throw new IllegalStateException("websocket closed");
                }
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("event", eventName);
                envelope.put("data", payload);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
            }

            @Override
            public void complete() {
                closeQuietly(session);
            }

            @Override
            public void completeWithError(Exception error) {
                log.debug("WebSocket 流结束 executionId={}", executionId, error);
                closeQuietly(session);
            }
        }));
    }

    void pumpTraceEvents(String executionId, String appCode, ExecutionTraceStreamCallback callback) {
        AtomicInteger lastFingerprint = new AtomicInteger(Integer.MIN_VALUE);
        try {
            callback.send("connected", Map.of("executionId", executionId));
            while (true) {
                ExecutionTrace trace = collectorQueryAggregator.getExecutionTrace(executionId, appCode);
                if (trace != null) {
                    int fp = ExecutionTraceSupport.fingerprint(trace);
                    if (fp != lastFingerprint.get()) {
                        lastFingerprint.set(fp);
                        callback.send("trace", trace);
                    }
                    if (ExecutionTraceSupport.isTerminal(trace)) {
                        callback.send("done", Map.of("executionId", executionId));
                        callback.complete();
                        return;
                    }
                } else {
                    callback.send("waiting", Map.of("executionId", executionId));
                }
                Thread.sleep(properties.getPollIntervalMs());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("执行轨迹流结束 executionId={}", executionId, e);
            try {
                callback.send("error", Map.of("message", e.getMessage() != null ? e.getMessage() : "stream error"));
            } catch (Exception ignored) {
                // ignore
            }
            callback.completeWithError(e);
        }
    }

    @PreDestroy
    void shutdownStreamExecutor() {
        streamExecutor.shutdown();
        try {
            if (!streamExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                streamExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            streamExecutor.shutdownNow();
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (Exception ignored) {
            // ignore
        }
    }
}
