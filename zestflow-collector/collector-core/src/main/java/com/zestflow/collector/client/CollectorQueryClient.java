package com.zestflow.collector.client;

import com.zestflow.collector.model.dto.EventQuery;
import com.zestflow.collector.model.dto.EventStats;
import com.zestflow.collector.model.dto.EventStatsQuery;
import com.zestflow.collector.model.dto.ExecutionTrace;
import com.zestflow.common.model.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集器查询客户端 — 向 Collector REST API 查询事件/轨迹/统计
 * <p>
 * 对标 executor 端 AdminClient 的设计模式，但方向相反：
 * AdminClient 是 Executor→Admin 的注册客户端，
 * CollectorQueryClient 是 Consumer→Collector 的数据查询客户端。
 */
@Slf4j
public class CollectorQueryClient {

    private final RestTemplate restTemplate;
    private final String accessToken;

    private static final ParameterizedTypeReference<Result<PageResult<EventQueryResult>>> EVENT_PAGE_TYPE =
            new ParameterizedTypeReference<Result<PageResult<EventQueryResult>>>() {};
    private static final ParameterizedTypeReference<Result<EventStats>> EVENT_STATS_TYPE =
            new ParameterizedTypeReference<Result<EventStats>>() {};
    private static final ParameterizedTypeReference<Result<PageResult<ExecutionTrace>>> EXEC_TRACE_PAGE_TYPE =
            new ParameterizedTypeReference<Result<PageResult<ExecutionTrace>>>() {};
    private static final ParameterizedTypeReference<Result<ExecutionTrace>> EXEC_TRACE_TYPE =
            new ParameterizedTypeReference<Result<ExecutionTrace>>() {};
    private static final ParameterizedTypeReference<Result<Void>> RESULT_VOID_TYPE =
            new ParameterizedTypeReference<Result<Void>>() {};

    public CollectorQueryClient(RestTemplate restTemplate, String accessToken) {
        this.restTemplate = restTemplate;
        this.accessToken = accessToken;
    }

    /**
     * 查询事件列表（分页）
     */
    public PageResult<EventQueryResult> queryEvents(String baseUrl, EventQuery query) {
        try {
            String url = baseUrl + "/collector/events/query";
            HttpEntity<EventQuery> entity = new HttpEntity<>(query, buildHeaders());
            var resp = restTemplate.exchange(url, HttpMethod.POST, entity, EVENT_PAGE_TYPE);
            if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                return resp.getBody().getData();
            }
        } catch (Exception e) {
            log.warn("查询事件失败 baseUrl={} error={}", baseUrl, e.getMessage());
        }
        return new PageResult<>(List.of(), 0L, query.getPage(), query.getPageSize());
    }

    /**
     * 查询执行轨迹列表（分页）
     */
    public PageResult<ExecutionTrace> queryExecutionTraces(String baseUrl, EventQuery query) {
        try {
            String url = baseUrl + "/collector/events/executions";
            HttpEntity<EventQuery> entity = new HttpEntity<>(query, buildHeaders());
            var resp = restTemplate.exchange(url, HttpMethod.POST, entity, EXEC_TRACE_PAGE_TYPE);
            if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                return resp.getBody().getData();
            }
        } catch (Exception e) {
            log.warn("查询执行轨迹失败 baseUrl={} error={}", baseUrl, e.getMessage());
        }
        return new PageResult<>(List.of(), 0L, query.getPage(), query.getPageSize());
    }

    /**
     * 查询单条执行轨迹详情
     */
    public ExecutionTrace getExecutionTrace(String baseUrl, String executionId) {
        try {
            String url = baseUrl + "/collector/events/executions/" + executionId;
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            var resp = restTemplate.exchange(url, HttpMethod.GET, entity, EXEC_TRACE_TYPE);
            if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                return resp.getBody().getData();
            }
        } catch (Exception e) {
            log.warn("查询执行轨迹详情失败 baseUrl={} executionId={} error={}", baseUrl, executionId, e.getMessage());
        }
        return null;
    }

    /**
     * 查询事件统计
     */
    public EventStats queryStats(String baseUrl, Long startTime, Long endTime) {
        try {
            EventStatsQuery query = new EventStatsQuery();
            query.setStartTime(startTime);
            query.setEndTime(endTime);
            String url = baseUrl + "/collector/events/stats";
            HttpEntity<EventStatsQuery> entity = new HttpEntity<>(query, buildHeaders());
            var resp = restTemplate.exchange(url, HttpMethod.POST, entity, EVENT_STATS_TYPE);
            if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                return resp.getBody().getData();
            }
        } catch (Exception e) {
            log.warn("查询统计失败 baseUrl={} error={}", baseUrl, e.getMessage());
        }
        return null;
    }

    /**
     * 健康检查
     */
    public boolean healthCheck(String baseUrl) {
        try {
            String url = baseUrl + "/collector/health";
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            var resp = restTemplate.exchange(url, HttpMethod.GET, entity, RESULT_VOID_TYPE);
            return resp.getBody() != null && resp.getBody().getCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null && !accessToken.isEmpty()) {
            headers.set("X-Collector-Token", accessToken);
        }
        return headers;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PageResult<T> {
        private List<T> list;
        private long total;
        private int page;
        private int pageSize;
    }

    @lombok.Data
    public static class EventQueryResult {
        private String eventId;
        private String eventType;
        private String executionId;
        private String chainId;
        private String chainName;
        private String nodeId;
        private String nodeName;
        private String executorId;
        private String appName;
        private String params;
        private String result;
        private String errorMessage;
        private Long costMs;
        private Integer status;
        private Long timestamp;
        private String metadata;
        private String createTime;
    }
}
