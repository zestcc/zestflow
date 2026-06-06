package com.zestflow.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 执行趋势数据点 — 按时间桶聚合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionTrendPoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 桶起始时间戳（毫秒） */
    private long bucketStart;

    /** 执行次数 */
    private long totalCount;

    /** 成功次数 */
    private long successCount;

    /** 失败次数（含超时） */
    private long failCount;

    /** 平均耗时（毫秒） */
    private double avgCostMs;
}
