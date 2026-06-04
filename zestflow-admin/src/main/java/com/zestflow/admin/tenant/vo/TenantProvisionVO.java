package com.zestflow.admin.tenant.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantProvisionVO {

    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private String tenantType;
    private String provisionSource;
    private LocalDateTime expiresAt;
    private int roles = 0;
    private int dictTypes = 0;
    private int dictData = 0;
    private int playgroundScenes = 0;
    private int schedules = 0;
    private int scenesCloned;
    private int itemsCloned;
}
