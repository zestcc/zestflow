package com.zestflow.executor.schedule.routing;

import com.zestflow.common.model.dto.PeerExecutorDTO;

import java.util.List;

/**
 * 链调度路由策略 — 从在线 Executor 列表中选择一个目标。
 */
public interface ScheduleRouteStrategy {

    /** 策略名称，与 zf_schedule.route_strategy 对应 */
    String name();

    PeerExecutorDTO select(List<PeerExecutorDTO> executors, String chainCode);
}
