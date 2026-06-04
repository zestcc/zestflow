package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.TenantCreateDTO;
import com.zestflow.admin.model.dto.TenantUpdateDTO;
import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.model.entity.UserTenantPO;
import com.zestflow.admin.model.vo.TenantSimpleVO;
import com.zestflow.admin.model.vo.TenantVO;
import com.zestflow.admin.repository.TenantMapper;
import com.zestflow.admin.repository.UserTenantMapper;
import com.zestflow.admin.service.TenantService;
import com.zestflow.admin.tenant.ProvisionSources;
import com.zestflow.admin.tenant.TenantProvisionRequest;
import com.zestflow.admin.tenant.TenantProvisionResult;
import com.zestflow.admin.tenant.TenantProvisioner;
import com.zestflow.admin.tenant.TenantTypes;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantMapper tenantMapper;
    private final UserTenantMapper userTenantMapper;
    private final TenantProvisioner tenantProvisioner;

    @Override
    public List<TenantVO> listAll() {
        List<TenantPO> list = tenantMapper.selectList(null);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public TenantVO getById(Long id) {
        TenantPO po = tenantMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }
        return toVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantVO create(TenantCreateDTO dto) {
        TenantProvisionRequest request = TenantProvisionRequest.builder()
                .name(dto.getName())
                .code(dto.getCode())
                .description(dto.getDescription())
                .tenantType(TenantTypes.STANDARD)
                .provisionSource(ProvisionSources.ADMIN)
                .createdBy("admin")
                .build();
        TenantProvisionResult result = tenantProvisioner.provision(request);
        return toVO(result.getTenant());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantVO update(Long id, TenantUpdateDTO dto) {
        TenantPO po = tenantMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }
        if (dto.getName() != null) po.setName(dto.getName());
        if (dto.getDescription() != null) po.setDescription(dto.getDescription());
        if (dto.getStatus() != null) po.setStatus(dto.getStatus());
        po.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(po);
        log.info("租户更新成功 tenantId={}", id);
        return toVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TenantPO po = tenantMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND);
        }
        if (id == 1L) {
            throw new BizException(ErrorCode.TENANT_CANNOT_DELETE_SYSTEM);
        }
        if (TenantTypes.TRIAL.equals(po.getTenantType())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "试玩租户请由定时任务回收");
        }
        tenantMapper.deleteById(id);
        log.info("租户删除成功 tenantId={}", id);
    }

    @Override
    public List<TenantSimpleVO> listUserTenants(Long userId) {
        List<UserTenantPO> userTenants = userTenantMapper.selectList(
                new LambdaQueryWrapper<UserTenantPO>()
                        .eq(UserTenantPO::getUserId, userId)
        );
        if (userTenants.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> tenantIds = userTenants.stream()
                .map(UserTenantPO::getTenantId)
                .collect(Collectors.toList());

        List<TenantPO> tenants = tenantMapper.selectBatchIds(tenantIds);
        return tenants.stream()
                .map(t -> {
                    UserTenantPO ut = userTenants.stream()
                            .filter(u -> u.getTenantId().equals(t.getId()))
                            .findFirst().orElse(null);
                    return TenantSimpleVO.builder()
                            .id(t.getId())
                            .name(t.getName())
                            .code(t.getCode())
                            .current(false)
                            .tenantAdmin(ut != null && ut.getIsTenantAdmin() == 1)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public TenantSimpleVO getDefaultTenant(Long userId) {
        List<TenantSimpleVO> tenants = listUserTenants(userId);
        if (tenants.isEmpty()) {
            return null;
        }
        // 默认取第一个租户
        TenantSimpleVO first = tenants.get(0);
        first.setCurrent(true);
        return first;
    }

    @Override
    public Long switchTenant(Long userId, Long tenantId) {
        UserTenantPO ut = userTenantMapper.selectOne(
                new LambdaQueryWrapper<UserTenantPO>()
                        .eq(UserTenantPO::getUserId, userId)
                        .eq(UserTenantPO::getTenantId, tenantId)
                        .last("LIMIT 1")
        );
        if (ut == null) {
            throw new BizException(ErrorCode.TENANT_NOT_FOUND, "用户未关联该租户");
        }
        return tenantId;
    }

    private TenantVO toVO(TenantPO po) {
        return TenantVO.builder()
                .id(po.getId())
                .name(po.getName())
                .code(po.getCode())
                .description(po.getDescription())
                .status(po.getStatus())
                .tenantType(po.getTenantType())
                .provisionSource(po.getProvisionSource())
                .expiresAt(po.getExpiresAt())
                .lastActiveAt(po.getLastActiveAt())
                .createdBy(po.getCreatedBy())
                .updatedBy(po.getUpdatedBy())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
