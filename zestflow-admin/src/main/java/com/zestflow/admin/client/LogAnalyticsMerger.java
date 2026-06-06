package com.zestflow.admin.client;

import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStatsQuery;
import com.zestflow.common.protocol.ExecutionRankItem;
import com.zestflow.common.protocol.ExecutionTrendPoint;
import com.zestflow.common.protocol.FailureClusterItem;
import com.zestflow.common.protocol.LogAnalyticsQuery;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多采集器分析结果合并 — 计数求和、耗时加权平均
 */
final class LogAnalyticsMerger {

    private LogAnalyticsMerger() {
    }

    static EventStats mergeStats(List<EventStats> parts) {
        if (parts == null || parts.isEmpty()) {
            return EventStats.builder().build();
        }
        if (parts.size() == 1 && parts.get(0) != null) {
            return parts.get(0);
        }
        long totalCount = 0;
        long executionCount = 0;
        long successCount = 0;
        long failCount = 0;
        long inProgress = 0;
        double avgWeighted = 0;
        long maxCost = 0;
        long p95 = 0;
        Map<String, Long> typeDist = new LinkedHashMap<>();

        for (EventStats s : parts) {
            if (s == null) {
                continue;
            }
            totalCount += s.getTotalCount();
            executionCount += s.getExecutionCount();
            successCount += s.getSuccessCount();
            failCount += s.getFailCount();
            inProgress += s.getInProgressCount();
            avgWeighted += s.getAvgCostMs() * Math.max(1, s.getExecutionCount());
            maxCost = Math.max(maxCost, s.getMaxCostMs());
            p95 = Math.max(p95, s.getP95CostMs());
            if (s.getTypeDistribution() != null) {
                s.getTypeDistribution().forEach((k, v) -> typeDist.merge(k, v, Long::sum));
            }
        }
        double avg = executionCount > 0 ? avgWeighted / executionCount : 0;
        double rate = (successCount + failCount) > 0
                ? (double) successCount / (successCount + failCount) * 100.0 : 0;

        return EventStats.builder()
                .totalCount(totalCount)
                .executionCount(executionCount)
                .successCount(successCount)
                .failCount(failCount)
                .inProgressCount(inProgress)
                .typeDistribution(typeDist.isEmpty() ? null : typeDist)
                .successRate(Math.round(rate * 10) / 10.0)
                .avgCostMs(Math.round(avg * 10) / 10.0)
                .p95CostMs(p95)
                .maxCostMs(maxCost)
                .build();
    }

    static List<ExecutionTrendPoint> mergeTrend(List<List<ExecutionTrendPoint>> parts) {
        Map<Long, ExecutionTrendPoint> merged = new LinkedHashMap<>();
        for (List<ExecutionTrendPoint> list : parts) {
            if (list == null) {
                continue;
            }
            for (ExecutionTrendPoint p : list) {
                merged.merge(p.getBucketStart(), p, (a, b) -> ExecutionTrendPoint.builder()
                        .bucketStart(a.getBucketStart())
                        .totalCount(a.getTotalCount() + b.getTotalCount())
                        .successCount(a.getSuccessCount() + b.getSuccessCount())
                        .failCount(a.getFailCount() + b.getFailCount())
                        .avgCostMs(weightedAvg(a.getAvgCostMs(), a.getTotalCount(), b.getAvgCostMs(), b.getTotalCount()))
                        .build());
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingLong(ExecutionTrendPoint::getBucketStart))
                .toList();
    }

    static List<ExecutionRankItem> mergeRanking(List<List<ExecutionRankItem>> parts, int limit) {
        Map<String, ExecutionRankItem> merged = new LinkedHashMap<>();
        for (List<ExecutionRankItem> list : parts) {
            if (list == null) {
                continue;
            }
            for (ExecutionRankItem item : list) {
                merged.merge(item.getKey(), item, (a, b) -> {
                    long total = a.getTotalCount() + b.getTotalCount();
                    long fail = a.getFailCount() + b.getFailCount();
                    double rate = total > 0 ? (double) (total - fail) / total * 100.0 : 100.0;
                    return ExecutionRankItem.builder()
                            .key(a.getKey())
                            .name(a.getName() != null && !a.getName().isBlank() ? a.getName() : b.getName())
                            .totalCount(total)
                            .failCount(fail)
                            .successRate(Math.round(rate * 10) / 10.0)
                            .avgCostMs(weightedAvg(a.getAvgCostMs(), a.getTotalCount(), b.getAvgCostMs(), b.getTotalCount()))
                            .maxCostMs(Math.max(a.getMaxCostMs(), b.getMaxCostMs()))
                            .build();
                });
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingLong(ExecutionRankItem::getTotalCount).reversed())
                .limit(limit > 0 ? limit : 10)
                .toList();
    }

    static List<FailureClusterItem> mergeFailures(List<List<FailureClusterItem>> parts, int limit) {
        Map<String, FailureClusterItem> merged = new LinkedHashMap<>();
        for (List<FailureClusterItem> list : parts) {
            if (list == null) {
                continue;
            }
            for (FailureClusterItem item : list) {
                merged.merge(item.getErrorSummary(), item, (a, b) -> FailureClusterItem.builder()
                        .errorSummary(a.getErrorSummary())
                        .count(a.getCount() + b.getCount())
                        .lastSeen(Math.max(a.getLastSeen(), b.getLastSeen()))
                        .build());
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingLong(FailureClusterItem::getCount).reversed())
                .limit(limit > 0 ? limit : 10)
                .toList();
    }

    private static double weightedAvg(double a1, long w1, double a2, long w2) {
        long total = w1 + w2;
        if (total <= 0) {
            return 0;
        }
        return (a1 * w1 + a2 * w2) / total;
    }
}
