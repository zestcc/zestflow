package com.zestflow.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 事件统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStats implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总事件数（原始事件条数，兼容旧字段） */
    private long totalCount;

    /** 执行次数（按 executionId 去重） */
    private long executionCount;

    /** 成功执行次数 */
    private long successCount;

    /** 进行中执行次数 */
    private long inProgressCount;

    /** 按事件类型分类统计 */
    private Map<String, Long> typeDistribution;

    /** 成功率（0-100，基于执行终态） */
    private double successRate;

    /** 平均耗时（毫秒，链级终态） */
    private double avgCostMs;

    /** P95 耗时（毫秒） */
    private long p95CostMs;

    /** 最大耗时（毫秒） */
    private long maxCostMs;

    /** 失败执行次数（含超时） */
    private long failCount;
}
