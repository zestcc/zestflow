package com.zestflow.admin.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String token;
    private UserVO user;
    /** 用户可访问的租户列表 */
    private List<TenantSimpleVO> tenants;
    /** 当前选中的租户 */
    private TenantSimpleVO currentTenant;
}
