package com.zestflow.common.protocol;

import lombok.Builder;
import lombok.Data;

/** Collector 上报本地 EventStats，Admin 补全注册/调度维度后评估并发信 */
@Data
@Builder
public class SlaAlertMetricsReportDTO {

    private Long tenantId;
    private String appCode;
    private EventStats eventStats;
}
