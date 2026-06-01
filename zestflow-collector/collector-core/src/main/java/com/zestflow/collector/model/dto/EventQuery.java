package com.zestflow.collector.model.dto;

import com.zestflow.common.model.dto.ChainEvent.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 事件查询参数 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行追踪 ID */
    private String executionId;

    /** 链实例 ID */
    private String chainId;

    /** 执行器 ID */
    private String executorId;

    /** 应用名 */
    private String appName;

    /** 租户 ID */
    private Long tenantId;

    /** 事件类型列表 */
    private List<EventType> eventTypes;

    /** 开始时间（毫秒） */
    private Long startTime;

    /** 结束时间（毫秒） */
    private Long endTime;

    /** 状态（0=失败, 1=成功） */
    private Integer status;

    /** 链名称（模糊匹配） */
    private String chainName;

    /** 关键字（模糊匹配 chainName / nodeName） */
    private String keyword;

    /** 页码，从 1 开始 */
    @Builder.Default
    private int page = 1;

    /** 每页条数 */
    @Builder.Default
    private int pageSize = 20;
}
