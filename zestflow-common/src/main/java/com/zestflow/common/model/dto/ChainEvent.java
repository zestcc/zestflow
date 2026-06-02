package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 链执行事件 — Executor 发射、Collector 采集的核心数据单元
 * <p>
 * 事件类型分为 7 种，覆盖链条执行全生命周期：
 * CHAIN_STARTED / NODE_STARTED / NODE_COMPLETED / NODE_FAILED /
 * CHAIN_COMPLETED / CHAIN_FAILED / CHAIN_TIMEOUT
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件类型 */
    public enum EventType {
        CHAIN_STARTED,
        CHAIN_COMPLETED,
        CHAIN_FAILED,
        CHAIN_TIMEOUT,
        NODE_STARTED,
        NODE_COMPLETED,
        NODE_FAILED,
        NODE_RETRYING,
        NODE_RETRY_EXHAUSTED,
        NODE_FALLBACK_START,
        NODE_FALLBACK_SUCCESS,
        NODE_FALLBACK_FAILED,
        NODE_TIMEOUT,
        CHAIN_COMPENSATED,
        NODE_COMPENSATING,
        NODE_COMPENSATED
    }

    /** 事件全局唯一 ID（UUID，避免分布式 ID 中心依赖） */
    private String eventId;

    /** 事件类型 */
    private EventType eventType;

    /** 执行追踪 ID（一次链执行全局唯一，用于把同一次执行的所有事件聚合为 Trace） */
    private String executionId;

    /** 链实例 ID */
    private String chainId;

    /** 链名称 */
    private String chainName;

    /** 节点实例 ID（非节点级事件时为空） */
    private String nodeId;

    /** 节点名称（非节点级事件时为空） */
    private String nodeName;

    /** 执行器 ID（appCode@host:port） */
    private String executorId;

    /** 应用编码 */
    private String appCode;

    /** 应用名（分组标识） */
    private String appName;

    /** 执行入参 JSON */
    private String params;

    /** 执行结果 JSON（完成事件时填充） */
    private String result;

    /** 错误消息（失败/超时事件时填充） */
    private String errorMessage;

    /** 执行耗时（毫秒，完成事件时填充） */
    private Long costMs;

    /** 节点状态（0=失败, 1=成功，对标 RegistryConstants） */
    private Integer status;

    /** 事件发生时间戳（毫秒） */
    private long timestamp;

    /** 租户 ID */
    private Long tenantId;

    /** 扩展元数据 JSON */
    private String metadata;
}
