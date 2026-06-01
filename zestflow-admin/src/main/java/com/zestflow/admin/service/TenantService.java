package com.zestflow.admin.service;

import com.zestflow.admin.model.dto.TenantCreateDTO;
import com.zestflow.admin.model.dto.TenantUpdateDTO;
import com.zestflow.admin.model.vo.TenantSimpleVO;
import com.zestflow.admin.model.vo.TenantVO;

import java.util.List;

public interface TenantService {

    List<TenantVO> listAll();

    TenantVO getById(Long id);

    TenantVO create(TenantCreateDTO dto);

    TenantVO update(Long id, TenantUpdateDTO dto);

    void delete(Long id);

    /**
     * 获取用户可访问的租户列表（登录时使用）
     */
    List<TenantSimpleVO> listUserTenants(Long userId);

    /**
     * 获取用户默认租户（登录时使用）
     * 优先选上次使用的租户，其次选第一个租户
     */
    TenantSimpleVO getDefaultTenant(Long userId);

    /**
     * 切换租户（返回新 JWT 的 currentTenantId）
     */
    Long switchTenant(Long userId, Long tenantId);
}
