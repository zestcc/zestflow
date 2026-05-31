package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集器注册视图 VO
 */
@Data
@Builder
public class CollectorRegistryVO {

    private Long id;
    private String collectorId;
    private String appCode;
    private String appName;
    private String collectorHost;
    private Integer collectorPort;
    private Integer status;
    private LocalDateTime lastHeartbeat;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
