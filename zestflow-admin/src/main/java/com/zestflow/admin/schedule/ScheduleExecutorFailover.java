package com.zestflow.admin.schedule;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import lombok.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 调度执行器 failover — 对标 xxl-job 路由失败自动切换下一台。
 */
public final class ScheduleExecutorFailover {

    /** 链执行成功状态（与 Executor 引擎一致） */
    public static final int EXECUTE_SUCCESS = 3;

    private ScheduleExecutorFailover() {
    }

    @Value
    public static class FailoverResult {
        ExecutorRegistryPO executor;
        ChainExecuteResultDTO result;
        int attempted;
    }

    /**
     * 先走路由策略选主节点，失败后按 executorId 顺序尝试其余在线节点。
     * <p>
     * 所有 failover 尝试共用同一幂等键，避免切换节点时重复执行。
     */
    public static FailoverResult executeWithFailover(List<ExecutorRegistryPO> online,
                                                     RouteStrategy strategy,
                                                     String chainCode,
                                                     Map<String, Object> params,
                                                     String idempotencyKey,
                                                     ExecutorClient client) {
        if (online == null || online.isEmpty()) {
            ChainExecuteResultDTO empty = new ChainExecuteResultDTO();
            empty.setChainCode(chainCode);
            empty.setStatus(2);
            empty.setErrorMessage("无可用在线执行器");
            return new FailoverResult(null, empty, 0);
        }

        ExecutorRegistryPO primary = strategy.select(online, chainCode);
        if (primary == null) {
            ChainExecuteResultDTO noRoute = new ChainExecuteResultDTO();
            noRoute.setChainCode(chainCode);
            noRoute.setStatus(2);
            noRoute.setErrorMessage("路由策略未选中执行器");
            return new FailoverResult(null, noRoute, 0);
        }

        List<ExecutorRegistryPO> ordered = orderWithPrimaryFirst(online, primary);
        ChainExecuteResultDTO last = null;
        ExecutorRegistryPO used = null;
        int attempted = 0;

        for (ExecutorRegistryPO executor : ordered) {
            attempted++;
            last = client.execute(executor.getExecutorHost(), executor.getExecutorPort(),
                    buildRequest(chainCode, params, idempotencyKey));
            used = executor;
            if (isSuccess(last)) {
                return new FailoverResult(used, last, attempted);
            }
        }
        return new FailoverResult(used, last, attempted);
    }

    static List<ExecutorRegistryPO> orderWithPrimaryFirst(List<ExecutorRegistryPO> online,
                                                          ExecutorRegistryPO primary) {
        List<ExecutorRegistryPO> rest = new ArrayList<>();
        for (ExecutorRegistryPO executor : online) {
            if (!Objects.equals(executor.getExecutorId(), primary.getExecutorId())) {
                rest.add(executor);
            }
        }
        rest.sort(Comparator.comparing(ExecutorRegistryPO::getExecutorId, Comparator.nullsLast(String::compareTo)));
        List<ExecutorRegistryPO> ordered = new ArrayList<>(rest.size() + 1);
        ordered.add(primary);
        ordered.addAll(rest);
        return ordered;
    }

    public static boolean isSuccess(ChainExecuteResultDTO result) {
        return result != null && result.getStatus() != null && result.getStatus() == EXECUTE_SUCCESS;
    }

    private static ChainExecuteRequestDTO buildRequest(String chainCode,
                                                         Map<String, Object> params,
                                                         String idempotencyKey) {
        ChainExecuteRequestDTO request = ChainExecuteRequestDTO.builder()
                .chainCode(chainCode)
                .params(params)
                .source("admin-schedule")
                .build();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.setIdempotencyKey(idempotencyKey);
        }
        return request;
    }
}
