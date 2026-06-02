package com.zestflow.collector.jdbc.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.collector.jdbc.service.ChainGraphSnapshotService;
import com.zestflow.collector.spi.EventQueryService;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ChainSnapshotDTO;
import com.zestflow.common.model.dto.ChainSnapshotSyncDTO;
import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStatsQuery;
import com.zestflow.common.protocol.ExecutionTrace;
import com.zestflow.common.protocol.PageResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CollectorServerHandler 单元测试 — 路由分发、Token 校验、响应写入
 */
@ExtendWith(MockitoExtension.class)
class CollectorServerHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private EventQueryService eventQueryService;
    @Mock private ChainGraphSnapshotService snapshotService;
    @Mock private ChannelHandlerContext ctx;

    @Captor private ArgumentCaptor<FullHttpResponse> responseCaptor;

    private CollectorServerHandler handler;

    @BeforeEach
    void setUp() {
        // accessToken=null 跳过 Token 校验
        handler = new CollectorServerHandler(eventQueryService, snapshotService, null, null);
    }

    // ==================== 工具方法 ====================

    private FullHttpRequest buildRequest(HttpMethod method, String uri) {
        return buildRequest(method, uri, "");
    }

    private FullHttpRequest buildRequest(HttpMethod method, String uri, String body) {
        ByteBuf buf = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, method, uri, buf);
        req.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        req.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        return req;
    }

    private void invokeHandler(FullHttpRequest request) {
        handler.channelRead0(ctx, request);
    }

    private FullHttpResponse captureResponse() {
        verify(ctx).writeAndFlush(responseCaptor.capture());
        return responseCaptor.getValue();
    }

    private String responseBody(FullHttpResponse resp) {
        return resp.content().toString(CharsetUtil.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private <T> T parseData(String json, Class<T> clazz) throws Exception {
        return MAPPER.readValue(json, clazz);
    }

    // ==================== /collector/health ====================

    @Nested
    class HealthCheck {

        @Test
        void health_returns200() {
            invokeHandler(buildRequest(HttpMethod.GET, "/collector/health"));
            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            assertThat(responseBody(resp)).contains("\"code\":200");
        }

        @Test
        void health_noAuthRequired() {
            handler = new CollectorServerHandler(eventQueryService, snapshotService, "secret", null);
            invokeHandler(buildRequest(HttpMethod.GET, "/collector/health"));
            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
        }
    }

    // ==================== Token 校验 ====================

    @Nested
    class TokenAuth {

        @BeforeEach
        void setUp() {
            handler = new CollectorServerHandler(eventQueryService, snapshotService, "my-secret", null);
        }

        @Test
        void missingToken_returns401() {
            FullHttpRequest req = buildRequest(HttpMethod.POST, "/collector/events/query", "{}");
            invokeHandler(req);
            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.UNAUTHORIZED);
            assertThat(responseBody(resp)).contains("\"code\":401");
        }

        @Test
        void wrongToken_returns401() {
            FullHttpRequest req = buildRequest(HttpMethod.POST, "/collector/events/query", "{}");
            req.headers().set("X-Collector-Token", "wrong");
            invokeHandler(req);
            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.UNAUTHORIZED);
        }

        @Test
        void correctToken_passes() {
            when(eventQueryService.queryEvents(any())).thenReturn(List.of());
            when(eventQueryService.countEvents(any())).thenReturn(0L);

            FullHttpRequest req = buildRequest(HttpMethod.POST, "/collector/events/query", "{}");
            req.headers().set("X-Collector-Token", "my-secret");
            invokeHandler(req);
            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
        }

        @Test
        void emptyTokenBypasses() {
            handler = new CollectorServerHandler(eventQueryService, snapshotService, "", null);
            when(eventQueryService.queryEvents(any())).thenReturn(List.of());
            when(eventQueryService.countEvents(any())).thenReturn(0L);

            FullHttpRequest req = buildRequest(HttpMethod.POST, "/collector/events/query", "{}");
            invokeHandler(req);
            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
        }
    }

    // ==================== 路由：POST /collector/events/query ====================

    @Nested
    class QueryEvents {

        @Test
        void returnsPaginatedResults() throws Exception {
            ChainEvent event = ChainEvent.builder().eventId("evt-1").chainName("test").build();
            when(eventQueryService.queryEvents(any())).thenReturn(List.of(event));
            when(eventQueryService.countEvents(any())).thenReturn(1L);

            String body = MAPPER.writeValueAsString(EventQuery.builder().page(1).pageSize(20).build());
            invokeHandler(buildRequest(HttpMethod.POST, "/collector/events/query", body));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            String respBody = responseBody(resp);
            assertThat(respBody).contains("\"code\":200");
            assertThat(respBody).contains("\"total\":1");
            assertThat(respBody).contains("\"eventId\":\"evt-1\"");
        }

        @Test
        void delegatesQueryToService() throws Exception {
            when(eventQueryService.queryEvents(any())).thenReturn(List.of());
            when(eventQueryService.countEvents(any())).thenReturn(0L);

            EventQuery query = EventQuery.builder()
                    .executionId("exec-1").chainId("chain-1").appCode("demo-app")
                    .status(1).page(1).pageSize(10).build();
            String body = MAPPER.writeValueAsString(query);
            invokeHandler(buildRequest(HttpMethod.POST, "/collector/events/query", body));

            verify(eventQueryService).queryEvents(any());
            verify(eventQueryService).countEvents(any());
        }

        @Test
        void emptyResult_returnsEmptyList() throws Exception {
            when(eventQueryService.queryEvents(any())).thenReturn(List.of());
            when(eventQueryService.countEvents(any())).thenReturn(0L);

            invokeHandler(buildRequest(HttpMethod.POST, "/collector/events/query", "{}"));

            FullHttpResponse resp = captureResponse();
            assertThat(responseBody(resp)).contains("\"list\":[]");
            assertThat(responseBody(resp)).contains("\"total\":0");
        }
    }

    // ==================== 路由：GET /collector/events/{eventId} ====================

    @Nested
    class GetEventById {

        @Test
        void found_returnsEvent() {
            ChainEvent event = ChainEvent.builder().eventId("evt-1").chainName("test").build();
            when(eventQueryService.getById("evt-1")).thenReturn(event);

            invokeHandler(buildRequest(HttpMethod.GET, "/collector/events/evt-1"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            assertThat(responseBody(resp)).contains("\"eventId\":\"evt-1\"");
        }

        @Test
        void notFound_returns404() {
            when(eventQueryService.getById("nonexistent")).thenReturn(null);

            invokeHandler(buildRequest(HttpMethod.GET, "/collector/events/nonexistent"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.NOT_FOUND);
            assertThat(responseBody(resp)).contains("\"code\":404");
        }
    }

    // ==================== 路由：POST /collector/events/stats ====================

    @Nested
    class QueryStats {

        @Test
        void returnsStats() throws Exception {
            EventStats stats = EventStats.builder()
                    .totalCount(100).failCount(10).successRate(90.0).avgCostMs(50.0).build();
            when(eventQueryService.queryStats(any())).thenReturn(stats);

            String body = MAPPER.writeValueAsString(new EventStatsQuery());
            invokeHandler(buildRequest(HttpMethod.POST, "/collector/events/stats", body));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            assertThat(responseBody(resp)).contains("\"totalCount\":100");
        }

        @Test
        void delegatesQueryParams() throws Exception {
            when(eventQueryService.queryStats(any())).thenReturn(EventStats.builder().build());

            EventStatsQuery query = EventStatsQuery.builder()
                    .startTime(1000L).endTime(2000L).appName("demo-app").build();
            String body = MAPPER.writeValueAsString(query);
            invokeHandler(buildRequest(HttpMethod.POST, "/collector/events/stats", body));

            verify(eventQueryService).queryStats(any(EventStatsQuery.class));
        }
    }

    // ==================== 路由：POST /collector/events/executions ====================

    @Nested
    class QueryExecutionTraces {

        @Test
        void returnsPaginatedTraces() throws Exception {
            ExecutionTrace trace = ExecutionTrace.builder().executionId("exec-1").chainName("test").build();
            when(eventQueryService.queryExecutionTraces(any())).thenReturn(List.of(trace));
            when(eventQueryService.countExecutionTraces(any())).thenReturn(1L);

            invokeHandler(buildRequest(HttpMethod.POST, "/collector/events/executions", "{}"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            assertThat(responseBody(resp)).contains("\"executionId\":\"exec-1\"");
        }

        @Test
        void emptyResult() {
            when(eventQueryService.queryExecutionTraces(any())).thenReturn(List.of());
            when(eventQueryService.countExecutionTraces(any())).thenReturn(0L);

            invokeHandler(buildRequest(HttpMethod.POST, "/collector/events/executions", "{}"));

            FullHttpResponse resp = captureResponse();
            assertThat(responseBody(resp)).contains("\"list\":[]");
            assertThat(responseBody(resp)).contains("\"total\":0");
        }
    }

    // ==================== 路由：GET /collector/events/executions/{executionId} ====================

    @Nested
    class GetExecutionTrace {

        @Test
        void found_returnsTrace() {
            ExecutionTrace trace = ExecutionTrace.builder().executionId("exec-1")
                    .chainName("test").status(1).build();
            when(eventQueryService.getExecutionTrace("exec-1")).thenReturn(trace);

            invokeHandler(buildRequest(HttpMethod.GET, "/collector/events/executions/exec-1"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            assertThat(responseBody(resp)).contains("\"executionId\":\"exec-1\"");
        }

        @Test
        void notFound_returns404() {
            when(eventQueryService.getExecutionTrace("nonexistent")).thenReturn(null);

            invokeHandler(buildRequest(HttpMethod.GET, "/collector/events/executions/nonexistent"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.NOT_FOUND);
        }
    }

    // ==================== 路由：POST /collector/snapshots ====================

    @Nested
    class SyncSnapshot {

        @Test
        void success_returnsVersion() throws Exception {
            ChainSnapshotSyncDTO dto = new ChainSnapshotSyncDTO();
            dto.setChainCode("chain-1");
            dto.setGraphData("{}");
            dto.setAppCode("app-a");
            dto.setCreatedBy("admin");
            when(snapshotService.syncSnapshot("chain-1", "{}", "app-a", null, "admin")).thenReturn(5);

            String body = MAPPER.writeValueAsString(dto);
            invokeHandler(buildRequest(HttpMethod.POST, "/collector/snapshots", body));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            assertThat(responseBody(resp)).contains("\"version\":5");
        }

        @Test
        void missingChainCode_returns400() throws Exception {
            ChainSnapshotSyncDTO dto = new ChainSnapshotSyncDTO();
            dto.setChainCode("");

            String body = MAPPER.writeValueAsString(dto);
            invokeHandler(buildRequest(HttpMethod.POST, "/collector/snapshots", body));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
            verify(snapshotService, never()).syncSnapshot(anyString(), anyString(), anyString(), any(), anyString());
        }

        @Test
        void nullChainCode_returns400() throws Exception {
            ChainSnapshotSyncDTO dto = new ChainSnapshotSyncDTO();

            String body = MAPPER.writeValueAsString(dto);
            invokeHandler(buildRequest(HttpMethod.POST, "/collector/snapshots", body));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
        }
    }

    // ==================== 路由：GET /collector/snapshots ====================

    @Nested
    class GetSnapshot {

        @Test
        void found_returnsSnapshot() {
            ChainSnapshotDTO dto = ChainSnapshotDTO.builder()
                    .chainCode("chain-1").version(3).graphData("{}").build();
            when(snapshotService.findSnapshotAt("chain-1", 1000L, null)).thenReturn(dto);

            invokeHandler(buildRequest(HttpMethod.GET, "/collector/snapshots?chainCode=chain-1&timestamp=1000"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            assertThat(responseBody(resp)).contains("\"chainCode\":\"chain-1\"");
        }

        @Test
        void notFound_returns404() {
            when(snapshotService.findSnapshotAt("chain-1", 1000L, null)).thenReturn(null);

            invokeHandler(buildRequest(HttpMethod.GET, "/collector/snapshots?chainCode=chain-1&timestamp=1000"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.NOT_FOUND);
        }

        @Test
        void missingParam_returns400() {
            invokeHandler(buildRequest(HttpMethod.GET, "/collector/snapshots"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
        }

        @Test
        void withTenantId_passesToService() {
            ChainSnapshotDTO dto = ChainSnapshotDTO.builder().chainCode("chain-1").build();
            when(snapshotService.findSnapshotAt("chain-1", 1000L, 99L)).thenReturn(dto);

            invokeHandler(buildRequest(HttpMethod.GET,
                    "/collector/snapshots?chainCode=chain-1&timestamp=1000&tenantId=99"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
        }
    }

    // ==================== 错误处理 ====================

    @Nested
    class ErrorHandling {

        @Test
        void unknownRoute_returns404() {
            invokeHandler(buildRequest(HttpMethod.GET, "/collector/unknown"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.NOT_FOUND);
        }

        @Test
        void methodNotAllowed_returns405() {
            invokeHandler(buildRequest(HttpMethod.DELETE, "/collector/health"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.METHOD_NOT_ALLOWED);
        }

        @Test
        void serviceException_returns500() {
            when(eventQueryService.queryEvents(any())).thenThrow(new RuntimeException("DB error"));

            invokeHandler(buildRequest(HttpMethod.POST, "/collector/events/query", "{}"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            assertThat(responseBody(resp)).contains("\"code\":500");
        }

        @Test
        void invalidJsonBody_returns500() {
            invokeHandler(buildRequest(HttpMethod.POST, "/collector/events/query", "not-json"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        void responseContentTypeIsJson() {
            when(eventQueryService.queryEvents(any())).thenReturn(List.of());
            when(eventQueryService.countEvents(any())).thenReturn(0L);

            invokeHandler(buildRequest(HttpMethod.POST, "/collector/events/query", "{}"));

            FullHttpResponse resp = captureResponse();
            assertThat(resp.headers().get(HttpHeaderNames.CONTENT_TYPE))
                    .contains("application/json");
        }
    }
}
