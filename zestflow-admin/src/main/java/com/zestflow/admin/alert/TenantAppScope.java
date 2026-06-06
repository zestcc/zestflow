package com.zestflow.admin.alert;

import lombok.Data;

/** 租户 + 应用模块扫描范围 */
@Data
public class TenantAppScope {

    private Long tenantId;
    private String appCode;
}
