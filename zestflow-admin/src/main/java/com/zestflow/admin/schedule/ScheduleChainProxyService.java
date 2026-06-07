package com.zestflow.admin.schedule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.ScheduleCreateDTO;
import com.zestflow.admin.model.dto.ScheduleUpdateDTO;
import com.zestflow.admin.model.vo.ScheduleLogStatsVO;
import com.zestflow.admin.model.vo.ScheduleLogVO;
import com.zestflow.admin.model.vo.ScheduleVO;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 业务链调度 Hub 代理 — CRUD/日志/触发均转发至 Executor 业务库 API。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleChainProxyService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutorProxyService executorProxyService;

    public IPage<ScheduleVO> list(String appCode, String keyword, Integer status, Integer page, Integer size) {
        String query = "?keyword=" + esc(keyword)
                + "&status=" + (status != null ? status : "")
                + "&page=" + page + "&size=" + size;
        String json = executorProxyService.getFromExecutor(appCode, "/api/schedules", query);
        return parseSchedulePage(json, page, size);
    }

    public ScheduleVO getById(String appCode, Long id) {
        String json = executorProxyService.executeOnExecutor(appCode, "GET", "/api/schedules/" + id, null);
        return parseSchedule(json);
    }

    public ScheduleVO create(String appCode, ScheduleCreateDTO dto, String username) {
        try {
            var body = MAPPER.createObjectNode();
            body.put("chainCode", dto.getChainCode());
            body.put("chainName", dto.getChainName());
            body.put("cron", dto.getCron());
            body.put("routeStrategy", dto.getRouteStrategy() != null ? dto.getRouteStrategy() : "local");
            if (dto.getParams() != null) {
                body.put("params", dto.getParams());
            }
            if (dto.getRemark() != null) {
                body.put("remark", dto.getRemark());
            }
            body.put("createdBy", username != null ? username : SecurityUtils.getCurrentUsername());
            String json = executorProxyService.executeOnExecutor(appCode, "POST", "/api/schedules",
                    MAPPER.writeValueAsString(body));
            return parseSchedule(json);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND, "创建调度失败: " + e.getMessage());
        }
    }

    public ScheduleVO update(String appCode, Long id, ScheduleUpdateDTO dto) {
        try {
            var body = MAPPER.createObjectNode();
            if (dto.getCron() != null) {
                body.put("cron", dto.getCron());
            }
            if (dto.getRouteStrategy() != null) {
                body.put("routeStrategy", dto.getRouteStrategy());
            }
            if (dto.getParams() != null) {
                body.put("params", dto.getParams());
            }
            if (dto.getRemark() != null) {
                body.put("remark", dto.getRemark());
            }
            if (dto.getStatus() != null) {
                body.put("status", dto.getStatus());
            }
            String json = executorProxyService.executeOnExecutor(appCode, "PUT", "/api/schedules/" + id,
                    MAPPER.writeValueAsString(body));
            return parseSchedule(json);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND, "更新调度失败: " + e.getMessage());
        }
    }

    public void delete(String appCode, Long id) {
        executorProxyService.executeOnExecutor(appCode, "DELETE", "/api/schedules/" + id, null);
    }

    public void toggleStatus(String appCode, Long id) {
        executorProxyService.executeOnExecutor(appCode, "PUT", "/api/schedules/" + id + "/status", null);
    }

    public ScheduleLogVO trigger(String appCode, Long id) {
        String json = executorProxyService.executeOnExecutor(appCode, "POST", "/api/schedules/" + id + "/trigger", null);
        return parseLog(json);
    }

    public IPage<ScheduleLogVO> listLogs(String appCode, Long scheduleId, String keyword, Integer status,
                                         Integer page, Integer size) {
        String query = "?scheduleId=" + (scheduleId != null ? scheduleId : "")
                + "&keyword=" + esc(keyword)
                + "&status=" + (status != null ? status : "")
                + "&page=" + page + "&size=" + size;
        String json = executorProxyService.getFromExecutor(appCode, "/api/schedules/logs", query);
        return parseLogPage(json, page, size);
    }

    public ScheduleLogStatsVO logStats(String appCode, Integer hours) {
        int h = hours != null && hours > 0 ? hours : 24;
        String json = executorProxyService.getFromExecutor(appCode, "/api/schedules/stats", "?hours=" + h);
        try {
            JsonNode node = MAPPER.readTree(json);
            return ScheduleLogStatsVO.builder()
                    .totalCount(node.path("totalCount").asLong(0))
                    .successCount(node.path("successCount").asLong(0))
                    .failedCount(node.path("failedCount").asLong(0))
                    .runningCount(node.path("runningCount").asLong(0))
                    .successRate(node.path("successRate").asDouble(0))
                    .avgCostMs(node.path("avgCostMs").asDouble(0))
                    .build();
        } catch (Exception e) {
            log.warn("解析调度统计失败 appCode={}", appCode, e);
            return ScheduleLogStatsVO.builder().build();
        }
    }

    public long countFailures(String appCode, java.time.LocalDateTime since) {
        int minutes = (int) Math.max(1, java.time.Duration.between(since, java.time.LocalDateTime.now()).toMinutes());
        String json = executorProxyService.getFromExecutor(appCode, "/api/schedules/fail-count", "?minutes=" + minutes);
        try {
            JsonNode node = MAPPER.readTree(json);
            return node.path("failedCount").asLong(0);
        } catch (Exception e) {
            log.warn("解析链调度失败数失败 appCode={}", appCode, e);
            return 0L;
        }
    }

    private IPage<ScheduleVO> parseSchedulePage(String json, int page, int size) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.has("code") && root.get("code").asInt() >= 400) {
                return emptyPage(page, size);
            }
            long total = root.path("total").asLong(0);
            List<ScheduleVO> records = MAPPER.convertValue(root.path("records"),
                    new TypeReference<>() {});
            Page<ScheduleVO> voPage = new Page<>(page, size, total);
            voPage.setRecords(records);
            return voPage;
        } catch (Exception e) {
            log.warn("解析调度列表失败", e);
            return emptyPage(page, size);
        }
    }

    private IPage<ScheduleLogVO> parseLogPage(String json, int page, int size) {
        try {
            JsonNode root = MAPPER.readTree(json);
            long total = root.path("total").asLong(0);
            List<ScheduleLogVO> records = MAPPER.convertValue(root.path("records"),
                    new TypeReference<>() {});
            Page<ScheduleLogVO> voPage = new Page<>(page, size, total);
            voPage.setRecords(records);
            return voPage;
        } catch (Exception e) {
            log.warn("解析调度日志失败", e);
            return new Page<>(page, size, 0);
        }
    }

    private ScheduleVO parseSchedule(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node.has("code") && node.get("code").asInt() >= 400) {
                throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND, node.path("message").asText("调度不存在"));
            }
            return MAPPER.convertValue(node, ScheduleVO.class);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND, e.getMessage());
        }
    }

    private ScheduleLogVO parseLog(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node.has("code") && node.get("code").asInt() >= 400) {
                throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND, node.path("message").asText("触发失败"));
            }
            return MAPPER.convertValue(node, ScheduleLogVO.class);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND, e.getMessage());
        }
    }

    private static Page<ScheduleVO> emptyPage(int page, int size) {
        Page<ScheduleVO> p = new Page<>(page, size, 0);
        p.setRecords(List.of());
        return p;
    }

    private static String esc(String s) {
        return s != null ? s : "";
    }
}
