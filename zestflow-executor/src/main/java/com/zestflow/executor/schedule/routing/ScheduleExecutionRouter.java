package com.zestflow.executor.schedule.routing;

import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.PeerExecutorDTO;
import com.zestflow.executor.http.ChainExecuteFacade;
import com.zestflow.executor.registry.AdminClient;
import com.zestflow.executor.registry.ExecutorProperties;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 链调度执行路由 — local 本进程执行；其他策略走 Admin 注册表 + Failover。
 */
@Slf4j
@RequiredArgsConstructor
public class ScheduleExecutionRouter {

    private final AdminClient adminClient;
    private final ChainExecuteFacade chainExecuteFacade;
    private final RemoteScheduleExecutorClient remoteClient;
    private final ScheduleRouteStrategyRegistry strategyRegistry;
    private final ExecutorProperties executorProperties;

    @Value
    public static class RoutedExecution {
        ChainExecuteResultDTO result;
        String executorId;
        int attempted;
    }

    public RoutedExecution execute(String routeStrategy, String chainCode, ChainExecuteRequestDTO request) {
        ScheduleRouteStrategy strategy = strategyRegistry.resolve(routeStrategy);
        if (strategy == null) {
            return localExecution(request);
        }

        List<PeerExecutorDTO> peers = adminClient.fetchOnlinePeers(executorProperties.getAppCode());
        if (peers.isEmpty()) {
            log.warn("无在线对等 Executor，回退本地执行 chainCode={} routeStrategy={}", chainCode, routeStrategy);
            return localExecution(request);
        }

        List<PeerExecutorDTO> sorted = peers.stream()
                .sorted(Comparator.comparing(PeerExecutorDTO::getExecutorId, Comparator.nullsLast(String::compareTo)))
                .toList();

        PeerExecutorDTO primary = strategy.select(sorted, chainCode);
        if (primary == null) {
            log.warn("路由策略未选中 Executor，回退本地执行 chainCode={} routeStrategy={}", chainCode, routeStrategy);
            return localExecution(request);
        }

        List<PeerExecutorDTO> ordered = orderWithPrimaryFirst(sorted, primary);
        ChainExecuteResultDTO last = null;
        String usedExecutorId = null;
        int attempted = 0;

        for (PeerExecutorDTO peer : ordered) {
            attempted++;
            if (isSelf(peer)) {
                try {
                    last = chainExecuteFacade.executeCore(request);
                } catch (Exception e) {
                    log.warn("本地调度执行失败 chainCode={} error={}", chainCode, e.getMessage());
                    last = errorResult(chainCode, e.getMessage());
                }
            } else {
                last = remoteClient.execute(peer.getHost(), peer.getPort(), request);
            }
            usedExecutorId = peer.getExecutorId();
            if (last != null && last.isSuccess()) {
                log.info("调度路由成功 chainCode={} executorId={} attempted={} routeStrategy={}",
                        chainCode, usedExecutorId, attempted, routeStrategy);
                return new RoutedExecution(last, usedExecutorId, attempted);
            }
            log.warn("调度路由尝试失败 chainCode={} executorId={} attempt={}/{} error={}",
                    chainCode, usedExecutorId, attempted, ordered.size(),
                    last != null ? last.getErrorMessage() : "unknown");
        }

        return new RoutedExecution(last, usedExecutorId, attempted);
    }

    private RoutedExecution localExecution(ChainExecuteRequestDTO request) {
        try {
            ChainExecuteResultDTO result = chainExecuteFacade.executeCore(request);
            return new RoutedExecution(result, resolveLocalExecutorId(), 1);
        } catch (Exception e) {
            return new RoutedExecution(errorResult(request.getChainCode(), e.getMessage()), resolveLocalExecutorId(), 1);
        }
    }

    private boolean isSelf(PeerExecutorDTO peer) {
        return Objects.equals(peer.getHost(), executorProperties.getHost())
                && peer.getPort() == executorProperties.getPort()
                && Objects.equals(peer.getAppCode(), executorProperties.getAppCode());
    }

    private String resolveLocalExecutorId() {
        return String.format("%s@%s:%d",
                executorProperties.getAppCode(),
                executorProperties.getHost(),
                executorProperties.getPort());
    }

    static List<PeerExecutorDTO> orderWithPrimaryFirst(List<PeerExecutorDTO> online, PeerExecutorDTO primary) {
        List<PeerExecutorDTO> rest = new ArrayList<>();
        for (PeerExecutorDTO peer : online) {
            if (!Objects.equals(peer.getExecutorId(), primary.getExecutorId())) {
                rest.add(peer);
            }
        }
        rest.sort(Comparator.comparing(PeerExecutorDTO::getExecutorId, Comparator.nullsLast(String::compareTo)));
        List<PeerExecutorDTO> ordered = new ArrayList<>(rest.size() + 1);
        ordered.add(primary);
        ordered.addAll(rest);
        return ordered;
    }

    private static ChainExecuteResultDTO errorResult(String chainCode, String message) {
        ChainExecuteResultDTO dto = new ChainExecuteResultDTO();
        dto.setChainCode(chainCode);
        dto.setStatus(com.zestflow.common.constant.ChainConstants.CHAIN_FAILED);
        dto.setErrorMessage(message);
        return dto;
    }
}
