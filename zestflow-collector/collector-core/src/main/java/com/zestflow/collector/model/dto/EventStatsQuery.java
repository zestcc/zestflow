package com.zestflow.collector.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统计查询参数 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStatsQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 应用名 */
    private String appName;

    /** 租户 ID */
    private Long tenantId;

    /** 执行器 ID */
    private String executorId;

    /** 链实例 ID */
    private String chainId;

    /** 开始时间（毫秒） */
    private Long startTime;

    /** 结束时间（毫秒） */
    private Long endTime;
}
