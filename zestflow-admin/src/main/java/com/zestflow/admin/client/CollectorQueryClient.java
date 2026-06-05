package com.zestflow.admin.client;

import com.zestflow.common.model.dto.ChainSnapshotDTO;
import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventQueryResult;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStatsQuery;
import com.zestflow.common.protocol.ExecutionTrace;
import com.zestflow.common.protocol.InvocationPayloadDTO;
import com.zestflow.common.protocol.NodeExecutionDetail;
import com.zestflow.common.protocol.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 采集器查询客户端 — 向 Collector REST API 查询事件/轨迹/统计
 * <p>
 * HTTP 防腐层：切换 Collector 通信协议（如 gRPC）只需修改此类。
 */
@Slf4j
public class CollectorQueryClient {

    private final RestTemplate restTemplate;
    private final String accessToken;

    private static final ParameterizedTypeReference<com.zestflow.common.model.Result<PageResult<EventQueryResult>>> EVENT_PAGE_TYPE =
            new ParameterizedTypeReference<com.zestflow.common.model.Result<PageResult<EventQueryResult>>>() {};
    private static final ParameterizedTypeReference<com.zestflow.common.model.Result<EventStats>> EVENT_STATS_TYPE =
            new ParameterizedTypeReference<com.zestflow.common.model.Result<EventStats>>() {};
    private static final ParameterizedTypeReference<com.zestflow.common.model.Result<PageResult<ExecutionTrace>>> EXEC_TRACE_PAGE_TYPE =
            new ParameterizedTypeReference<com.zestflow.common.model.Result<PageResult<ExecutionTrace>>>() {};
    private static final ParameterizedTypeReference<com.zestflow.common.model.Result<ExecutionTrace>> EXEC_TRACE_TYPE =
            new ParameterizedTypeReference<com.zestflow.common.model.Result<ExecutionTrace>>() {};
    private static final ParameterizedTypeReference<com.zestflow.common.model.Result<Void>> RESULT_VOID_TYPE =
            new ParameterizedTypeReference<com.zestflow.common.model.Result<Void>>() {};
    private static final ParameterizedTypeReference<com.zestflow.common.model.Result<NodeExecutionDetail>> NODE_DETAIL_TYPE =
            new ParameterizedTypeReference<com.zestflow.common.model.Result<NodeExecutionDetail>>() {};
    private static final ParameterizedTypeReference<com.zestflow.common.model.Result<InvocationPayloadDTO>> INVOCATION_TYPE =
            new ParameterizedTypeReference<com.zestflow.common.model.Result<InvocationPayloadDTO>>() {};
    private static final ParameterizedTypeReference<com.zestflow.common.model.Result<ChainSnapshotDTO>> SNAPSHOT_TYPE =
            new ParameterizedTypeReference<com.zestflow.common.model.Result<ChainSnapshotDTO>>() {};

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
            log.warn("查询事件失败 baseUrl={} error={}", baseUrl, e.getMessage(), e);
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
            log.warn("查询执行轨迹失败 baseUrl={} error={}", baseUrl, e.getMessage(), e);
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
            log.warn("查询执行轨迹详情失败 baseUrl={} executionId={} error={}", baseUrl, executionId, e.getMessage(), e);
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
            log.warn("查询统计失败 baseUrl={} error={}", baseUrl, e.getMessage(), e);
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

    /**
     * 查询节点执行详情（入参/出参按需加载）
     */
    public NodeExecutionDetail getNodeExecutionDetail(String baseUrl, String executionId,
                                                       String nodeId, String nodeShape) {
        try {
            String url = baseUrl + "/collector/events/executions/" + executionId + "/nodes/" + nodeId;
            if (nodeShape != null && !nodeShape.isBlank()) {
                url += "?nodeShape=" + nodeShape;
            }
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            var resp = restTemplate.exchange(url, HttpMethod.GET, entity, NODE_DETAIL_TYPE);
            if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                return resp.getBody().getData();
            }
        } catch (Exception e) {
            log.warn("查询节点详情失败 executionId={} nodeId={} error={}", executionId, nodeId, e.getMessage(), e);
        }
        return null;
    }

    /**
     * 保存外部调用载荷
     */
    public boolean saveInvocationPayload(String baseUrl, InvocationPayloadDTO dto) {
        try {
            String url = baseUrl + "/collector/invocations";
            HttpEntity<InvocationPayloadDTO> entity = new HttpEntity<>(dto, buildHeaders());
            var resp = restTemplate.exchange(url, HttpMethod.POST, entity, RESULT_VOID_TYPE);
            return resp.getBody() != null && resp.getBody().getCode() == 200;
        } catch (Exception e) {
            log.warn("保存调用载荷失败 invocationId={} error={}", dto.getInvocationId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 查询外部调用载荷
     */
    public InvocationPayloadDTO getInvocationPayload(String baseUrl, String invocationId) {
        try {
            String url = baseUrl + "/collector/invocations/" + invocationId;
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            var resp = restTemplate.exchange(url, HttpMethod.GET, entity, INVOCATION_TYPE);
            if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                return resp.getBody().getData();
            }
        } catch (Exception e) {
            log.warn("查询调用载荷失败 invocationId={} error={}", invocationId, e.getMessage(), e);
        }
        return null;
    }

    /**
     * 查询图数据快照
     */
    public ChainSnapshotDTO getSnapshot(String baseUrl, String chainCode, long timestamp, Long tenantId) {
        try {
            String url = baseUrl + "/collector/snapshots?chainCode=" + chainCode + "&timestamp=" + timestamp
                    + (tenantId != null ? "&tenantId=" + tenantId : "");
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            var resp = restTemplate.exchange(url, HttpMethod.GET, entity, SNAPSHOT_TYPE);
            if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                return resp.getBody().getData();
            }
        } catch (Exception e) {
            log.warn("查询图数据快照失败 chainCode={} timestamp={} error={}", chainCode, timestamp, e.getMessage(), e);
        }
        return null;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null && !accessToken.isEmpty()) {
            headers.set("X-Collector-Token", accessToken);
        }
        return headers;
    }
}
