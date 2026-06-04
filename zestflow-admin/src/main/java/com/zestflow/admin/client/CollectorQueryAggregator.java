package com.zestflow.admin.client;

import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventQueryResult;
import com.zestflow.common.protocol.ExecutionTrace;
import com.zestflow.common.protocol.InvocationPayloadDTO;
import com.zestflow.common.protocol.NodeExecutionDetail;
import com.zestflow.common.protocol.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 多采集器聚合查询 — 并行 fan-out + 去重合并（对标 Elasticsearch 协调节点 / Jaeger 多存储后端查询）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorQueryAggregator {

    private final CollectorQueryClient queryClient;
    private final CollectorRegistryService collectorRegistryService;

    @Value("${zestflow.admin.protocol:http}")
    private String protocol;

    @Value("${zestflow.collector.api-url:}")
    private String fallbackApiUrl;

    private final ExecutorService queryPool = Executors.newFixedThreadPool(
            Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors())),
            r -> {
                Thread t = new Thread(r, "zestflow-collector-aggregate");
                t.setDaemon(true);
                return t;
            });

    public PageResult<EventQueryResult> queryEvents(EventQuery query, String appCode) {
        List<String> baseUrls = resolveCollectorUrls(appCode);
        if (baseUrls.isEmpty()) {
            return emptyPage(query);
        }
        if (baseUrls.size() == 1) {
            return queryClient.queryEvents(baseUrls.get(0), query);
        }
        List<CompletableFuture<PageResult<EventQueryResult>>> futures = baseUrls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> queryClient.queryEvents(url, query), queryPool))
                .toList();
        return mergeEventPages(futures, query);
    }

    public PageResult<ExecutionTrace> queryExecutionTraces(EventQuery query, String appCode) {
        List<String> baseUrls = resolveCollectorUrls(appCode);
        if (baseUrls.isEmpty()) {
            return emptyExecutionPage(query);
        }
        if (baseUrls.size() == 1) {
            return queryClient.queryExecutionTraces(baseUrls.get(0), query);
        }
        List<CompletableFuture<PageResult<ExecutionTrace>>> futures = baseUrls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> queryClient.queryExecutionTraces(url, query), queryPool))
                .toList();
        return mergeExecutionPages(futures, query);
    }

    public ExecutionTrace getExecutionTrace(String executionId, String appCode) {
        for (String url : resolveCollectorUrls(appCode)) {
            ExecutionTrace trace = queryClient.getExecutionTrace(url, executionId);
            if (trace != null) {
                return trace;
            }
        }
        return null;
    }

    public NodeExecutionDetail getNodeExecutionDetail(String executionId, String nodeId,
                                                       String nodeShape, String appCode) {
        for (String url : resolveCollectorUrls(appCode)) {
            NodeExecutionDetail detail = queryClient.getNodeExecutionDetail(
                    url, executionId, nodeId, nodeShape);
            if (detail != null) {
                return detail;
            }
        }
        return null;
    }

    public boolean saveInvocationPayload(InvocationPayloadDTO dto) {
        for (String url : resolveCollectorUrls(dto.getAppCode())) {
            if (queryClient.saveInvocationPayload(url, dto)) {
                return true;
            }
        }
        return false;
    }

    public InvocationPayloadDTO getInvocationPayload(String invocationId, String appCode) {
        for (String url : resolveCollectorUrls(appCode)) {
            InvocationPayloadDTO dto = queryClient.getInvocationPayload(url, invocationId);
            if (dto != null) {
                return dto;
            }
        }
        return null;
    }

    private List<String> resolveCollectorUrls(String appCode) {
        List<String> urls = resolveRegisteredUrls(appCode);
        if (urls.isEmpty() && fallbackApiUrl != null && !fallbackApiUrl.isBlank()) {
            log.info("注册表中无在线采集器，使用配置的 api-url={}", fallbackApiUrl.trim());
            return List.of(fallbackApiUrl.trim());
        }
        return urls;
    }

    private List<String> resolveRegisteredUrls(String appCode) {
        List<CollectorRegistryVO> collectors;
        if (appCode != null && !appCode.isBlank()) {
            collectors = collectorRegistryService.listOnlineByAppCode(appCode);
        } else {
            collectors = collectorRegistryService.listAllOnline();
        }
        return collectors.stream()
                .map(c -> protocol + "://" + c.getCollectorHost() + ":" + c.getCollectorPort())
                .distinct()
                .collect(Collectors.toList());
    }

    private PageResult<EventQueryResult> mergeEventPages(
            List<CompletableFuture<PageResult<EventQueryResult>>> futures, EventQuery query) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        Map<String, EventQueryResult> dedup = new LinkedHashMap<>();
        long totalHint = 0;
        for (CompletableFuture<PageResult<EventQueryResult>> future : futures) {
            PageResult<EventQueryResult> page = future.getNow(emptyPage(query));
            totalHint = Math.max(totalHint, page.getTotal());
            for (EventQueryResult item : page.getList()) {
                if (item.getEventId() != null) {
                    dedup.putIfAbsent(item.getEventId(), item);
                }
            }
        }
        List<EventQueryResult> merged = dedup.values().stream()
                .sorted(Comparator.comparing(EventQueryResult::getTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        int from = Math.max(0, (query.getPage() - 1) * query.getPageSize());
        int to = Math.min(merged.size(), from + query.getPageSize());
        List<EventQueryResult> slice = from >= merged.size() ? List.of() : merged.subList(from, to);
        return new PageResult<>(slice, Math.max(merged.size(), totalHint), query.getPage(), query.getPageSize());
    }

    private PageResult<ExecutionTrace> mergeExecutionPages(
            List<CompletableFuture<PageResult<ExecutionTrace>>> futures, EventQuery query) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        Map<String, ExecutionTrace> dedup = new LinkedHashMap<>();
        long totalHint = 0;
        for (CompletableFuture<PageResult<ExecutionTrace>> future : futures) {
            PageResult<ExecutionTrace> page = future.getNow(emptyExecutionPage(query));
            totalHint = Math.max(totalHint, page.getTotal());
            for (ExecutionTrace item : page.getList()) {
                if (item.getExecutionId() != null) {
                    dedup.putIfAbsent(item.getExecutionId(), item);
                }
            }
        }
        List<ExecutionTrace> merged = dedup.values().stream()
                .sorted(Comparator.comparing(ExecutionTrace::getStartTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        int from = Math.max(0, (query.getPage() - 1) * query.getPageSize());
        int to = Math.min(merged.size(), from + query.getPageSize());
        List<ExecutionTrace> slice = from >= merged.size() ? List.of() : merged.subList(from, to);
        return new PageResult<>(slice, Math.max(merged.size(), totalHint), query.getPage(), query.getPageSize());
    }

    private static PageResult<EventQueryResult> emptyPage(EventQuery query) {
        return new PageResult<>(List.of(), 0L, query.getPage(), query.getPageSize());
    }

    private static PageResult<ExecutionTrace> emptyExecutionPage(EventQuery query) {
        return new PageResult<>(List.of(), 0L, query.getPage(), query.getPageSize());
    }
}
