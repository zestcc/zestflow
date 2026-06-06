package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.ai.model.entity.AiCopilotMessagePO;
import com.zestflow.admin.ai.model.entity.AiCopilotSessionPO;
import com.zestflow.admin.ai.model.vo.AiUsageDailyVO;
import com.zestflow.admin.ai.model.vo.AiUsageOverviewVO;
import com.zestflow.admin.ai.repository.AiCopilotMessageMapper;
import com.zestflow.admin.ai.repository.AiCopilotSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiUsageStatsService {

    private final AiCopilotSessionMapper sessionMapper;
    private final AiCopilotMessageMapper messageMapper;
    private final TenantAiConfigService tenantAiConfigService;

    public AiUsageOverviewVO overview(int days) {
        int windowDays = Math.max(1, Math.min(days, 90));
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);

        List<AiCopilotSessionPO> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<AiCopilotSessionPO>()
                        .eq(AiCopilotSessionPO::getTenantId, tenantId)
                        .ge(AiCopilotSessionPO::getCreatedAt, since)
                        .orderByAsc(AiCopilotSessionPO::getCreatedAt));

        long total = sessions.size();
        long success = sessions.stream().filter(s -> s.getSuccess() == null || s.getSuccess() == 1).count();
        long latencySum = sessions.stream()
                .map(AiCopilotSessionPO::getLatencyMs)
                .filter(v -> v != null && v > 0)
                .mapToLong(Integer::longValue)
                .sum();
        long latencyCount = sessions.stream()
                .filter(s -> s.getLatencyMs() != null && s.getLatencyMs() > 0)
                .count();

        Map<String, Long> byMode = sessions.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getMode() == null ? "unknown" : s.getMode(),
                        LinkedHashMap::new,
                        Collectors.counting()));

        long adopted = sessions.stream().filter(s -> s.getAdopted() != null && s.getAdopted() == 1).count();
        long feedback = sessions.stream().filter(s -> s.getAdopted() != null).count();

        long tokenEstimate = 0;
        if (!sessions.isEmpty()) {
            List<Long> sessionIds = sessions.stream().map(AiCopilotSessionPO::getId).toList();
            List<AiCopilotMessagePO> messages = messageMapper.selectList(
                    new LambdaQueryWrapper<AiCopilotMessagePO>()
                            .eq(AiCopilotMessagePO::getTenantId, tenantId)
                            .in(AiCopilotMessagePO::getSessionId, sessionIds));
            tokenEstimate = messages.stream()
                    .map(AiCopilotMessagePO::getTokenEstimate)
                    .filter(v -> v != null && v > 0)
                    .mapToLong(Integer::longValue)
                    .sum();
        }

        List<AiUsageDailyVO> daily = buildDailyTrend(sessions, windowDays);

        return AiUsageOverviewVO.builder()
                .days(windowDays)
                .totalSessions(total)
                .successSessions(success)
                .successRate(total == 0 ? 0 : roundRate(success, total))
                .avgLatencyMs(latencyCount == 0 ? 0 : latencySum / latencyCount)
                .totalTokenEstimate(tokenEstimate)
                .adoptedCount(adopted)
                .feedbackCount(feedback)
                .adoptedRate(feedback == 0 ? 0 : roundRate(adopted, feedback))
                .sessionsByMode(byMode)
                .dailyTrend(daily)
                .build();
    }

    private List<AiUsageDailyVO> buildDailyTrend(List<AiCopilotSessionPO> sessions, int windowDays) {
        Map<LocalDate, List<AiCopilotSessionPO>> grouped = sessions.stream()
                .filter(s -> s.getCreatedAt() != null)
                .collect(Collectors.groupingBy(s -> s.getCreatedAt().toLocalDate(), LinkedHashMap::new, Collectors.toList()));

        List<AiUsageDailyVO> daily = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(windowDays - 1L);
        for (int i = 0; i < windowDays; i++) {
            LocalDate day = start.plusDays(i);
            List<AiCopilotSessionPO> daySessions = grouped.getOrDefault(day, List.of());
            daily.add(AiUsageDailyVO.builder()
                    .date(day.toString())
                    .sessions(daySessions.size())
                    .successSessions(daySessions.stream()
                            .filter(s -> s.getSuccess() == null || s.getSuccess() == 1).count())
                    .tokenEstimate(0)
                    .build());
        }
        return daily;
    }

    private static double roundRate(long part, long total) {
        return Math.round(part * 1000.0 / total) / 10.0;
    }
}
