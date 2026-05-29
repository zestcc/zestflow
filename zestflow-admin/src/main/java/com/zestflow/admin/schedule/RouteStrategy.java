package com.zestflow.admin.schedule;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;

import java.util.List;

/**
 * 路由策略 — 从在线执行器列表中选择一个目标
 */
public interface RouteStrategy {

    /** 策略名称，与 DB 字段 route_strategy 对应 */
    String name();

    /** 从列表中选出一个执行器 */
    ExecutorRegistryPO select(List<ExecutorRegistryPO> executors, String chainCode);
}
