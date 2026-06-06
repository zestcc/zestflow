package com.zestflow.admin.repository;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.admin.alert.TenantAppScope;
import com.zestflow.admin.model.entity.UserAppRolePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserAppRoleMapper extends BaseMapper<UserAppRolePO> {

    /** 所有已分配模块（跨租户，供平台 SLA 扫描） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT tenant_id AS tenantId, app_code AS appCode
            FROM user_app_role
            WHERE app_code IS NOT NULL AND TRIM(app_code) <> ''
            """)
    List<TenantAppScope> selectDistinctTenantApps();
}
