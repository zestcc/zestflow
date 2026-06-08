package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.ai.model.entity.AiCopilotSessionPO;
import com.zestflow.admin.ai.model.entity.AiCopilotTraceStepPO;
import com.zestflow.admin.ai.model.vo.AiCopilotTraceOverviewVO;
import com.zestflow.admin.ai.model.vo.AiCopilotTraceSessionRowVO;
import com.zestflow.admin.ai.model.vo.AiCopilotTraceStepVO;
import com.zestflow.admin.ai.repository.AiCopilotSessionMapper;
import com.zestflow.admin.ai.repository.AiCopilotTraceStepMapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Copilot Trace 步骤落库与看板聚合（对标 LangSmith step trace）
 */
@Service
@RequiredArgsConstructor
public class AiCopilotTraceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ThreadLocal<Long> CURRENT_JOB = new ThreadLocal<>();
    private static final ThreadLocal<AtomicInteger> SORT_COUNTER = new ThreadLocal<>();

    private final AiCopilotTraceStepMapper traceStepMapper;
    private final AiCopilotSessionMapper sessionMapper;
    private final TenantAiConfigService tenantAiConfigService;

    public void bindJob(Long jobId) {
        CURRENT_JOB.set(jobId);
    }

    public void clearJob() {
        CURRENT_JOB.remove();
        SORT_COUNTER.remove();
    }

    public long startStep(Long sessionId, String stepType, String stepName) {
        if (sessionId == null) {
            return -1L;
        }
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        AiCopilotTraceStepPO po = new AiCopilotTraceStepPO();
        po.setTenantId(tenantId);
        po.setSessionId(sessionId);
        po.setJobId(CURRENT_JOB.get());
        po.setStepType(stepType);
        po.setStepName(stepName);
        po.setStatus("RUNNING");
        po.setSortOrder(nextSortOrder());
        traceStepMapper.insert(po);
        return po.getId();
    }

    public void finishStep(long stepId, boolean success, String detailJson, int latencyMs, Integer tokenEstimate) {
        if (stepId <= 0) {
            return;
        }
        AiCopilotTraceStepPO update = new AiCopilotTraceStepPO();
        update.setId(stepId);
        update.setStatus(success ? "OK" : "FAIL");
        update.setLatencyMs(latencyMs);
        update.setTokenEstimate(tokenEstimate);
        update.setDetailJson(truncateDetail(detailJson));
        traceStepMapper.updateById(update);
    }

    public void runStep(Long sessionId, String stepType, String stepName, Runnable action) {
        long stepId = startStep(sessionId, stepType, stepName);
        long start = System.currentTimeMillis();
        try {
            action.run();
            finishStep(stepId, true, null, (int) (System.currentTimeMillis() - start), null);
        } catch (RuntimeException e) {
            finishStep(stepId, false, e.getMessage(), (int) (System.currentTimeMillis() - start), null);
            throw e;
        }
    }

    public <T> T runStepWithResult(Long sessionId, String stepType, String stepName, java.util.function.Supplier<T> action) {
        long stepId = startStep(sessionId, stepType, stepName);
        long start = System.currentTimeMillis();
        try {
            T result = action.get();
            finishStep(stepId, true, null, (int) (System.currentTimeMillis() - start), null);
            return result;
        } catch (RuntimeException e) {
            finishStep(stepId, false, e.getMessage(), (int) (System.currentTimeMillis() - start), null);
            throw e;
        }
    }

    public List<AiCopilotTraceStepVO> listBySession(Long sessionId, Long tenantId) {
        requireSessionOwned(sessionId, tenantId);
        List<AiCopilotTraceStepPO> rows = traceStepMapper.selectList(
                new LambdaQueryWrapper<AiCopilotTraceStepPO>()
                        .eq(AiCopilotTraceStepPO::getSessionId, sessionId)
                        .eq(AiCopilotTraceStepPO::getTenantId, tenantId)
                        .orderByAsc(AiCopilotTraceStepPO::getSortOrder)
                        .orderByAsc(AiCopilotTraceStepPO::getId));
        return rows.stream().map(this::toVo).toList();
    }

    public AiCopilotTraceOverviewVO overview(int days) {
        int windowDays = Math.max(1, Math.min(days, 90));
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);

        List<AiCopilotTraceStepPO> steps = traceStepMapper.selectList(
                new LambdaQueryWrapper<AiCopilotTraceStepPO>()
                        .eq(AiCopilotTraceStepPO::getTenantId, tenantId)
                        .ge(AiCopilotTraceStepPO::getCreatedAt, since));

        long total = steps.size();
        long failed = steps.stream().filter(s -> "FAIL".equals(s.getStatus())).count();
        long latencySum = steps.stream()
                .map(AiCopilotTraceStepPO::getLatencyMs)
                .filter(v -> v != null && v > 0)
                .mapToLong(Integer::longValue)
                .sum();
        long latencyCount = steps.stream()
                .filter(s -> s.getLatencyMs() != null && s.getLatencyMs() > 0)
                .count();

        Map<String, Long> byType = steps.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getStepType() == null ? "UNKNOWN" : s.getStepType(),
                        LinkedHashMap::new,
                        Collectors.counting()));

        List<AiCopilotSessionPO> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<AiCopilotSessionPO>()
                        .eq(AiCopilotSessionPO::getTenantId, tenantId)
                        .ge(AiCopilotSessionPO::getCreatedAt, since)
                        .orderByDesc(AiCopilotSessionPO::getCreatedAt)
                        .last("LIMIT 30"));

        List<AiCopilotTraceSessionRowVO> recent = new ArrayList<>();
        for (AiCopilotSessionPO session : sessions) {
            List<AiCopilotTraceStepPO> sessionSteps = steps.stream()
                    .filter(s -> session.getId().equals(s.getSessionId()))
                    .toList();
            int totalLatency = sessionSteps.stream()
                    .map(AiCopilotTraceStepPO::getLatencyMs)
                    .filter(v -> v != null && v > 0)
                    .mapToInt(Integer::intValue)
                    .sum();
            recent.add(AiCopilotTraceSessionRowVO.builder()
                    .sessionId(session.getId())
                    .title(session.getTitle())
                    .mode(session.getMode())
                    .appCode(session.getAppCode())
                    .designId(session.getDesignId())
                    .stepCount(sessionSteps.size())
                    .totalLatencyMs(totalLatency > 0 ? totalLatency : session.getLatencyMs())
                    .success(session.getSuccess() == null || session.getSuccess() == 1)
                    .createdAt(session.getCreatedAt())
                    .build());
        }

        return AiCopilotTraceOverviewVO.builder()
                .days(windowDays)
                .totalSteps(total)
                .failedSteps(failed)
                .avgStepLatencyMs(latencyCount == 0 ? 0 : latencySum / latencyCount)
                .stepsByType(byType)
                .recentSessions(recent)
                .build();
    }

    private AiCopilotTraceStepVO toVo(AiCopilotTraceStepPO po) {
        return AiCopilotTraceStepVO.builder()
                .id(po.getId())
                .sessionId(po.getSessionId())
                .jobId(po.getJobId())
                .stepType(po.getStepType())
                .stepName(po.getStepName())
                .status(po.getStatus())
                .latencyMs(po.getLatencyMs())
                .tokenEstimate(po.getTokenEstimate())
                .detailJson(po.getDetailJson())
                .sortOrder(po.getSortOrder())
                .createdAt(po.getCreatedAt())
                .build();
    }

    private void requireSessionOwned(Long sessionId, Long tenantId) {
        AiCopilotSessionPO session = sessionMapper.selectById(sessionId);
        if (session == null || !tenantId.equals(session.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    private int nextSortOrder() {
        AtomicInteger counter = SORT_COUNTER.get();
        if (counter == null) {
            counter = new AtomicInteger(0);
            SORT_COUNTER.set(counter);
        }
        return counter.incrementAndGet();
    }

    private static String truncateDetail(String detail) {
        if (!StringUtils.hasText(detail)) {
            return null;
        }
        return detail.length() <= 2000 ? detail : detail.substring(0, 2000);
    }
}
