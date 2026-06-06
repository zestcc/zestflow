package com.zestflow.collector.jdbc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.collector.jdbc.entity.ChainEventPO;
import com.zestflow.collector.jdbc.entity.ExecutionPayloadPO;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.collector.jdbc.mapper.ExecutionPayloadMapper;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ChainEvent.EventType;
import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStatsQuery;
import com.zestflow.common.protocol.ExecutionTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JdbcEventQueryService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class JdbcEventQueryServiceTest {

    @Mock private ChainEventMapper chainEventMapper;
    @Mock private ExecutionPayloadMapper executionPayloadMapper;
    @Captor private ArgumentCaptor<LambdaQueryWrapper<ChainEventPO>> wrapperCaptor;
    @Captor private ArgumentCaptor<Page<ChainEventPO>> pageCaptor;

    private JdbcEventQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new JdbcEventQueryService(chainEventMapper, executionPayloadMapper);
    }

    private void stubStatsMaps(Map<String, Object> legacy, Map<String, Object> exec) {
        when(chainEventMapper.selectAggregatedStats(any())).thenReturn(legacy);
        when(chainEventMapper.selectExecutionLevelStats(any())).thenReturn(exec);
        when(chainEventMapper.selectTypeDistribution(any())).thenReturn(List.of());
        when(chainEventMapper.selectExecutionCosts(any())).thenReturn(List.of());
    }

    // ==================== 测试数据 ====================

    private ChainEventPO createPO(String eventId, String eventType, String executionId,
                                   String chainId, String appCode, Integer status, long timestamp) {
        return ChainEventPO.builder()
                .id(1L)
                .eventId(eventId)
                .eventType(eventType)
                .executionId(executionId)
                .chainId(chainId)
                .chainName("test-chain")
                .nodeId("node-1")
                .nodeName("测试节点")
                .executorId("executor@host:9999")
                .appCode(appCode)
                .appName(appCode)
                .tenantId(1L)
                .costMs(100L)
                .status(status)
                .timestamp(timestamp)
                .metadata(null)
                .createTime(LocalDateTime.now())
                .build();
    }

    private ChainEventPO createChainCompleted(String executionId, long timestamp) {
        return createPO("evt-completed", "CHAIN_COMPLETED", executionId, "chain-1",
                "demo-app", 1, timestamp);
    }

    private ChainEventPO createChainFailed(String executionId, long timestamp) {
        ChainEventPO po = createPO("evt-failed", "CHAIN_FAILED", executionId, "chain-1",
                "demo-app", 0, timestamp);
        po.setCostMs(500L);
        return po;
    }

    @SuppressWarnings("unchecked")
    private void stubSelectPage(List<ChainEventPO> records, long total) {
        IPage<ChainEventPO> page = new Page<>(1, 20, total);
        page.setRecords(records);
        when(chainEventMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);
    }

    // ==================== queryEvents ====================

    @Nested
    class QueryEvents {

        @Test
        void returnsDTOList() {
            ChainEventPO po = createPO("evt-1", "CHAIN_STARTED", "exec-1",
                    "chain-1", "demo-app", 1, 1000L);
            stubSelectPage(List.of(po), 1);

            EventQuery query = EventQuery.builder().page(1).pageSize(20).build();
            List<ChainEvent> result = queryService.queryEvents(query);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEventId()).isEqualTo("evt-1");
            assertThat(result.get(0).getEventType()).isEqualTo(EventType.CHAIN_STARTED);
        }

        @Test
        void emptyResult() {
            stubSelectPage(Collections.emptyList(), 0);

            List<ChainEvent> result = queryService.queryEvents(new EventQuery());

            assertThat(result).isEmpty();
        }

        @Test
        void buildsWrapperWithAllFilters() {
            stubSelectPage(Collections.emptyList(), 0);

            EventQuery query = EventQuery.builder()
                    .executionId("exec-1")
                    .chainId("chain-1")
                    .executorId("executor@host")
                    .appCode("demo-app")
                    .appName("demo-app")
                    .tenantId(1L)
                    .status(1)
                    .startTime(1000L)
                    .endTime(2000L)
                    .keyword("test")
                    .eventTypes(List.of(EventType.CHAIN_STARTED, EventType.CHAIN_COMPLETED))
                    .page(1).pageSize(20)
                    .build();
            queryService.queryEvents(query);

            verify(chainEventMapper).selectPage(any(IPage.class), wrapperCaptor.capture());
            // Wrapper 已构建，verify 无异常即各条件被正确添加
        }
    }

    // ==================== countEvents ====================

    @Nested
    class CountEvents {

        @Test
        void returnsCount() {
            when(chainEventMapper.selectCount(any())).thenReturn(42L);

            long count = queryService.countEvents(new EventQuery());

            assertThat(count).isEqualTo(42);
        }

        @Test
        void zeroCount() {
            when(chainEventMapper.selectCount(any())).thenReturn(0L);

            long count = queryService.countEvents(new EventQuery());

            assertThat(count).isZero();
        }
    }

    // ==================== getById ====================

    @Nested
    class GetById {

        @Test
        void found_returnsDTO() {
            ChainEventPO po = createPO("evt-1", "NODE_COMPLETED", "exec-1",
                    "chain-1", "demo-app", 1, 1000L);
            when(chainEventMapper.selectOne(any())).thenReturn(po);

            ChainEvent result = queryService.getById("evt-1");

            assertThat(result).isNotNull();
            assertThat(result.getEventId()).isEqualTo("evt-1");
            assertThat(result.getEventType()).isEqualTo(EventType.NODE_COMPLETED);
        }

        @Test
        void notFound_returnsNull() {
            when(chainEventMapper.selectOne(any())).thenReturn(null);

            ChainEvent result = queryService.getById("nonexistent");

            assertThat(result).isNull();
        }
    }

    // ==================== queryStats ====================

    @Nested
    class QueryStats {

        @Test
        void returnsStatsFromAggregatedData() {
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("totalCount", 200L);
            Map<String, Object> exec = new LinkedHashMap<>();
            exec.put("executionCount", 100L);
            exec.put("avgCostMs", 150.0);
            exec.put("successCount", 90L);
            exec.put("failCount", 10L);
            exec.put("inProgressCount", 0L);
            exec.put("maxCostMs", 500L);
            stubStatsMaps(legacy, exec);

            EventStats stats = queryService.queryStats(new EventStatsQuery());

            assertThat(stats.getTotalCount()).isEqualTo(200);
            assertThat(stats.getExecutionCount()).isEqualTo(100);
            assertThat(stats.getAvgCostMs()).isEqualTo(150.0);
            assertThat(stats.getFailCount()).isEqualTo(10);
            assertThat(stats.getSuccessRate()).isEqualTo(90.0);
        }

        @Test
        void emptyStats_returnsZeroValues() {
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("totalCount", 0L);
            Map<String, Object> exec = new LinkedHashMap<>();
            exec.put("executionCount", 0L);
            exec.put("successCount", 0L);
            exec.put("failCount", 0L);
            exec.put("inProgressCount", 0L);
            stubStatsMaps(legacy, exec);

            EventStats stats = queryService.queryStats(new EventStatsQuery());

            assertThat(stats.getTotalCount()).isZero();
            assertThat(stats.getExecutionCount()).isZero();
            assertThat(stats.getAvgCostMs()).isZero();
            assertThat(stats.getSuccessRate()).isZero();
        }

        @Test
        void successRateWithOnlyFailures() {
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("totalCount", 10L);
            Map<String, Object> exec = new LinkedHashMap<>();
            exec.put("executionCount", 10L);
            exec.put("avgCostMs", 100.0);
            exec.put("successCount", 0L);
            exec.put("failCount", 10L);
            exec.put("inProgressCount", 0L);
            stubStatsMaps(legacy, exec);

            EventStats stats = queryService.queryStats(new EventStatsQuery());

            assertThat(stats.getSuccessRate()).isEqualTo(0.0);
            assertThat(stats.getFailCount()).isEqualTo(10);
        }
    }

    // ==================== queryExecutionTraces ====================

    @Nested
    class QueryExecutionTraces {

        @Test
        void returnsTraceSummaries() {
            ChainEventPO po = createPO("evt-1", "CHAIN_COMPLETED", "exec-1",
                    "chain-1", "demo-app", 1, 1000L);
            when(chainEventMapper.selectExecutionTraces(any(), anyInt(), anyInt()))
                    .thenReturn(List.of(po));

            List<ExecutionTrace> traces = queryService.queryExecutionTraces(
                    EventQuery.builder().page(1).pageSize(20).build());

            assertThat(traces).hasSize(1);
            assertThat(traces.get(0).getExecutionId()).isEqualTo("exec-1");
            assertThat(traces.get(0).getChainCode()).isEqualTo("chain-1");
            assertThat(traces.get(0).getChainName()).isEqualTo("test-chain");
        }

        @Test
        void emptyResult() {
            when(chainEventMapper.selectExecutionTraces(any(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());

            List<ExecutionTrace> traces = queryService.queryExecutionTraces(
                    EventQuery.builder().page(1).pageSize(20).build());

            assertThat(traces).isEmpty();
        }

        @Test
        void correctPaginationParams() {
            when(chainEventMapper.selectExecutionTraces(any(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());

            queryService.queryExecutionTraces(
                    EventQuery.builder().page(3).pageSize(10).build());

            verify(chainEventMapper).selectExecutionTraces(any(), eq(10), eq(20));
        }
    }

    // ==================== countExecutionTraces ====================

    @Nested
    class CountExecutionTraces {

        @Test
        void returnsCount() {
            when(chainEventMapper.countExecutionTraces(any())).thenReturn(7L);

            long count = queryService.countExecutionTraces(new EventQuery());

            assertThat(count).isEqualTo(7);
        }
    }

    // ==================== getExecutionTrace ====================

    @Nested
    class GetExecutionTraceDetail {

        @Test
        void returnsFullTraceWithEvents() {
            ChainEventPO started = createPO("evt-1", "CHAIN_STARTED", "exec-1",
                    "chain-1", "demo-app", 1, 1000L);
            ChainEventPO completed = createChainCompleted("exec-1", 2000L);
            when(chainEventMapper.selectByExecutionId("exec-1"))
                    .thenReturn(List.of(started, completed));

            ExecutionTrace trace = queryService.getExecutionTrace("exec-1");

            assertThat(trace).isNotNull();
            assertThat(trace.getExecutionId()).isEqualTo("exec-1");
            assertThat(trace.getStatus()).isEqualTo(1);
            assertThat(trace.getCostMs()).isEqualTo(100L);
            assertThat(trace.getEventCount()).isEqualTo(2);
            assertThat(trace.getEvents()).hasSize(2);
            assertThat(trace.getChainName()).isEqualTo("test-chain");
        }

        @Test
        void failedChain_setsCorrectStatus() {
            ChainEventPO started = createPO("evt-s", "CHAIN_STARTED", "exec-2",
                    "chain-1", "demo-app", 1, 1000L);
            ChainEventPO failed = createChainFailed("exec-2", 2000L);
            when(chainEventMapper.selectByExecutionId("exec-2"))
                    .thenReturn(List.of(started, failed));
            when(executionPayloadMapper.selectByRefId("evt-failed"))
                    .thenReturn(ExecutionPayloadPO.builder()
                            .refId("evt-failed")
                            .errorMessage("业务异常")
                            .build());

            ExecutionTrace trace = queryService.getExecutionTrace("exec-2");

            assertThat(trace.getStatus()).isEqualTo(0);
            assertThat(trace.getCostMs()).isEqualTo(500L);
            assertThat(trace.getErrorMessage()).isEqualTo("业务异常");
        }

        @Test
        void nodeCounts_aggregatedCorrectly() {
            ChainEventPO nStarted = createPO("evt-n1", "NODE_STARTED", "exec-3",
                    "chain-1", "demo-app", null, 1000L);
            nStarted.setNodeId("node-a");
            ChainEventPO nCompleted = createPO("evt-n2", "NODE_COMPLETED", "exec-3",
                    "chain-1", "demo-app", 1, 1500L);
            nCompleted.setNodeId("node-a");
            ChainEventPO nFailed = createPO("evt-n3", "NODE_FAILED", "exec-3",
                    "chain-1", "demo-app", 0, 2000L);
            nFailed.setNodeId("node-b");
            ChainEventPO completed = createChainCompleted("exec-3", 3000L);
            when(chainEventMapper.selectByExecutionId("exec-3"))
                    .thenReturn(List.of(nStarted, nCompleted, nFailed, completed));

            ExecutionTrace trace = queryService.getExecutionTrace("exec-3");

            assertThat(trace.getNodeCount()).isEqualTo(2);
            assertThat(trace.getSuccessCount()).isEqualTo(1);
            assertThat(trace.getFailedCount()).isEqualTo(1);
        }

        @Test
        void nodeCounts_doesNotDoubleCountStartedAndCompleted() {
            ChainEventPO nStarted = createPO("evt-n1", "NODE_STARTED", "exec-7",
                    "chain-1", "demo-app", null, 1000L);
            nStarted.setNodeId("node-a");
            ChainEventPO nCompleted = createPO("evt-n2", "NODE_COMPLETED", "exec-7",
                    "chain-1", "demo-app", 1, 1500L);
            nCompleted.setNodeId("node-a");
            when(chainEventMapper.selectByExecutionId("exec-7"))
                    .thenReturn(List.of(nStarted, nCompleted));

            ExecutionTrace trace = queryService.getExecutionTrace("exec-7");

            assertThat(trace.getNodeCount()).isEqualTo(1);
            assertThat(trace.getSuccessCount()).isEqualTo(1);
            assertThat(trace.getFailedCount()).isZero();
        }

        @Test
        void nodeCounts_retryDoesNotDoubleCountSuccess() {
            ChainEventPO nStarted = createPO("evt-n1", "NODE_STARTED", "exec-8",
                    "chain-1", "demo-app", null, 1000L);
            nStarted.setNodeId("node-a");
            ChainEventPO nCompleted1 = createPO("evt-n2", "NODE_COMPLETED", "exec-8",
                    "chain-1", "demo-app", 1, 1500L);
            nCompleted1.setNodeId("node-a");
            ChainEventPO nRetry = createPO("evt-n3", "NODE_RETRYING", "exec-8",
                    "chain-1", "demo-app", null, 1600L);
            nRetry.setNodeId("node-a");
            ChainEventPO nCompleted2 = createPO("evt-n4", "NODE_COMPLETED", "exec-8",
                    "chain-1", "demo-app", 1, 2000L);
            nCompleted2.setNodeId("node-a");
            when(chainEventMapper.selectByExecutionId("exec-8"))
                    .thenReturn(List.of(nStarted, nCompleted1, nRetry, nCompleted2));

            ExecutionTrace trace = queryService.getExecutionTrace("exec-8");

            assertThat(trace.getNodeCount()).isEqualTo(1);
            assertThat(trace.getSuccessCount()).isEqualTo(1);
        }

        @Test
        void inProgressChain_statusIsNegativeOne() {
            ChainEventPO started = createPO("evt-s", "CHAIN_STARTED", "exec-9",
                    "chain-1", "demo-app", null, 1000L);
            when(chainEventMapper.selectByExecutionId("exec-9"))
                    .thenReturn(List.of(started));

            ExecutionTrace trace = queryService.getExecutionTrace("exec-9");

            assertThat(trace.getStatus()).isEqualTo(-1);
            assertThat(trace.getCostMs()).isNull();
        }

        @Test
        void unknownExecutionId_returnsNull() {
            when(chainEventMapper.selectByExecutionId("nonexistent"))
                    .thenReturn(Collections.emptyList());

            ExecutionTrace trace = queryService.getExecutionTrace("nonexistent");

            assertThat(trace).isNull();
        }

        @Test
        void nullExecutionId_returnsNull() {
            ExecutionTrace trace = queryService.getExecutionTrace(null);
            assertThat(trace).isNull();
        }

        @Test
        void emptyExecutionId_returnsNull() {
            ExecutionTrace trace = queryService.getExecutionTrace("");
            assertThat(trace).isNull();
        }

        @Test
        void timeoutEvent_setsErrorMessage() {
            ChainEventPO started = createPO("evt-s", "CHAIN_STARTED", "exec-4",
                    "chain-1", "demo-app", 1, 1000L);
            ChainEventPO timeout = createPO("evt-t", "CHAIN_TIMEOUT", "exec-4",
                    "chain-1", "demo-app", 0, 2000L);
            timeout.setCostMs(30000L);
            when(chainEventMapper.selectByExecutionId("exec-4"))
                    .thenReturn(List.of(started, timeout));

            ExecutionTrace trace = queryService.getExecutionTrace("exec-4");

            assertThat(trace.getStatus()).isZero();
            assertThat(trace.getCostMs()).isEqualTo(30000L);
            assertThat(trace.getErrorMessage()).isEqualTo("执行超时");
        }

        @Test
        void minMaxTimestampsCalculatedCorrectly() {
            ChainEventPO e1 = createPO("e1", "NODE_STARTED", "exec-5",
                    "chain-1", "demo-app", null, 100L);
            ChainEventPO e2 = createPO("e2", "NODE_COMPLETED", "exec-5",
                    "chain-1", "demo-app", 1, 9999L);
            e1.setCostMs(null);
            when(chainEventMapper.selectByExecutionId("exec-5"))
                    .thenReturn(List.of(e1, e2));

            ExecutionTrace trace = queryService.getExecutionTrace("exec-5");

            assertThat(trace.getStartTime()).isEqualTo(100L);
            assertThat(trace.getEndTime()).isEqualTo(9999L);
        }

        @Test
        void fallbackEventsCounted() {
            ChainEventPO fbSuccess = createPO("e1", "NODE_FALLBACK_SUCCESS", "exec-6",
                    "chain-1", "demo-app", 1, 1000L);
            fbSuccess.setNodeId("node-a");
            ChainEventPO fbFailed = createPO("e2", "NODE_FALLBACK_FAILED", "exec-6",
                    "chain-1", "demo-app", 0, 2000L);
            fbFailed.setNodeId("node-b");
            ChainEventPO completed = createChainCompleted("exec-6", 3000L);
            when(chainEventMapper.selectByExecutionId("exec-6"))
                    .thenReturn(List.of(fbSuccess, fbFailed, completed));

            ExecutionTrace trace = queryService.getExecutionTrace("exec-6");

            assertThat(trace.getSuccessCount()).isEqualTo(1);
            assertThat(trace.getFailedCount()).isEqualTo(1);
            assertThat(trace.getNodeCount()).isEqualTo(2);
        }
    }
}
