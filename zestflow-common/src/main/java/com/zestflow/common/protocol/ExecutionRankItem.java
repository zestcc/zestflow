package com.zestflow.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 执行排行项 — 链 / 执行器 / 节点维度
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRankItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维度键（chainCode / executorId / nodeId） */
    private String key;

    /** 展示名 */
    private String name;

    /** 执行次数或事件次数 */
    private long totalCount;

    /** 失败次数 */
    private long failCount;

    /** 成功率（0-100） */
    private double successRate;

    /** 平均耗时（毫秒） */
    private double avgCostMs;

    /** 最大耗时（毫秒） */
    private long maxCostMs;
}
