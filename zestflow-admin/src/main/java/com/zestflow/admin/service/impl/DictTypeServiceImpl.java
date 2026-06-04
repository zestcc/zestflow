package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.DictDataCreateDTO;
import com.zestflow.admin.model.dto.DictDataUpdateDTO;
import com.zestflow.admin.model.dto.DictTypeCreateDTO;
import com.zestflow.admin.model.dto.DictTypeUpdateDTO;
import com.zestflow.admin.model.entity.DictDataPO;
import com.zestflow.admin.model.entity.DictTypePO;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        initSystemDictType("component_type", "元件类型",
                List.of(
                        dataItem("EXECUTOR", "执行器", "primary"),
                        dataItem("PREDICATE", "条件", "warning"),
                        dataItem("SELECTOR", "选择器", "warning"),
                        dataItem("LOADER", "加载器", "info"),
                        dataItem("PARSER", "解析器", ""),
                        dataItem("PRE_PROCESSOR", "前置处理器", ""),
                        dataItem("POST_PROCESSOR", "后置处理器", ""),
                        dataItem("PARAM_BINDER", "参数绑定器", ""),
                        dataItem("PARAM_VALIDATOR", "参数校验器", "")
                ));
        initSystemDictType("execute_strategy", "执行策略",
                List.of(
                        dataItem("NORMAL", "正常", "primary"),
                        dataItem("RETRY_ON_FAILURE", "失败重试", "warning"),
                        dataItem("STOP_ON_EXCEPTION", "异常停止", "danger"),
                        dataItem("IGNORE_EXCEPTION", "忽略异常", "info")
                ));
        initSystemDictType("route_strategy", "路由策略",
                List.of(
                        dataItem("round_robin", "轮询", "primary"),
                        dataItem("hash", "哈希", ""),
                        dataItem("random", "随机", "")
                ));
        initSystemDictType("transaction_propagation", "事务传播策略",
                List.of(
                        dataItem("INHERIT", "继承链级", "info"),
                        dataItem("REQUIRED", "REQUIRED（加入当前事务）", "primary"),
                        dataItem("REQUIRES_NEW", "REQUIRES_NEW（独立新事务）", "warning"),
                        dataItem("NESTED", "NESTED（嵌套事务）", ""),
                        dataItem("SUPPORTS", "SUPPORTS（支持当前事务）", ""),
                        dataItem("NOT_SUPPORTED", "NOT_SUPPORTED（挂起事务）", "danger"),
                        dataItem("MANDATORY", "MANDATORY（必须在事务中）", ""),
                        dataItem("NEVER", "NEVER（禁止事务）", "")
                ));
        initSystemDictType("tag_type", "标签类型",
                List.of(
                        dataItem("primary", "primary", "primary"),
                        dataItem("success", "success", "success"),
                        dataItem("warning", "warning", "warning"),
                        dataItem("danger", "danger", "danger"),
                        dataItem("info", "info", "info")
                ));
        initSystemDictType("app_type", "应用类型", List.of());
        log.info("系统字典数据初始化完成");
    }

    private void initSystemDictType(String code, String name, List<DictDataPO> dataItems) {
        DictTypePO exists = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<DictTypePO>()
                        .eq(DictTypePO::getTenantId, SYSTEM_TEMPLATE_TENANT_ID)
                        .eq(DictTypePO::getCode, code));
        if (exists != null) {
            return;
        }
        DictTypePO po = new DictTypePO();
        po.setCode(code);
        po.setName(name);
        po.setStatus(1);
        po.setSort(1);
        po.setTenantId(SYSTEM_TEMPLATE_TENANT_ID);
        dictTypeMapper.insert(po);
        log.info("系统字典类型创建 code={} name={}", code, name);

        if (dataItems.isEmpty()) {
            return;
        }
        for (int i = 0; i < dataItems.size(); i++) {
            DictDataPO item = dataItems.get(i);
            item.setTypeCode(code);
            item.setSort(i + 1);
            item.setStatus(1);
            item.setDefaultFlag(i == 0 ? 1 : 0);
            item.setTenantId(SYSTEM_TEMPLATE_TENANT_ID);
            try {
                dictDataMapper.insert(item);
            } catch (Exception e) {
                log.warn("系统字典数据项创建失败（可能已存在）typeCode={} value={}", code, item.getValue());
            }
        }
    }

    private static DictDataPO dataItem(String value, String label, String tagType) {
        DictDataPO po = new DictDataPO();
        po.setValue(value);
        po.setLabel(label);
        po.setTagType(tagType.isEmpty() ? null : tagType);
        return po;
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
    public List<DictDataVO> getDictData(String typeCode) {
        String cacheKey = cacheKey(typeCode);
        List<DictDataVO> cached = dictDataCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<DictDataVO> list = listDataByCode(typeCode);
        dictDataCache.put(cacheKey, list);
        return list;
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
        if (dto.getSort() != null) po.setSort(dto.getSort());
        if (dto.getStatus() != null) po.setStatus(dto.getStatus());
        if (dto.getTagType() != null) po.setTagType(dto.getTagType());
        if (dto.getDefaultFlag() != null) po.setDefaultFlag(dto.getDefaultFlag());
        if (dto.getRemark() != null) po.setRemark(dto.getRemark());

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

    private List<DictDataVO> listDataByCode(String code) {
        List<DictDataPO> poList = dictDataMapper.selectList(
                new LambdaQueryWrapper<DictDataPO>()
                        .eq(DictDataPO::getTenantId, tenantAppContext.getCurrentTenantId())
                        .eq(DictDataPO::getTypeCode, code)
                        .orderByAsc(DictDataPO::getSort)
                        .orderByDesc(DictDataPO::getDefaultFlag));
        return poList.stream().map(this::toDataVO).collect(Collectors.toList());
    }

    private void clearCache(String typeCode) {
        dictDataCache.remove(cacheKey(typeCode));
    }

    private String cacheKey(String typeCode) {
        return tenantAppContext.getCurrentTenantId() + ":" + typeCode;
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
                .label(po.getLabel())
                .value(po.getValue())
                .sort(po.getSort())
                .status(po.getStatus())
                .tagType(po.getTagType())
                .defaultFlag(po.getDefaultFlag())
                .remark(po.getRemark())
                .updatedBy(po.getUpdatedBy())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
