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
public class UserVO {

    private Long id;
    private String username;
    private String email;
    private String avatar;
    private Integer isSuperAdmin;
    private Integer mustChangePassword;
    /** 用户所属租户列表（登录时返回） */
    private List<TenantSimpleVO> tenants;
}
