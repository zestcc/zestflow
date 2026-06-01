package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.client.CollectorClient;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.vo.DashboardStatsVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.service.DashboardService;
import com.zestflow.admin.service.TenantAppContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutorRegistryMapper executorRegistryMapper;
    private final ExecutorProxyService proxyService;
    private final CollectorClient collectorClient;
    private final TenantAppContext tenantAppContext;

    @Override
    public DashboardStatsVO getStats() {
        // 统计应用数：从 executor_registry 按 app_code 去重
        long totalApps = executorRegistryMapper.selectCount(
                new QueryWrapper<ExecutorRegistryPO>()
                        .isNotNull("app_code")
                        .ne("app_code", "")
                        .select("DISTINCT app_code")
        );

        long totalExecutors = executorRegistryMapper.selectCount(null);
        long healthyExecutors = executorRegistryMapper.selectCount(
                new LambdaQueryWrapper<ExecutorRegistryPO>().eq(ExecutorRegistryPO::getStatus, 1)
        );
        long errorExecutors = executorRegistryMapper.selectCount(
                new LambdaQueryWrapper<ExecutorRegistryPO>().eq(ExecutorRegistryPO::getStatus, 2)
        );
        long offlineExecutors = executorRegistryMapper.selectCount(
                new LambdaQueryWrapper<ExecutorRegistryPO>().eq(ExecutorRegistryPO::getStatus, 0)
        );

        // 链 & 设计统计：遍历有在线执行器的应用，汇总 counts
        long totalChains = 0;
        long enabledChains = 0;
        long totalDesigns = 0;

        // 从 executor_registry 获取所有不同的 appCode
        List<ExecutorRegistryPO> distinctApps = executorRegistryMapper.selectList(
                new QueryWrapper<ExecutorRegistryPO>()
                        .select("DISTINCT app_code, app_name")
                        .isNotNull("app_code")
                        .ne("app_code", "")
                        .orderByAsc("app_code")
        );
        List<String> appCodes = distinctApps.stream()
                .map(ExecutorRegistryPO::getAppCode)
                .collect(Collectors.toList());

        // 非超管按 appCode 过滤可见应用
        Set<String> accessibleCodes = tenantAppContext.getCurrentUserAppCodes();
        if (accessibleCodes != null && !accessibleCodes.isEmpty()) {
            appCodes = appCodes.stream().filter(accessibleCodes::contains).collect(Collectors.toList());
        }

        for (String appCode : appCodes) {
            if (appCode == null || appCode.isBlank()) continue;

            long online = executorRegistryMapper.selectCount(
                    new LambdaQueryWrapper<ExecutorRegistryPO>()
                            .eq(ExecutorRegistryPO::getAppCode, appCode)
                            .eq(ExecutorRegistryPO::getStatus, 1));
            if (online == 0) continue;

            // 获取链总数（page=1&size=1 只取 total）
            String chainJson = proxyService.getFromExecutor(appCode, "/api/chains", "?page=1&size=1");
            String designJson = proxyService.getFromExecutor(appCode, "/api/designs", "?page=1&size=1");

            try {
                if (chainJson != null) {
                    JsonNode chainRoot = MAPPER.readTree(chainJson);
                    totalChains += chainRoot.path("total").asLong(0);
                }
            } catch (Exception e) {
                log.warn("解析链统计失败 appCode={}", appCode, e);
            }

            // 获取启用链数（status=4 视为已启用）
            String enabledJson = proxyService.getFromExecutor(appCode, "/api/chains", "?page=1&size=1&status=4");
            try {
                if (enabledJson != null) {
                    JsonNode enabledRoot = MAPPER.readTree(enabledJson);
                    enabledChains += enabledRoot.path("total").asLong(0);
                }
            } catch (Exception e) {
                log.warn("解析启用链统计失败 appCode={}", appCode, e);
            }

            try {
                if (designJson != null) {
                    JsonNode designRoot = MAPPER.readTree(designJson);
                    totalDesigns += designRoot.path("total").asLong(0);
                }
            } catch (Exception e) {
                log.warn("解析设计统计失败 appCode={}", appCode, e);
            }
        }

        // 执行统计：从 Collector 查询今日数据
        long todayExecutions = 0;
        double avgExecutionMs = 0;
        double successRate = 0;

        try {
            LocalDate today = LocalDate.now();
            long todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            Map<String, Object> stats = collectorClient.queryStats(todayStart, null, tenantAppContext.getCurrentTenantId());
            if (!stats.isEmpty()) {
                todayExecutions = ((Number) stats.getOrDefault("totalCount", 0)).longValue();
                avgExecutionMs = ((Number) stats.getOrDefault("avgCostMs", 0)).doubleValue();
                successRate = ((Number) stats.getOrDefault("successRate", 0)).doubleValue();
            }
        } catch (Exception e) {
            log.warn("查询执行统计失败", e);
        }

        DashboardStatsVO vo = DashboardStatsVO.builder()
                .totalApps(totalApps)
                .totalExecutors(totalExecutors)
                .healthyExecutors(healthyExecutors)
                .errorExecutors(errorExecutors)
                .offlineExecutors(offlineExecutors)
                .totalChains(totalChains)
                .enabledChains(enabledChains)
                .totalDesigns(totalDesigns)
                .todayExecutions(todayExecutions)
                .avgExecutionMs(avgExecutionMs)
                .successRate(successRate)
                .build();

        log.info("仪表盘统计数据: totalApps={} totalChains={} enabledChains={} totalDesigns={} todayExec={} avgMs={} rate={}%",
                totalApps, totalChains, enabledChains, totalDesigns, todayExecutions,
                String.format("%.1f", avgExecutionMs), String.format("%.1f", successRate));

        return vo;
    }
}
