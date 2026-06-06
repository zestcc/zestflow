package com.zestflow.admin.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.common.model.dto.ChainSyncDTO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.registry.RegistryOnlineQuerySupport;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 多 Executor 链版本漂移对账 — 定期比对各实例 active-codes（对标 Nacos 配置一致性巡检）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "zestflow.admin.reconcile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExecutorChainDriftMonitor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutorRegistryMapper executorRegistryMapper;
    private final RegistryLiveStore liveStore;
    private final ExecutorProxyService executorProxyService;
    private final AdminRuntimeStateStore runtimeStateStore;
    private final RestTemplate restTemplate;

    @Value("${zestflow.admin.protocol:http}")
    private String protocol;

    private volatile DriftSnapshot lastSnapshot = DriftSnapshot.clean();

    public void reconcileActiveChains() {
        List<ExecutorRegistryPO> online = RegistryOnlineQuerySupport.listLiveOnlineExecutors(
                executorRegistryMapper, liveStore, null);
        if (online.size() < 2) {
            lastSnapshot = DriftSnapshot.clean();
            return;
        }

        Map<String, List<ExecutorRegistryPO>> byApp = online.stream()
                .filter(e -> e.getAppCode() != null && !e.getAppCode().isBlank())
                .collect(Collectors.groupingBy(ExecutorRegistryPO::getAppCode));

        List<String> driftApps = new ArrayList<>();
        Map<String, Map<String, Set<String>>> driftDetails = new HashMap<>();

        for (Map.Entry<String, List<ExecutorRegistryPO>> entry : byApp.entrySet()) {
            String appCode = entry.getKey();
            List<ExecutorRegistryPO> executors = entry.getValue();
            if (executors.size() < 2) {
                continue;
            }
            Map<String, Set<String>> perExecutor = new HashMap<>();
            Set<String> baseline = null;
            boolean drift = false;
            for (ExecutorRegistryPO executor : executors) {
                Set<String> active = fetchActiveCodes(executor);
                perExecutor.put(executor.getExecutorId(), active);
                if (baseline == null) {
                    baseline = active;
                } else if (!baseline.equals(active)) {
                    drift = true;
                }
            }
            if (drift) {
                driftApps.add(appCode);
                driftDetails.put(appCode, perExecutor);
                log.warn("[chain-drift] appCode={} executors={} active-codes 不一致: {}",
                        appCode, executors.size(), formatDrift(perExecutor));
            }
        }

        reconcileChainSyncReports(online);

        lastSnapshot = driftApps.isEmpty()
                ? DriftSnapshot.clean()
                : new DriftSnapshot(true, driftApps, driftDetails);
    }

    private void reconcileChainSyncReports(List<ExecutorRegistryPO> online) {
        Map<String, ChainSyncDTO> syncMap = runtimeStateStore.getAllChainSync();
        if (syncMap.isEmpty()) {
            return;
        }
        Set<String> onlineIds = online.stream()
                .map(ExecutorRegistryPO::getExecutorId)
                .collect(Collectors.toSet());
        for (Map.Entry<String, ChainSyncDTO> entry : syncMap.entrySet()) {
            if (!onlineIds.contains(entry.getKey())) {
                continue;
            }
            ChainSyncDTO sync = entry.getValue();
            if (sync == null || sync.getLoadedChains() == null) {
                continue;
            }
            if ("FAILED".equalsIgnoreCase(sync.getStatus())) {
                log.warn("[chain-drift] executorId={} 链同步 FAILED: {}", entry.getKey(), sync.getErrorMessage());
            }
        }
    }

    private Set<String> fetchActiveCodes(ExecutorRegistryPO executor) {
        try {
            String url = protocol + "://" + executor.getExecutorHost() + ":" + executor.getExecutorPort()
                    + "/api/chains/active-codes";
            String json = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(executorProxyService.executorHeaders()), String.class).getBody();
            if (json == null || json.isBlank()) {
                return Set.of();
            }
            List<String> codes = MAPPER.readValue(json, new TypeReference<>() {});
            return new TreeSet<>(codes);
        } catch (Exception e) {
            log.warn("[chain-drift] 读取 active-codes 失败 executorId={}", executor.getExecutorId(), e);
            return Set.of();
        }
    }

    private static String formatDrift(Map<String, Set<String>> perExecutor) {
        StringBuilder sb = new StringBuilder();
        perExecutor.forEach((id, codes) -> sb.append(id).append('=').append(codes).append(';'));
        return sb.toString();
    }

    public DriftSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public record DriftSnapshot(
            boolean driftDetected,
            List<String> driftAppCodes,
            Map<String, Map<String, Set<String>>> details
    ) {
        static DriftSnapshot clean() {
            return new DriftSnapshot(false, List.of(), Map.of());
        }
    }
}
