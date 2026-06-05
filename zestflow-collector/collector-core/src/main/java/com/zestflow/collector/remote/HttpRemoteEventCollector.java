package com.zestflow.collector.remote;

import com.zestflow.collector.http.ZestFlowHttpClient;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.spi.EventCollector;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 远程 HTTP 事件采集 — Executor 无本地 JDBC 采集器时，将事件 POST 到 Collector Netty {@code /collector/events/ingest}。
 */
@Slf4j
public class HttpRemoteEventCollector implements EventCollector {

    private static final String INGEST_PATH = "/collector/events/ingest";

    private final ZestFlowHttpClient httpClient;
    private final String baseUrl;
    private final String accessToken;
    private final ExecutorService sender;

    public HttpRemoteEventCollector(ZestFlowHttpClient httpClient, String baseUrl, String accessToken) {
        this.httpClient = httpClient;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.accessToken = accessToken;
        this.sender = Executors.newSingleThreadExecutor(daemonFactory("zestflow-remote-event-sender"));
    }

    @Override
    public void collect(ChainEvent event) {
        collectBatch(List.of(event));
    }

    @Override
    public void collectBatch(List<ChainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        sender.execute(() -> {
            try {
                httpClient.post(baseUrl + INGEST_PATH, events, authHeaders(), null);
            } catch (Exception e) {
                log.error("远程事件采集失败 size={} url={}", events.size(), baseUrl, e);
            }
        });
    }

    @Override
    public String getName() {
        return "HttpRemoteEventCollector";
    }

    private Map<String, String> authHeaders() {
        if (accessToken == null || accessToken.isBlank()) {
            return Map.of();
        }
        Map<String, String> headers = new HashMap<>(1);
        headers.put("X-Collector-Token", accessToken);
        return headers;
    }

    private static String trimTrailingSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static ThreadFactory daemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
