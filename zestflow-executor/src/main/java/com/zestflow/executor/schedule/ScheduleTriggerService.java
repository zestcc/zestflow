package com.zestflow.executor.schedule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.PeerExecutorDTO;
import com.zestflow.common.model.Result;
import com.zestflow.executor.registry.ExecutorProperties;
import com.zestflow.executor.schedule.routing.ScheduleExecutionRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ScheduleTriggerService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ScheduleRepository scheduleRepository;
    private final ScheduleExecutionRouter executionRouter;
    private final ExecutorProperties executorProperties;

    public ScheduleLogPO trigger(SchedulePO schedule, String triggerType, String idempotencyKey) {
        long start = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        ScheduleLogPO logPo = ScheduleLogPO.builder()
                .scheduleId(schedule.getId())
                .chainCode(schedule.getChainCode())
                .routeStrategy(schedule.getRouteStrategy())
                .triggerType(triggerType)
                .params(schedule.getParams())
                .triggeredAt(now.format(DTF))
                .status(0)
                .build();

        Map<String, Object> params = parseParams(schedule.getParams());
        ChainExecuteRequestDTO request = ChainExecuteRequestDTO.builder()
                .chainCode(schedule.getChainCode())
                .params(params)
                .source("executor-schedule")
                .idempotencyKey(idempotencyKey)
                .build();

        ScheduleExecutionRouter.RoutedExecution routed;
        try {
            routed = executionRouter.execute(schedule.getRouteStrategy(), schedule.getChainCode(), request);
        } catch (Exception e) {
            log.error("调度执行异常 scheduleId={} chainCode={}", schedule.getId(), schedule.getChainCode(), e);
            logPo.setExecutorId(resolveLocalExecutorId());
            logPo.setStatus(2);
            logPo.setErrorMessage(e.getMessage());
            logPo.setCostMs(System.currentTimeMillis() - start);
            logPo.setId(scheduleRepository.insertLog(logPo));
            return logPo;
        }

        ChainExecuteResultDTO result = routed.getResult();
        logPo.setExecutorId(routed.getExecutorId() != null ? routed.getExecutorId() : resolveLocalExecutorId());
        logPo.setCostMs(System.currentTimeMillis() - start);
        if (result != null && result.isSuccess()) {
            logPo.setStatus(1);
        } else {
            logPo.setStatus(2);
            logPo.setErrorMessage(result != null ? result.getErrorMessage() : "执行失败");
        }
        if (result != null && result.getInstanceId() != null && !result.getInstanceId().isBlank()) {
            logPo.setExecutionId(result.getInstanceId());
        }
        logPo.setId(scheduleRepository.insertLog(logPo));
        log.info("调度触发完成 scheduleId={} chainCode={} executorId={} attempted={} status={} cost={}ms",
                schedule.getId(), schedule.getChainCode(), logPo.getExecutorId(), routed.getAttempted(),
                logPo.getStatus(), logPo.getCostMs());
        return logPo;
    }

    private Map<String, Object> parseParams(String params) {
        if (params == null || params.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(params, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析调度参数失败，使用空参数", e);
            return Collections.emptyMap();
        }
    }

    private String resolveLocalExecutorId() {
        return String.format("%s@%s:%d",
                executorProperties.getAppCode(),
                executorProperties.getHost(),
                executorProperties.getPort());
    }
}
