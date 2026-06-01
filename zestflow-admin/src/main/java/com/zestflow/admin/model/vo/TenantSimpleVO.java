package com.zestflow.admin.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSimpleVO {

    private Long id;
    private String name;
    private String code;
    /** 是否当前选中的租户 */
    private boolean current;
    /** 是否租户管理员 */
    private boolean tenantAdmin;
}
