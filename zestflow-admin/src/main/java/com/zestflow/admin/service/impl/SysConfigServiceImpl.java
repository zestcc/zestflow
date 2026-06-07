package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.SysConfigCreateDTO;
import com.zestflow.admin.model.dto.SysConfigUpdateDTO;
import com.zestflow.admin.model.entity.SysConfigPO;
import com.zestflow.admin.model.vo.SysConfigVO;
import com.zestflow.admin.repository.SysConfigMapper;
import com.zestflow.admin.service.SysConfigService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final TenantAppContext tenantAppContext;

    @Override
    public IPage<SysConfigVO> list(String keyword, String category, Integer status, Integer page, Integer size) {
        LambdaQueryWrapper<SysConfigPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfigPO::getTenantId, tenantAppContext.getCurrentTenantId());
        if (StringUtils.hasText(category)) {
            wrapper.eq(SysConfigPO::getCategory, category.trim());
        }
        if (status != null) {
            wrapper.eq(SysConfigPO::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysConfigPO::getConfigKey, kw)
                    .or().like(SysConfigPO::getConfigName, kw)
                    .or().like(SysConfigPO::getRemark, kw));
        }
        wrapper.orderByAsc(SysConfigPO::getCategory)
                .orderByAsc(SysConfigPO::getSort)
                .orderByDesc(SysConfigPO::getUpdatedAt);

        IPage<SysConfigPO> poPage = sysConfigMapper.selectPage(new Page<>(page, size), wrapper);
        Page<SysConfigVO> voPage = new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
        voPage.setRecords(poPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<String> listCategories() {
        LambdaQueryWrapper<SysConfigPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SysConfigPO::getCategory)
                .eq(SysConfigPO::getTenantId, tenantAppContext.getCurrentTenantId())
                .isNotNull(SysConfigPO::getCategory)
                .groupBy(SysConfigPO::getCategory)
                .orderByAsc(SysConfigPO::getCategory);
        return sysConfigMapper.selectList(wrapper).stream()
                .map(SysConfigPO::getCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public SysConfigVO getById(Long id) {
        SysConfigPO po = requireOwned(id);
        return toVO(po);
    }

    @Override
    public Optional<String> getValue(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return Optional.empty();
        }
        SysConfigPO po = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfigPO>()
                        .eq(SysConfigPO::getTenantId, tenantAppContext.getCurrentTenantId())
                        .eq(SysConfigPO::getConfigKey, configKey.trim())
                        .eq(SysConfigPO::getStatus, 1));
        return po == null ? Optional.empty() : Optional.ofNullable(po.getConfigValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysConfigVO create(SysConfigCreateDTO dto, String username) {
        String key = dto.getConfigKey().trim();
        Long exists = sysConfigMapper.selectCount(
                new LambdaQueryWrapper<SysConfigPO>()
                        .eq(SysConfigPO::getTenantId, tenantAppContext.getCurrentTenantId())
                        .eq(SysConfigPO::getConfigKey, key));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.SYS_CONFIG_KEY_EXISTS);
        }

        SysConfigPO po = new SysConfigPO();
        po.setConfigKey(key);
        po.setConfigName(dto.getConfigName().trim());
        po.setConfigValue(dto.getConfigValue());
        po.setValueType(StringUtils.hasText(dto.getValueType()) ? dto.getValueType().trim() : "json");
        po.setCategory(StringUtils.hasText(dto.getCategory()) ? dto.getCategory().trim() : "system");
        po.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        po.setSort(resolveSort(dto.getSort(), po.getCategory()));
        po.setRemark(dto.getRemark());

        sysConfigMapper.insert(po);
        log.info("系统配置创建 key={} category={}", po.getConfigKey(), po.getCategory());
        return toVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysConfigVO update(Long id, SysConfigUpdateDTO dto) {
        SysConfigPO po = requireOwned(id);
        if (dto.getConfigName() != null) po.setConfigName(dto.getConfigName());
        if (dto.getConfigValue() != null) po.setConfigValue(dto.getConfigValue());
        if (dto.getValueType() != null) po.setValueType(dto.getValueType());
        if (dto.getCategory() != null) po.setCategory(dto.getCategory());
        if (dto.getStatus() != null) po.setStatus(dto.getStatus());
        if (dto.getSort() != null) po.setSort(dto.getSort());
        if (dto.getRemark() != null) po.setRemark(dto.getRemark());

        sysConfigMapper.updateById(po);
        log.info("系统配置更新 id={} key={}", id, po.getConfigKey());
        return toVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireOwned(id);
        sysConfigMapper.deleteById(id);
        log.info("系统配置删除 id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id) {
        SysConfigPO po = requireOwned(id);
        po.setStatus(po.getStatus() != null && po.getStatus() == 1 ? 0 : 1);
        sysConfigMapper.updateById(po);
        log.info("系统配置状态切换 id={} status={}", id, po.getStatus());
    }

    private SysConfigPO requireOwned(Long id) {
        SysConfigPO po = sysConfigMapper.selectById(id);
        if (po == null || !tenantAppContext.getCurrentTenantId().equals(po.getTenantId())) {
            throw new BizException(ErrorCode.SYS_CONFIG_NOT_FOUND);
        }
        return po;
    }

    private int resolveSort(Integer sort, String category) {
        if (sort != null && sort > 0) {
            return sort;
        }
        LambdaQueryWrapper<SysConfigPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfigPO::getTenantId, tenantAppContext.getCurrentTenantId());
        if (StringUtils.hasText(category)) {
            wrapper.eq(SysConfigPO::getCategory, category);
        }
        wrapper.orderByDesc(SysConfigPO::getSort).last("LIMIT 1");
        SysConfigPO max = sysConfigMapper.selectOne(wrapper);
        return (max != null && max.getSort() != null ? max.getSort() : 0) + 1;
    }

    private SysConfigVO toVO(SysConfigPO po) {
        return SysConfigVO.builder()
                .id(po.getId())
                .configKey(po.getConfigKey())
                .configName(po.getConfigName())
                .configValue(po.getConfigValue())
                .valueType(po.getValueType())
                .category(po.getCategory())
                .status(po.getStatus())
                .sort(po.getSort())
                .remark(po.getRemark())
                .updatedBy(po.getUpdatedBy())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
