package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.dict.DictTreeBuilder;
import com.zestflow.admin.dict.SystemDictSeeds;
import com.zestflow.admin.model.dto.DictDataCreateDTO;
import com.zestflow.admin.model.dto.DictDataUpdateDTO;
import com.zestflow.admin.model.dto.DictTypeCreateDTO;
import com.zestflow.admin.model.dto.DictTypeUpdateDTO;
import com.zestflow.admin.model.entity.DictDataPO;
import com.zestflow.admin.model.entity.DictTypePO;
import com.zestflow.admin.model.vo.DictDataTreeVO;
import com.zestflow.admin.model.vo.DictDataVO;
import com.zestflow.admin.model.vo.DictTypeVO;
import com.zestflow.admin.repository.DictDataMapper;
import com.zestflow.admin.repository.DictTypeMapper;
import com.zestflow.admin.service.DictTypeService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictTypeServiceImpl implements DictTypeService {

    private static final long SYSTEM_TEMPLATE_TENANT_ID = 1L;

    private final DictTypeMapper dictTypeMapper;
    private final DictDataMapper dictDataMapper;
    private final TenantAppContext tenantAppContext;

    /** 本地缓存：tenantId:typeCode → DictDataVO list */
    private final Map<String, List<DictDataVO>> dictDataCache = new ConcurrentHashMap<>();

    /** 确保字典数据项存在的锁 */
    private final Object ensureLock = new Object();

    // ==================== 系统字典种子 ====================

    @PostConstruct
    public void initSystemDicts() {
        log.info("开始初始化系统字典数据");
        int sort = 1;
        for (SystemDictSeeds.Seed seed : SystemDictSeeds.all()) {
            initSystemDictType(seed.code(), seed.name(), seed.items(), sort++);
        }
        log.info("系统字典数据初始化完成");
    }

    /**
     * 确保系统字典类型存在，并补齐缺失的数据项（已有类型不会整类跳过）。
     */
    private void initSystemDictType(String code, String name, List<DictDataPO> dataItems, int typeSort) {
        DictTypePO exists = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<DictTypePO>()
                        .eq(DictTypePO::getTenantId, SYSTEM_TEMPLATE_TENANT_ID)
                        .eq(DictTypePO::getCode, code));
        if (exists == null) {
            DictTypePO po = new DictTypePO();
            po.setCode(code);
            po.setName(name);
            po.setStatus(1);
            po.setSort(typeSort);
            po.setTenantId(SYSTEM_TEMPLATE_TENANT_ID);
            dictTypeMapper.insert(po);
            log.info("系统字典类型创建 code={} name={}", code, name);
        }

        if (dataItems.isEmpty()) {
            return;
        }
        for (int i = 0; i < dataItems.size(); i++) {
            ensureSystemDictDataItem(code, dataItems.get(i), i + 1, i == 0);
        }
    }

    private void ensureSystemDictDataItem(String typeCode, DictDataPO template, int sort, boolean defaultFlag) {
        Long count = dictDataMapper.selectCount(
                new LambdaQueryWrapper<DictDataPO>()
                        .eq(DictDataPO::getTenantId, SYSTEM_TEMPLATE_TENANT_ID)
                        .eq(DictDataPO::getTypeCode, typeCode)
                        .eq(DictDataPO::getValue, template.getValue()));
        if (count != null && count > 0) {
            return;
        }
        DictDataPO item = new DictDataPO();
        item.setTypeCode(typeCode);
        item.setValue(template.getValue());
        item.setLabel(template.getLabel());
        item.setTagType(template.getTagType());
        item.setSort(sort);
        item.setStatus(1);
        item.setDefaultFlag(defaultFlag ? 1 : 0);
        item.setTenantId(SYSTEM_TEMPLATE_TENANT_ID);
        try {
            dictDataMapper.insert(item);
            log.info("系统字典数据项创建 typeCode={} value={}", typeCode, item.getValue());
        } catch (Exception e) {
            log.warn("系统字典数据项创建失败 typeCode={} value={}", typeCode, item.getValue(), e);
        }
    }

    // ==================== 字典类型 CRUD ====================

    @Override
    public IPage<DictTypeVO> list(String keyword, Integer status, Integer page, Integer size) {
        LambdaQueryWrapper<DictTypePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictTypePO::getTenantId, tenantAppContext.getCurrentTenantId());
        if (status != null) {
            wrapper.eq(DictTypePO::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(DictTypePO::getCode, keyword)
                    .or().like(DictTypePO::getName, keyword));
        }
        // 非超管：系统级（app_code IS NULL）+ 有权限的应用级字典
        Set<String> accessibleCodes = tenantAppContext.getCurrentUserAppCodes();
        if (accessibleCodes != null && !accessibleCodes.isEmpty()) {
            wrapper.and(w -> w.isNull(DictTypePO::getAppCode).or().in(DictTypePO::getAppCode, accessibleCodes));
        }
        wrapper.orderByAsc(DictTypePO::getSort).orderByDesc(DictTypePO::getCreatedAt);

        IPage<DictTypePO> poPage = dictTypeMapper.selectPage(new Page<>(page, size), wrapper);
        Page<DictTypeVO> voPage = new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
        voPage.setRecords(poPage.getRecords().stream().map(this::toTypeVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public DictTypeVO getByCode(String code) {
        DictTypePO po = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<DictTypePO>()
                        .eq(DictTypePO::getTenantId, tenantAppContext.getCurrentTenantId())
                        .eq(DictTypePO::getCode, code));
        if (po == null) {
            throw new BizException(ErrorCode.DICT_TYPE_NOT_FOUND);
        }
        // 校验 appCode 可见性：非空 appCode 需在用户可访问范围内
        checkAppCodeAccessible(po.getAppCode());
        DictTypeVO vo = toTypeVO(po);
        vo.setDataList(listDataByCode(code));
        return vo;
    }

    /**
     * 校验当前用户是否有权限访问指定 appCode 的字典
     */
    private void checkAppCodeAccessible(String appCode) {
        if (appCode == null) return; // 系统级字典全员可见
        Set<String> accessibleCodes = tenantAppContext.getCurrentUserAppCodes();
        if (accessibleCodes != null && !accessibleCodes.isEmpty() && !accessibleCodes.contains(appCode)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
    }

    @Override
    public List<DictDataVO> getDictData(String typeCode, String parentTypeCode, String parentValue) {
        String cacheKey = cacheKey(typeCode, parentTypeCode, parentValue);
        List<DictDataVO> cached = dictDataCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<DictDataVO> list = listDataByCode(typeCode, parentTypeCode, parentValue);
        dictDataCache.put(cacheKey, list);
        return list;
    }

    @Override
    public List<DictDataTreeVO> getDictDataTree(String typeCode) {
        List<DictDataPO> items = listDataPoByCode(typeCode);
        Map<String, String> parentLabels = resolveCrossParentLabels(items);
        return DictTreeBuilder.build(typeCode, items, parentLabels);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeVO create(DictTypeCreateDTO dto, String username) {
        // 检查编码唯一性（租户内）
        Long exists = dictTypeMapper.selectCount(
                new LambdaQueryWrapper<DictTypePO>()
                        .eq(DictTypePO::getTenantId, tenantAppContext.getCurrentTenantId())
                        .eq(DictTypePO::getCode, dto.getCode()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.DICT_TYPE_CODE_EXISTS);
        }

        DictTypePO po = new DictTypePO();
        po.setCode(dto.getCode());
        po.setName(dto.getName());
        po.setDescription(dto.getDescription());
        po.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        // sort <=0 时自动取最大值+1
        Integer sort = dto.getSort();
        if (sort == null || sort <= 0) {
            LambdaQueryWrapper<DictTypePO> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(DictTypePO::getSort).last("LIMIT 1");
            DictTypePO maxSort = dictTypeMapper.selectOne(wrapper);
            sort = (maxSort != null ? maxSort.getSort() : 0) + 1;
        }
        po.setSort(sort);

        dictTypeMapper.insert(po);
        log.info("字典类型创建成功 code={} name={} sort={}", po.getCode(), po.getName(), po.getSort());
        return toTypeVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeVO update(Long id, DictTypeUpdateDTO dto) {
        DictTypePO po = dictTypeMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.DICT_TYPE_NOT_FOUND);
        }
        if (dto.getName() != null) po.setName(dto.getName());
        if (dto.getDescription() != null) po.setDescription(dto.getDescription());
        if (dto.getStatus() != null) po.setStatus(dto.getStatus());
        if (dto.getSort() != null) po.setSort(dto.getSort());

        dictTypeMapper.updateById(po);
        clearCache(po.getCode());
        log.info("字典类型更新成功 id={} code={}", id, po.getCode());
        return toTypeVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DictTypePO po = dictTypeMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.DICT_TYPE_NOT_FOUND);
        }
        // 同时删除其下所有数据项
        dictDataMapper.delete(new LambdaQueryWrapper<DictDataPO>()
                .eq(DictDataPO::getTenantId, tenantAppContext.getCurrentTenantId())
                .eq(DictDataPO::getTypeCode, po.getCode()));
        dictTypeMapper.deleteById(id);
        clearCache(po.getCode());
        log.info("字典类型删除成功 id={} code={}", id, po.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id) {
        DictTypePO po = dictTypeMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.DICT_TYPE_NOT_FOUND);
        }
        int newStatus = po.getStatus() == 1 ? 0 : 1;
        po.setStatus(newStatus);
        dictTypeMapper.updateById(po);
        clearCache(po.getCode());
        log.info("字典类型状态切换 id={} newStatus={}", id, newStatus);
    }

    // ==================== 字典数据项 CRUD ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictDataVO addData(DictDataCreateDTO dto, String username) {
        // 检查字典类型是否存在
        DictTypePO typePo = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<DictTypePO>()
                        .eq(DictTypePO::getTenantId, tenantAppContext.getCurrentTenantId())
                        .eq(DictTypePO::getCode, dto.getTypeCode()));
        if (typePo == null) {
            throw new BizException(ErrorCode.DICT_TYPE_NOT_FOUND);
        }

        DictDataPO po = new DictDataPO();
        po.setTypeCode(dto.getTypeCode());
        po.setParentId(dto.getParentId());
        po.setParentTypeCode(blankToNull(dto.getParentTypeCode()));
        po.setParentValue(blankToNull(dto.getParentValue()));
        validateParentId(dto.getTypeCode(), null, dto.getParentId());
        po.setLabel(dto.getLabel());
        po.setValue(dto.getValue());
        // sort <=0 时自动取当前类型下最大值+1
        Integer sort = dto.getSort();
        if (sort == null || sort <= 0) {
            LambdaQueryWrapper<DictDataPO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DictDataPO::getTenantId, tenantAppContext.getCurrentTenantId())
                    .eq(DictDataPO::getTypeCode, dto.getTypeCode())
                    .orderByDesc(DictDataPO::getSort).last("LIMIT 1");
            DictDataPO maxSort = dictDataMapper.selectOne(wrapper);
            sort = (maxSort != null ? maxSort.getSort() : 0) + 1;
        }
        po.setSort(sort);

        po.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        po.setTagType(dto.getTagType());
        po.setDefaultFlag(dto.getDefaultFlag() != null ? dto.getDefaultFlag() : 0);
        po.setRemark(dto.getRemark());
        po.setExtra(dto.getExtra());

        dictDataMapper.insert(po);
        clearCache(dto.getTypeCode());
        log.info("字典数据项创建成功 typeCode={} label={} value={} sort={}", dto.getTypeCode(), dto.getLabel(), dto.getValue(), po.getSort());
        return toDataVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictDataVO updateData(Long id, DictDataUpdateDTO dto) {
        DictDataPO po = dictDataMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.DICT_DATA_NOT_FOUND);
        }
        if (dto.getLabel() != null) po.setLabel(dto.getLabel());
        if (dto.getValue() != null) po.setValue(dto.getValue());
        if (dto.getParentId() != null) {
            if (dto.getParentId() <= 0) {
                po.setParentId(null);
            } else {
                validateParentId(po.getTypeCode(), id, dto.getParentId());
                po.setParentId(dto.getParentId());
            }
        }
        if (dto.getParentTypeCode() != null) po.setParentTypeCode(blankToNull(dto.getParentTypeCode()));
        if (dto.getParentValue() != null) po.setParentValue(blankToNull(dto.getParentValue()));
        if (dto.getSort() != null) po.setSort(dto.getSort());
        if (dto.getStatus() != null) po.setStatus(dto.getStatus());
        if (dto.getTagType() != null) po.setTagType(dto.getTagType());
        if (dto.getDefaultFlag() != null) po.setDefaultFlag(dto.getDefaultFlag());
        if (dto.getRemark() != null) po.setRemark(dto.getRemark());
        if (dto.getExtra() != null) po.setExtra(dto.getExtra());

        dictDataMapper.updateById(po);
        clearCache(po.getTypeCode());
        log.info("字典数据项更新成功 id={}", id);
        return toDataVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteData(Long id) {
        DictDataPO po = dictDataMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.DICT_DATA_NOT_FOUND);
        }
        Long childCount = dictDataMapper.selectCount(
                new LambdaQueryWrapper<DictDataPO>()
                        .eq(DictDataPO::getTenantId, tenantAppContext.getCurrentTenantId())
                        .eq(DictDataPO::getTypeCode, po.getTypeCode())
                        .eq(DictDataPO::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(ErrorCode.DICT_DATA_HAS_CHILDREN);
        }
        dictDataMapper.deleteById(id);
        clearCache(po.getTypeCode());
        log.info("字典数据项删除成功 id={}", id);
    }

    // ==================== 字典种子/确保方法 ====================

    @Override
    public void ensureDictData(String typeCode, String value, String label) {
        synchronized (ensureLock) {
            // 确保字典类型存在
            DictTypePO typePo = dictTypeMapper.selectOne(
                    new LambdaQueryWrapper<DictTypePO>()
                            .eq(DictTypePO::getTenantId, tenantAppContext.getCurrentTenantId())
                            .eq(DictTypePO::getCode, typeCode));
            if (typePo == null) {
                DictTypePO newType = new DictTypePO();
                newType.setCode(typeCode);
                newType.setName(typeCode);
                newType.setStatus(1);
                newType.setSort(1);
                newType.setTenantId(tenantAppContext.getCurrentTenantId());
                dictTypeMapper.insert(newType);
                log.info("字典类型自动创建 code={}", typeCode);
            }

            // 确保字典数据项存在
            Long count = dictDataMapper.selectCount(
                    new LambdaQueryWrapper<DictDataPO>()
                            .eq(DictDataPO::getTenantId, tenantAppContext.getCurrentTenantId())
                            .eq(DictDataPO::getTypeCode, typeCode)
                            .eq(DictDataPO::getValue, value));
            if (count == null || count == 0) {
                DictDataPO data = new DictDataPO();
                data.setTypeCode(typeCode);
                data.setValue(value);
                data.setLabel(label);
                data.setStatus(1);
                dictDataMapper.insert(data);
                clearCache(typeCode);
                log.info("字典数据项自动创建 typeCode={} value={} label={}", typeCode, value, label);
            }
        }
    }

    // ==================== 私有方法 ====================

    private List<DictDataPO> listDataPoByCode(String code) {
        LambdaQueryWrapper<DictDataPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictDataPO::getTenantId, tenantAppContext.getCurrentTenantId())
                .eq(DictDataPO::getTypeCode, code)
                .orderByAsc(DictDataPO::getSort)
                .orderByDesc(DictDataPO::getDefaultFlag);
        return dictDataMapper.selectList(wrapper);
    }

    private Map<String, String> resolveCrossParentLabels(List<DictDataPO> items) {
        Map<String, String> labels = new HashMap<>();
        for (DictDataPO item : items) {
            if (!StringUtils.hasText(item.getParentTypeCode()) || !StringUtils.hasText(item.getParentValue())) {
                continue;
            }
            String parentType = item.getParentTypeCode().trim();
            String parentValue = item.getParentValue().trim();
            if (labels.containsKey(parentValue)) {
                continue;
            }
            DictDataPO parent = dictDataMapper.selectOne(
                    new LambdaQueryWrapper<DictDataPO>()
                            .eq(DictDataPO::getTenantId, tenantAppContext.getCurrentTenantId())
                            .eq(DictDataPO::getTypeCode, parentType)
                            .eq(DictDataPO::getValue, parentValue)
                            .last("LIMIT 1"));
            labels.put(parentValue, parent != null ? parent.getLabel() : parentValue);
        }
        return labels;
    }

    private void validateParentId(String typeCode, Long selfId, Long parentId) {
        if (parentId == null || parentId <= 0) {
            return;
        }
        DictDataPO parent = dictDataMapper.selectById(parentId);
        if (parent == null
                || !Objects.equals(parent.getTenantId(), tenantAppContext.getCurrentTenantId())
                || !typeCode.equals(parent.getTypeCode())) {
            throw new BizException(ErrorCode.DICT_DATA_PARENT_INVALID);
        }
        if (selfId == null) {
            return;
        }
        List<DictDataPO> siblings = listDataPoByCode(typeCode);
        Map<Long, Long> parentMap = new HashMap<>();
        for (DictDataPO item : siblings) {
            if (item.getParentId() != null) {
                parentMap.put(item.getId(), item.getParentId());
            }
        }
        parentMap.put(selfId, parentId);
        if (DictTreeBuilder.wouldCreateCycle(parentMap, selfId, parentId)) {
            throw new BizException(ErrorCode.DICT_DATA_PARENT_CYCLE);
        }
    }

    private List<DictDataVO> listDataByCode(String code, String parentTypeCode, String parentValue) {
        LambdaQueryWrapper<DictDataPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictDataPO::getTenantId, tenantAppContext.getCurrentTenantId())
                .eq(DictDataPO::getTypeCode, code);
        if (StringUtils.hasText(parentTypeCode)) {
            wrapper.eq(DictDataPO::getParentTypeCode, parentTypeCode.trim());
        }
        if (StringUtils.hasText(parentValue)) {
            wrapper.eq(DictDataPO::getParentValue, parentValue.trim());
        }
        wrapper.orderByAsc(DictDataPO::getSort)
                .orderByDesc(DictDataPO::getDefaultFlag);
        List<DictDataPO> poList = dictDataMapper.selectList(wrapper);
        return poList.stream().map(this::toDataVO).collect(Collectors.toList());
    }

    private List<DictDataVO> listDataByCode(String code) {
        return listDataByCode(code, null, null);
    }

    private void clearCache(String typeCode) {
        String prefix = tenantAppContext.getCurrentTenantId() + ":" + typeCode;
        dictDataCache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String cacheKey(String typeCode, String parentTypeCode, String parentValue) {
        return tenantAppContext.getCurrentTenantId() + ":" + typeCode + ":"
                + (parentTypeCode == null ? "" : parentTypeCode) + ":"
                + (parentValue == null ? "" : parentValue);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private DictTypeVO toTypeVO(DictTypePO po) {
        return DictTypeVO.builder()
                .id(po.getId())
                .code(po.getCode())
                .name(po.getName())
                .description(po.getDescription())
                .status(po.getStatus())
                .sort(po.getSort())
                .updatedBy(po.getUpdatedBy())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private DictDataVO toDataVO(DictDataPO po) {
        return DictDataVO.builder()
                .id(po.getId())
                .typeCode(po.getTypeCode())
                .parentId(po.getParentId())
                .parentTypeCode(po.getParentTypeCode())
                .parentValue(po.getParentValue())
                .label(po.getLabel())
                .value(po.getValue())
                .sort(po.getSort())
                .status(po.getStatus())
                .tagType(po.getTagType())
                .defaultFlag(po.getDefaultFlag())
                .remark(po.getRemark())
                .extra(po.getExtra())
                .updatedBy(po.getUpdatedBy())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
