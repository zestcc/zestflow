package com.zestflow.executor.schedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulePO {
    private Long id;
    private String chainCode;
    private String chainName;
    private String cron;
    private String scheduleKind;
    private String routeStrategy;
    private Integer shardTotal;
    private String shardParam;
    private String misfirePolicy;
    private String params;
    private Integer status;
    private String remark;
    private Long tenantId;
    private String appCode;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
}
