package com.zestflow.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 日志分析查询参数 — 对标 xxl-job 调度报表 / n8n Insights 时间窗筛选
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogAnalyticsQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private String appCode;
    private String appName;
    private String executorId;
    private String chainId;

    /** 开始时间（毫秒） */
    private Long startTime;

    /** 结束时间（毫秒） */
    private Long endTime;

    /** 趋势粒度：hour / day，默认 hour */
    @Builder.Default
    private String granularity = "hour";

    /** 排行榜条数，默认 10 */
    @Builder.Default
    private int limit = 10;

    /** 排行维度：count / fail / slow */
    @Builder.Default
    private String rankBy = "count";
}
