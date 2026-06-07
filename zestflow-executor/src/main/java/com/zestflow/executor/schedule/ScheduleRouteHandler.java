package com.zestflow.executor.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executor Netty — /api/schedules 业务库调度 CRUD 与触发。
 */
@Slf4j
@RequiredArgsConstructor
public class ScheduleRouteHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ScheduleRepository scheduleRepository;
    private final ScheduleTriggerService triggerService;

    public boolean dispatch(ChannelHandlerContext ctx, HttpMethod method, String uri, String body,
                            ResponseWriter writer) throws Exception {
        if (!uri.startsWith("/api/schedules")) {
            return false;
        }
        String[] parts = uri.split("/");
        if (parts.length == 4 && "logs".equals(stripQuery(parts[3])) && method == HttpMethod.GET) {
            return handleListLogs(ctx, uri, writer);
        }
        if (parts.length == 4 && "stats".equals(stripQuery(parts[3])) && method == HttpMethod.GET) {
            return handleLogStats(ctx, uri, writer);
        }
        if (parts.length == 4 && "fail-count".equals(stripQuery(parts[3])) && method == HttpMethod.GET) {
            return handleFailCount(ctx, uri, writer);
        }
        if (parts.length == 3) {
            if (method == HttpMethod.GET) {
                return handleList(ctx, uri, writer);
            }
            if (method == HttpMethod.POST) {
                return handleCreate(ctx, body, writer);
            }
        }
        if (parts.length == 5 && "trigger".equals(stripQuery(parts[4])) && method == HttpMethod.POST) {
            long id = parseLong(stripQuery(parts[3]));
            if (id > 0) {
                return handleTrigger(ctx, id, writer);
            }
        }
        if (parts.length == 5 && "status".equals(stripQuery(parts[4])) && method == HttpMethod.PUT) {
            long id = parseLong(stripQuery(parts[3]));
            if (id > 0) {
                return handleToggleStatus(ctx, id, writer);
            }
        }
        if (parts.length == 4) {
            long id = parseLong(stripQuery(parts[3]));
            if (id <= 0) {
                return false;
            }
            if (method == HttpMethod.GET) {
                return handleGet(ctx, id, writer);
            }
            if (method == HttpMethod.PUT) {
                return handleUpdate(ctx, id, body, writer);
            }
            if (method == HttpMethod.DELETE) {
                return handleDelete(ctx, id, writer);
            }
        }
        return false;
    }

    private boolean handleList(ChannelHandlerContext ctx, String uri, ResponseWriter writer) throws Exception {
        Map<String, String> params = parseQuery(uri);
        List<SchedulePO> all = scheduleRepository.list(params.get("keyword"), parseInteger(params.get("status")));
        int page = parseInteger(params.getOrDefault("page", "1"));
        int size = parseInteger(params.getOrDefault("size", "20"));
        int total = all.size();
        List<SchedulePO> paged = all.stream().skip((long) (page - 1) * size).limit(size).collect(Collectors.toList());
        ObjectNode root = MAPPER.createObjectNode();
        root.put("total", total);
        root.put("current", page);
        root.put("size", size);
        ArrayNode records = root.putArray("records");
        for (SchedulePO s : paged) {
            records.add(scheduleToJson(s));
        }
        writer.write(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(root));
        return true;
    }

    private boolean handleGet(ChannelHandlerContext ctx, long id, ResponseWriter writer) throws Exception {
        SchedulePO po = scheduleRepository.getById(id);
        if (po == null) {
            writer.write(ctx, HttpResponseStatus.NOT_FOUND, "{\"code\":404,\"message\":\"调度不存在\"}");
            return true;
        }
        writer.write(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(scheduleToJson(po)));
        return true;
    }

    private boolean handleCreate(ChannelHandlerContext ctx, String body, ResponseWriter writer) throws Exception {
        JsonNode node = MAPPER.readTree(body);
        SchedulePO po = SchedulePO.builder()
                .chainCode(text(node, "chainCode"))
                .chainName(text(node, "chainName"))
                .cron(text(node, "cron"))
                .routeStrategy(textOrDefault(node, "routeStrategy", "local"))
                .shardTotal(node.has("shardTotal") ? node.get("shardTotal").asInt(1) : 1)
                .params(text(node, "params"))
                .remark(text(node, "remark"))
                .status(1)
                .createdBy(text(node, "createdBy"))
                .build();
        long id = scheduleRepository.insert(po);
        ObjectNode resp = scheduleToJson(scheduleRepository.getById(id));
        writer.write(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(resp));
        return true;
    }

    private boolean handleUpdate(ChannelHandlerContext ctx, long id, String body, ResponseWriter writer) throws Exception {
        SchedulePO existing = scheduleRepository.getById(id);
        if (existing == null) {
            writer.write(ctx, HttpResponseStatus.NOT_FOUND, "{\"code\":404,\"message\":\"调度不存在\"}");
            return true;
        }
        JsonNode node = MAPPER.readTree(body);
        if (node.has("cron")) {
            existing.setCron(node.get("cron").asText());
        }
        if (node.has("routeStrategy")) {
            existing.setRouteStrategy(node.get("routeStrategy").asText());
        }
        if (node.has("shardTotal")) {
            existing.setShardTotal(node.get("shardTotal").asInt());
        }
        if (node.has("params")) {
            existing.setParams(node.get("params").isNull() ? null : node.get("params").asText());
        }
        if (node.has("remark")) {
            existing.setRemark(node.get("remark").asText());
        }
        if (node.has("status")) {
            existing.setStatus(node.get("status").asInt());
        }
        scheduleRepository.update(existing);
        writer.write(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(scheduleToJson(scheduleRepository.getById(id))));
        return true;
    }

    private boolean handleDelete(ChannelHandlerContext ctx, long id, ResponseWriter writer) throws Exception {
        scheduleRepository.delete(id);
        writer.write(ctx, HttpResponseStatus.OK, "{\"code\":200}");
        return true;
    }

    private boolean handleToggleStatus(ChannelHandlerContext ctx, long id, ResponseWriter writer) throws Exception {
        SchedulePO po = scheduleRepository.getById(id);
        if (po == null) {
            writer.write(ctx, HttpResponseStatus.NOT_FOUND, "{\"code\":404,\"message\":\"调度不存在\"}");
            return true;
        }
        int newStatus = po.getStatus() != null && po.getStatus() == 1 ? 0 : 1;
        scheduleRepository.toggleStatus(id, newStatus);
        writer.write(ctx, HttpResponseStatus.OK, "{\"code\":200}");
        return true;
    }

    private boolean handleTrigger(ChannelHandlerContext ctx, long id, ResponseWriter writer) throws Exception {
        SchedulePO po = scheduleRepository.getById(id);
        if (po == null) {
            writer.write(ctx, HttpResponseStatus.NOT_FOUND, "{\"code\":404,\"message\":\"调度不存在\"}");
            return true;
        }
        String key = ScheduleIdempotencyKeys.forManualTrigger(id);
        ScheduleLogPO logPo = triggerService.trigger(po, "manual", key);
        writer.write(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(logToJson(logPo)));
        return true;
    }

    private boolean handleListLogs(ChannelHandlerContext ctx, String uri, ResponseWriter writer) throws Exception {
        Map<String, String> params = parseQuery(uri);
        Long scheduleId = params.containsKey("scheduleId") ? parseLong(params.get("scheduleId")) : null;
        List<ScheduleLogPO> all = scheduleRepository.listLogs(scheduleId, params.get("keyword"),
                parseInteger(params.get("status")));
        int page = parseInteger(params.getOrDefault("page", "1"));
        int size = parseInteger(params.getOrDefault("size", "20"));
        int total = all.size();
        List<ScheduleLogPO> paged = all.stream().skip((long) (page - 1) * size).limit(size).collect(Collectors.toList());
        ObjectNode root = MAPPER.createObjectNode();
        root.put("total", total);
        root.put("current", page);
        root.put("size", size);
        ArrayNode records = root.putArray("records");
        for (ScheduleLogPO log : paged) {
            records.add(logToJson(log));
        }
        writer.write(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(root));
        return true;
    }

    private boolean handleLogStats(ChannelHandlerContext ctx, String uri, ResponseWriter writer) throws Exception {
        Map<String, String> params = parseQuery(uri);
        int hours = parseInteger(params.getOrDefault("hours", "24"));
        java.time.LocalDateTime since = java.time.LocalDateTime.now().minusHours(hours);
        List<ScheduleLogPO> logs = scheduleRepository.listLogsSince(since.format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        long success = logs.stream().filter(l -> l.getStatus() != null && l.getStatus() == 1).count();
        long failed = logs.stream().filter(l -> l.getStatus() != null && l.getStatus() == 2).count();
        long running = logs.stream().filter(l -> l.getStatus() != null && l.getStatus() == 0).count();
        double rate = (success + failed) > 0 ? (double) success / (success + failed) * 100.0 : 0.0;
        ObjectNode root = MAPPER.createObjectNode();
        root.put("totalCount", logs.size());
        root.put("successCount", success);
        root.put("failedCount", failed);
        root.put("runningCount", running);
        root.put("successRate", Math.round(rate * 10) / 10.0);
        root.put("avgCostMs", logs.stream().filter(l -> l.getCostMs() != null && l.getCostMs() > 0)
                .mapToLong(ScheduleLogPO::getCostMs).average().orElse(0D));
        writer.write(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(root));
        return true;
    }

    private boolean handleFailCount(ChannelHandlerContext ctx, String uri, ResponseWriter writer) throws Exception {
        Map<String, String> params = parseQuery(uri);
        int minutes = parseInteger(params.getOrDefault("minutes", "60"));
        long count = scheduleRepository.countFailedSince(minutes);
        ObjectNode root = MAPPER.createObjectNode();
        root.put("failedCount", count);
        root.put("windowMinutes", minutes);
        writer.write(ctx, HttpResponseStatus.OK, MAPPER.writeValueAsString(root));
        return true;
    }

    private ObjectNode scheduleToJson(SchedulePO s) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("id", s.getId());
        n.put("chainCode", s.getChainCode());
        n.put("chainName", s.getChainName());
        n.put("jobType", "CHAIN");
        n.put("cron", s.getCron());
        n.put("routeStrategy", s.getRouteStrategy() != null ? s.getRouteStrategy() : "local");
        n.put("shardTotal", s.getShardTotal() != null ? s.getShardTotal() : 1);
        if (s.getParams() != null) {
            n.put("params", s.getParams());
        }
        n.put("status", s.getStatus() != null ? s.getStatus() : 1);
        if (s.getRemark() != null) {
            n.put("remark", s.getRemark());
        }
        if (s.getCreatedBy() != null) {
            n.put("createdBy", s.getCreatedBy());
        }
        if (s.getCreatedAt() != null) {
            n.put("createdAt", s.getCreatedAt());
        }
        if (s.getUpdatedAt() != null) {
            n.put("updatedAt", s.getUpdatedAt());
        }
        return n;
    }

    private ObjectNode logToJson(ScheduleLogPO po) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("id", po.getId());
        n.put("scheduleId", po.getScheduleId());
        n.put("chainCode", po.getChainCode());
        if (po.getExecutorId() != null) {
            n.put("executorId", po.getExecutorId());
        }
        if (po.getExecutionId() != null) {
            n.put("executionId", po.getExecutionId());
        }
        n.put("triggerType", po.getTriggerType());
        n.put("status", po.getStatus());
        if (po.getErrorMessage() != null) {
            n.put("errorMessage", po.getErrorMessage());
        }
        if (po.getCostMs() != null) {
            n.put("costMs", po.getCostMs());
        }
        n.put("triggeredAt", po.getTriggeredAt());
        return n;
    }

    private static String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private static String textOrDefault(JsonNode node, String field, String def) {
        String v = text(node, field);
        return v != null && !v.isBlank() ? v : def;
    }

    private static Map<String, String> parseQuery(String uri) {
        int idx = uri.indexOf('?');
        if (idx < 0) {
            return Map.of();
        }
        Map<String, String> map = new java.util.HashMap<>();
        for (String pair : uri.substring(idx + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static String stripQuery(String s) {
        int idx = s.indexOf('?');
        return idx >= 0 ? s.substring(0, idx) : s;
    }

    private static int parseInteger(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return -1;
        }
    }

    @FunctionalInterface
    public interface ResponseWriter {
        void write(ChannelHandlerContext ctx, HttpResponseStatus status, String body) throws Exception;
    }
}
