package com.zestflow.collector.jdbc.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.collector.jdbc.metrics.CollectorMetricsProvider;
import com.zestflow.collector.jdbc.service.ChainGraphSnapshotService;
import com.zestflow.collector.spi.EventQueryService;
import com.zestflow.collector.spi.InvocationPayloadService;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.spi.EventCollector;
import com.zestflow.common.protocol.InvocationPayloadDTO;
import com.zestflow.common.protocol.NodeExecutionDetail;
import com.zestflow.common.model.dto.ChainSnapshotDTO;
import com.zestflow.common.model.dto.ChainSnapshotSyncDTO;
import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStatsQuery;
import com.zestflow.common.protocol.ExecutionTrace;
import com.zestflow.common.protocol.LogAnalyticsQuery;
import com.zestflow.common.protocol.PageResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * Collector Netty HTTP 处理器 — 替代 Spring MVC Controller
 * <p>
 * 对标 ExecutorServer 的 ServerHandler 模式，所有 Collector REST 端点通过独立的
 * Netty 端口（zestflow.collector.registry.port）暴露，供 Admin 查询事件/轨迹/快照。
 */
@Slf4j
@ChannelHandler.Sharable
public class CollectorServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EventQueryService eventQueryService;
    private final InvocationPayloadService invocationPayloadService;
    private final ChainGraphSnapshotService snapshotService;
    private final EventCollector eventCollector;
    private final String accessToken;
    private final ExecutorService queryExecutor;
    private final CollectorMetricsProvider metricsProvider;

    public CollectorServerHandler(EventQueryService eventQueryService,
                                  InvocationPayloadService invocationPayloadService,
                                  ChainGraphSnapshotService snapshotService,
                                  EventCollector eventCollector,
                                  String accessToken,
                                  ExecutorService queryExecutor,
                                  CollectorMetricsProvider metricsProvider) {
        this.eventQueryService = eventQueryService;
        this.invocationPayloadService = invocationPayloadService;
        this.snapshotService = snapshotService;
        this.eventCollector = eventCollector;
        this.accessToken = accessToken;
        this.queryExecutor = queryExecutor;
        this.metricsProvider = metricsProvider != null ? metricsProvider : noopMetricsProvider();
    }

    private static CollectorMetricsProvider noopMetricsProvider() {
        return new CollectorMetricsProvider(null, new com.zestflow.collector.jdbc.config.CollectorProperties());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (request.method() != HttpMethod.GET && request.method() != HttpMethod.POST) {
            writeResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED,
                    toJson(Result.fail(405, "Method not allowed")));
            return;
        }

        String uri = request.uri();
        String body = request.content().toString(CharsetUtil.UTF_8);
        log.debug("Collector请求 method={} uri={}", request.method(), uri);

        try {
            if (!dispatchApiRoute(ctx, request, request.method(), uri, body)) {
                writeResponse(ctx, HttpResponseStatus.NOT_FOUND,
                        toJson(Result.fail(404, "NOT_FOUND", "Not found: " + uri)));
            }
        } catch (Exception e) {
            log.error("请求处理失败 uri={}", uri, e);
            writeResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    toJson(Result.fail(500, e.getMessage())));
        }
    }

    /**
     * API 路由分发
     */
    private boolean dispatchApiRoute(ChannelHandlerContext ctx, FullHttpRequest request,
                                      HttpMethod method, String uri, String body) throws Exception {
        String noQueryUri = stripQuery(uri);
        String[] parts = noQueryUri.split("/");
        // parts[0] = "", parts[1] = "collector", parts[2] = "events"/"snapshots"/"health", ...

        // GET /collector/health
        if (parts.length == 3 && "health".equals(parts[2]) && method == HttpMethod.GET) {
            writeResponse(ctx, HttpResponseStatus.OK, toJson(Result.success(metricsProvider.healthDetails())));
            return true;
        }

        // Token 校验（非 /health 请求）
        if (!checkToken(request)) {
            writeResponse(ctx, HttpResponseStatus.UNAUTHORIZED,
                    toJson(Result.fail(401, "UNAUTHORIZED", "Invalid collector token")));
            return true;
        }

        // POST /collector/events/query
        if (parts.length == 4 && "events".equals(parts[2]) && "query".equals(parts[3])
                && method == HttpMethod.POST) {
            EventQuery query = MAPPER.readValue(body, EventQuery.class);
            runBlockingQuery(ctx, () -> {
                List<com.zestflow.common.model.dto.ChainEvent> list = eventQueryService.queryEvents(query);
                long total = eventQueryService.countEvents(query);
                return toJson(Result.success(new PageResult<>(list, total, query.getPage(), query.getPageSize())));
            });
            return true;
        }

        // POST /collector/events/stats
        if (parts.length == 4 && "events".equals(parts[2]) && "stats".equals(parts[3])
                && method == HttpMethod.POST) {
            EventStatsQuery query = MAPPER.readValue(body, EventStatsQuery.class);
            runBlockingQuery(ctx, () -> toJson(Result.success(eventQueryService.queryStats(query))));
            return true;
        }

        // POST /collector/events/analytics/*
        if (parts.length >= 5 && "events".equals(parts[2]) && "analytics".equals(parts[3])
                && method == HttpMethod.POST) {
            LogAnalyticsQuery query = MAPPER.readValue(body, LogAnalyticsQuery.class);
            if ("trend".equals(parts[4])) {
                runBlockingQuery(ctx, () -> toJson(Result.success(eventQueryService.queryExecutionTrend(query))));
                return true;
            }
            if (parts.length >= 6 && "rankings".equals(parts[4])) {
                if ("chains".equals(parts[5])) {
                    runBlockingQuery(ctx, () -> toJson(Result.success(eventQueryService.queryChainRanking(query))));
                    return true;
                }
                if ("executors".equals(parts[5])) {
                    runBlockingQuery(ctx, () -> toJson(Result.success(eventQueryService.queryExecutorRanking(query))));
                    return true;
                }
                if ("nodes".equals(parts[5])) {
                    runBlockingQuery(ctx, () -> toJson(Result.success(eventQueryService.queryNodeRanking(query))));
                    return true;
                }
            }
            if (parts.length >= 6 && "failures".equals(parts[4]) && "clusters".equals(parts[5])) {
                runBlockingQuery(ctx, () -> toJson(Result.success(eventQueryService.queryFailureClusters(query))));
                return true;
            }
        }

        // POST /collector/events/executions
        if (parts.length == 4 && "events".equals(parts[2]) && "executions".equals(parts[3])
                && method == HttpMethod.POST) {
            EventQuery query = MAPPER.readValue(body, EventQuery.class);
            runBlockingQuery(ctx, () -> {
                List<ExecutionTrace> list = eventQueryService.queryExecutionTraces(query);
                long total = eventQueryService.countExecutionTraces(query);
                return toJson(Result.success(new PageResult<>(list, total, query.getPage(), query.getPageSize())));
            });
            return true;
        }

        // GET /collector/events/{eventId}
        if (parts.length == 4 && "events".equals(parts[2]) && method == HttpMethod.GET) {
            String eventId = parts[3];
            runBlockingQuery(ctx, () -> {
                com.zestflow.common.model.dto.ChainEvent event = eventQueryService.getById(eventId);
                if (event == null) {
                    return toJson(Result.fail(404, "NOT_FOUND", "Event not found"));
                }
                return toJson(Result.success(event));
            });
            return true;
        }

        // GET /collector/events/executions/{executionId}/nodes/{nodeId}
        if (parts.length == 7 && "events".equals(parts[2]) && "executions".equals(parts[3])
                && "nodes".equals(parts[5]) && method == HttpMethod.GET) {
            String executionId = parts[4];
            String nodeId = parts[6];
            Map<String, String> params = parseQueryParams(uri);
            String nodeShape = params.get("nodeShape");
            runBlockingQuery(ctx, () -> {
                NodeExecutionDetail detail = eventQueryService.getNodeExecutionDetail(
                        executionId, nodeId, nodeShape);
                if (detail == null) {
                    return toJson(Result.fail(404, "NOT_FOUND", "Node execution detail not found"));
                }
                return toJson(Result.success(detail));
            });
            return true;
        }

        // POST /collector/events/ingest — 远程 Executor 批量写入链事件
        if (parts.length == 4 && "events".equals(parts[2]) && "ingest".equals(parts[3]) && method == HttpMethod.POST) {
            List<ChainEvent> events = MAPPER.readValue(body, new TypeReference<List<ChainEvent>>() { });
            if (events == null || events.isEmpty()) {
                writeResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                        toJson(Result.fail(400, "BAD_REQUEST", "events must not be empty")));
                return true;
            }
            runBlockingQuery(ctx, () -> {
                eventCollector.collectBatch(events);
                return toJson(Result.success(Map.of("accepted", events.size())));
            });
            return true;
        }

        // POST /collector/invocations
        if (parts.length == 3 && "invocations".equals(parts[2]) && method == HttpMethod.POST) {
            InvocationPayloadDTO dto = MAPPER.readValue(body, InvocationPayloadDTO.class);
            if (dto.getInvocationId() == null || dto.getInvocationId().isEmpty()) {
                writeResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                        toJson(Result.fail(400, "BAD_REQUEST", "invocationId is required")));
                return true;
            }
            runBlockingQuery(ctx, () -> {
                invocationPayloadService.save(dto);
                return toJson(Result.success(null));
            });
            return true;
        }

        // GET /collector/invocations/{invocationId}
        if (parts.length == 4 && "invocations".equals(parts[2]) && method == HttpMethod.GET) {
            String invocationId = parts[3];
            runBlockingQuery(ctx, () -> {
                InvocationPayloadDTO dto = invocationPayloadService.getByInvocationId(invocationId);
                if (dto == null) {
                    return toJson(Result.fail(404, "NOT_FOUND", "Invocation payload not found"));
                }
                return toJson(Result.success(dto));
            });
            return true;
        }

        // GET /collector/events/executions/{executionId}
        if (parts.length == 5 && "events".equals(parts[2]) && "executions".equals(parts[3])
                && method == HttpMethod.GET) {
            String executionId = parts[4];
            runBlockingQuery(ctx, () -> {
                ExecutionTrace trace = eventQueryService.getExecutionTrace(executionId);
                if (trace == null) {
                    return toJson(Result.fail(404, "NOT_FOUND", "Execution trace not found"));
                }
                return toJson(Result.success(trace));
            });
            return true;
        }

        // POST /collector/snapshots
        if (parts.length == 3 && "snapshots".equals(parts[2]) && method == HttpMethod.POST) {
            ChainSnapshotSyncDTO dto = MAPPER.readValue(body, ChainSnapshotSyncDTO.class);
            if (dto.getChainCode() == null || dto.getChainCode().isEmpty()) {
                writeResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                        toJson(Result.fail(400, "BAD_REQUEST", "chainCode is required")));
                return true;
            }
            int version = snapshotService.syncSnapshot(
                    dto.getChainCode(), dto.getGraphData(),
                    dto.getAppCode(), dto.getTenantId(), dto.getCreatedBy());
            writeResponse(ctx, HttpResponseStatus.OK, toJson(Result.success(Map.of("version", version))));
            return true;
        }

        // GET /collector/snapshots?chainCode=xxx&timestamp=xxx
        if (parts.length == 3 && "snapshots".equals(parts[2]) && method == HttpMethod.GET) {
            Map<String, String> params = parseQueryParams(uri);
            String chainCode = params.get("chainCode");
            String timestampStr = params.get("timestamp");
            String tenantIdStr = params.get("tenantId");
            if (chainCode == null || chainCode.isEmpty() || timestampStr == null) {
                writeResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                        toJson(Result.fail(400, "BAD_REQUEST", "chainCode and timestamp are required")));
                return true;
            }
            long timestamp = Long.parseLong(timestampStr);
            Long tenantId = tenantIdStr != null ? Long.parseLong(tenantIdStr) : null;
            runBlockingQuery(ctx, () -> {
                ChainSnapshotDTO dto = snapshotService.findSnapshotAt(chainCode, timestamp, tenantId);
                if (dto == null) {
                    return toJson(Result.fail(404, "NOT_FOUND", "Snapshot not found"));
                }
                return toJson(Result.success(dto));
            });
            return true;
        }

        return false;
    }

    /**
     * 在独立线程池执行阻塞 JDBC 查询，避免占用 Netty EventLoop
     */
    private void runBlockingQuery(ChannelHandlerContext ctx, Callable<String> query) {
        Runnable work = () -> {
            try {
                String body = query.call();
                HttpResponseStatus status = body.contains("\"code\":404") ? HttpResponseStatus.NOT_FOUND
                        : HttpResponseStatus.OK;
                writeResponse(ctx, status, body);
            } catch (Exception e) {
                log.error("Collector 查询失败", e);
                writeResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        toJson(Result.fail(500, e.getMessage())));
            }
        };
        if (queryExecutor == null) {
            work.run();
            return;
        }
        queryExecutor.submit(() -> {
            try {
                String body = query.call();
                HttpResponseStatus status = body.contains("\"code\":404") ? HttpResponseStatus.NOT_FOUND
                        : HttpResponseStatus.OK;
                ctx.channel().eventLoop().execute(() -> writeResponse(ctx, status, body));
            } catch (Exception e) {
                log.error("Collector 查询失败", e);
                ctx.channel().eventLoop().execute(() -> writeResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        toJson(Result.fail(500, e.getMessage()))));
            }
        });
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            log.debug("空闲超时，关闭连接");
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("CollectorServerHandler 异常", cause);
        ctx.close();
    }

    // ==================== Token 校验 ====================

    private boolean checkToken(FullHttpRequest request) {
        if (accessToken == null || accessToken.isEmpty()) {
            return true;
        }
        String header = request.headers().get("X-Collector-Token");
        return accessToken.equals(header);
    }

    // ==================== 响应写入 ====================

    private void writeResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
        ByteBuf buf = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, buf);
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
                .set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        ctx.writeAndFlush(response);
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("序列化失败", e);
            return "{\"code\":500,\"message\":\"serialization error\"}";
        }
    }

    // ==================== URI 工具 ====================

    private static String stripQuery(String uri) {
        if (uri == null) return null;
        int idx = uri.indexOf('?');
        return idx >= 0 ? uri.substring(0, idx) : uri;
    }

    private static Map<String, String> parseQueryParams(String uri) {
        Map<String, String> params = new LinkedHashMap<>();
        int idx = uri.indexOf('?');
        if (idx < 0) return params;
        String query = uri.substring(idx + 1);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && !kv[1].isEmpty()) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }
}
