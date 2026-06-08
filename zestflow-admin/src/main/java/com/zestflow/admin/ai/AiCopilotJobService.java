package com.zestflow.admin.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.ai.model.dto.AiExplainRequest;
import com.zestflow.admin.ai.model.dto.AiSuggestRequest;
import com.zestflow.admin.ai.model.entity.AiCopilotJobPO;
import com.zestflow.admin.ai.model.vo.AiCopilotJobVO;
import com.zestflow.admin.ai.model.vo.AiExplainResponse;
import com.zestflow.admin.ai.model.vo.AiSuggestResponse;
import com.zestflow.admin.ai.repository.AiCopilotJobMapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Copilot 异步 Job：提交后轮询/断线续跑（对标 Cursor background agent）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCopilotJobService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiCopilotJobMapper jobMapper;
    private final AiCopilotPipeline pipeline;
    private final AiCopilotTraceService traceService;
    private final TenantAiConfigService tenantAiConfigService;

    private final ExecutorService jobExecutor = Executors.newFixedThreadPool(
            Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors())),
            r -> {
                Thread t = new Thread(r, "zestflow-ai-copilot-job");
                t.setDaemon(true);
                return t;
            });

    public AiCopilotJobVO submitSuggest(AiSuggestRequest request) {
        return submitJob("suggest", null, request);
    }

    public AiCopilotJobVO submitExplain(AiExplainRequest request) {
        return submitJob("explain", request, null);
    }

    public AiCopilotJobVO getJob(Long jobId) {
        AiCopilotJobPO job = requireOwned(jobId);
        return toVo(job);
    }

    public void cancelJob(Long jobId) {
        AiCopilotJobPO job = requireOwned(jobId);
        if ("DONE".equals(job.getStatus()) || "FAILED".equals(job.getStatus())) {
            return;
        }
        AiCopilotJobPO update = new AiCopilotJobPO();
        update.setId(jobId);
        update.setStatus("CANCELLED");
        update.setFinishedAt(LocalDateTime.now());
        jobMapper.updateById(update);
    }

    public boolean isCancelled(Long jobId) {
        AiCopilotJobPO job = jobMapper.selectById(jobId);
        return job != null && "CANCELLED".equals(job.getStatus());
    }

    private AiCopilotJobVO submitJob(String jobType, AiExplainRequest explainRequest, AiSuggestRequest suggestRequest) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        Long userId = currentUserId();
        AiCopilotJobPO job = new AiCopilotJobPO();
        job.setTenantId(tenantId);
        job.setUserId(userId);
        job.setJobType(jobType);
        job.setStatus("PENDING");
        try {
            Object req = "suggest".equals(jobType) ? suggestRequest : explainRequest;
            job.setRequestJson(MAPPER.writeValueAsString(req));
        } catch (Exception e) {
            job.setRequestJson("{}");
        }
        jobMapper.insert(job);

        AiCopilotRequestContext ctx = new AiCopilotRequestContext();
        jobExecutor.execute(() -> runJob(ctx, job.getId(), jobType, suggestRequest, explainRequest));
        return toVo(job);
    }

    private void runJob(AiCopilotRequestContext ctx, Long jobId, String jobType,
                          AiSuggestRequest suggestRequest, AiExplainRequest explainRequest) {
        ctx.apply();
        markRunning(jobId);
        traceService.bindJob(jobId);
        long start = System.currentTimeMillis();
        JobUpdatingStreamSink sink = new JobUpdatingStreamSink(this, jobId);
        try {
            if ("suggest".equals(jobType)) {
                AiSuggestResponse response = pipeline.suggest(suggestRequest, sink);
                markDoneSuggest(jobId, response, start, response.getSessionId());
            } else {
                AiExplainResponse response = pipeline.explain(explainRequest, sink);
                markDoneExplain(jobId, response, start);
            }
        } catch (Exception e) {
            if (!isCancelled(jobId)) {
                markFailed(jobId, e.getMessage(), start);
            }
            log.warn("Copilot Job 失败 jobId={}", jobId, e);
        } finally {
            traceService.clearJob();
            ctx.clear();
        }
    }

    void updateProgress(Long jobId, String step) {
        if (isCancelled(jobId)) {
            throw new BizException(ErrorCode.SERVER_ERROR, "任务已取消");
        }
        AiCopilotJobPO update = new AiCopilotJobPO();
        update.setId(jobId);
        update.setProgressStep(step);
        jobMapper.updateById(update);
    }

    void appendReasoning(Long jobId, String delta) {
        if (!StringUtils.hasText(delta)) {
            return;
        }
        AiCopilotJobPO job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        String merged = (job.getReasoningBuffer() != null ? job.getReasoningBuffer() : "") + delta;
        if (merged.length() > 500_000) {
            merged = merged.substring(merged.length() - 500_000);
        }
        AiCopilotJobPO update = new AiCopilotJobPO();
        update.setId(jobId);
        update.setReasoningBuffer(merged);
        jobMapper.updateById(update);
    }

    private void markRunning(Long jobId) {
        AiCopilotJobPO update = new AiCopilotJobPO();
        update.setId(jobId);
        update.setStatus("RUNNING");
        jobMapper.updateById(update);
    }

    private void markDoneSuggest(Long jobId, AiSuggestResponse response, long startMs, Long sessionId) {
        if (isCancelled(jobId)) {
            return;
        }
        AiCopilotJobPO update = new AiCopilotJobPO();
        update.setId(jobId);
        update.setStatus("DONE");
        update.setSessionId(sessionId);
        update.setLatencyMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startMs));
        update.setFinishedAt(LocalDateTime.now());
        try {
            update.setResultJson(MAPPER.writeValueAsString(response));
        } catch (Exception ignored) {
            update.setResultJson(null);
        }
        jobMapper.updateById(update);
    }

    private void markDoneExplain(Long jobId, AiExplainResponse response, long startMs) {
        if (isCancelled(jobId)) {
            return;
        }
        AiCopilotJobPO update = new AiCopilotJobPO();
        update.setId(jobId);
        update.setStatus("DONE");
        update.setSessionId(response.getSessionId());
        update.setLatencyMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startMs));
        update.setFinishedAt(LocalDateTime.now());
        try {
            update.setResultJson(MAPPER.writeValueAsString(response));
        } catch (Exception ignored) {
            update.setResultJson(null);
        }
        jobMapper.updateById(update);
    }

    private void markFailed(Long jobId, String message, long startMs) {
        AiCopilotJobPO update = new AiCopilotJobPO();
        update.setId(jobId);
        update.setStatus("FAILED");
        update.setErrorMessage(truncate(message));
        update.setLatencyMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startMs));
        update.setFinishedAt(LocalDateTime.now());
        jobMapper.updateById(update);
    }

    private AiCopilotJobPO requireOwned(Long jobId) {
        AiCopilotJobPO job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        if (!tenantId.equals(job.getTenantId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
        Long userId = currentUserId();
        if (userId != null && userId > 0 && !userId.equals(job.getUserId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
        return job;
    }

    private AiCopilotJobVO toVo(AiCopilotJobPO job) {
        AiCopilotJobVO.AiCopilotJobVOBuilder builder = AiCopilotJobVO.builder()
                .jobId(job.getId())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .sessionId(job.getSessionId())
                .progressStep(job.getProgressStep())
                .reasoning(job.getReasoningBuffer())
                .errorMessage(job.getErrorMessage())
                .latencyMs(job.getLatencyMs())
                .createdAt(job.getCreatedAt())
                .finishedAt(job.getFinishedAt());
        if (StringUtils.hasText(job.getResultJson())) {
            try {
                if ("suggest".equals(job.getJobType())) {
                    builder.suggestResult(MAPPER.readValue(job.getResultJson(), AiSuggestResponse.class));
                } else if ("explain".equals(job.getJobType())) {
                    builder.explainResult(MAPPER.readValue(job.getResultJson(), AiExplainResponse.class));
                }
            } catch (Exception e) {
                log.debug("解析 Job 结果失败 jobId={}", job.getId());
            }
        }
        return builder.build();
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return 0L;
        }
        try {
            return SecurityUtils.getUserId(auth);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
