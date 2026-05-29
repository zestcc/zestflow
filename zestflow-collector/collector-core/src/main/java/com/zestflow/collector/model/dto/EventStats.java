package com.zestflow.collector.model.dto;

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

    /** 总事件数 */
    private long totalCount;

    /** 按事件类型分类统计 */
    private Map<String, Long> typeDistribution;

    /** 成功率 */
    private double successRate;

    /** 平均耗时（毫秒） */
    private double avgCostMs;

    /** 最大耗时（毫秒） */
    private long maxCostMs;

    /** 失败事件数 */
    private long failCount;
}
