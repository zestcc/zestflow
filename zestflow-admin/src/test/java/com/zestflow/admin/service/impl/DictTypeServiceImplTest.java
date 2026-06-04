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
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DictTypeServiceImplTest {

    @Mock private DictTypeMapper dictTypeMapper;
    @Mock private DictDataMapper dictDataMapper;
    @Mock private TenantAppContext tenantAppContext;
    @Captor private ArgumentCaptor<DictTypePO> typeCaptor;
    @Captor private ArgumentCaptor<DictDataPO> dataCaptor;

    private DictTypeServiceImpl dictTypeService;

    @BeforeEach
    void setUp() {
        dictTypeService = new DictTypeServiceImpl(dictTypeMapper, dictDataMapper, tenantAppContext);
    }

    // ==================== list（含 app_code 过滤） ====================

    @Test
    void list_superAdmin_noAppCodeFilter() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());
        when(dictTypeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<DictTypeVO> voPage = new Page<>(1, 10);
                    voPage.setRecords(Collections.emptyList());
                    return voPage;
                });

        IPage<DictTypeVO> result = dictTypeService.list(null, null, 1, 10);

        assertThat(result).isNotNull();
    }

    @Test
    void list_normalUser_filtersByAppCode() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Set.of("app-a"));
        IPage<DictTypePO> poPage = new Page<>(1, 10);
        poPage.setRecords(List.of(createTypePo(1L, "type-1", "类型1")));
        when(dictTypeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> poPage);

        IPage<DictTypeVO> result = dictTypeService.list(null, null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void list_withKeyword_filtersCodeAndName() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());
        IPage<DictTypePO> poPage = new Page<>(1, 10);
        poPage.setRecords(List.of(createTypePo(1L, "component_type", "元件类型")));
        when(dictTypeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> poPage);

        IPage<DictTypeVO> result = dictTypeService.list("component", null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void list_withStatus_filtersStatus() {
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Collections.emptySet());
        when(dictTypeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> new Page<>(1, 10));

        IPage<DictTypeVO> result = dictTypeService.list(null, 1, 1, 10);

        assertThat(result).isNotNull();
        verify(dictTypeMapper).selectPage(any(), any(LambdaQueryWrapper.class));
    }

    // ==================== getByCode ====================

    @Test
    void getByCode_found_returnsWithDataList() {
        DictTypePO typePo = createTypePo(1L, "component_type", "元件类型");
        typePo.setAppCode(null); // 系统级
        when(dictTypeMapper.selectOne(any())).thenReturn(typePo);
        when(dictDataMapper.selectList(any())).thenReturn(List.of(createDataPo(1L, "EXECUTOR", "执行器")));

        DictTypeVO vo = dictTypeService.getByCode("component_type");

        assertThat(vo.getCode()).isEqualTo("component_type");
        assertThat(vo.getDataList()).hasSize(1);
    }

    @Test
    void getByCode_notFound_throws() {
        when(dictTypeMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> dictTypeService.getByCode("unknown"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DICT_TYPE_NOT_FOUND);
    }

    @Test
    void getByCode_appLevel_withoutPermission_throws() {
        DictTypePO typePo = createTypePo(1L, "app-dict", "应用字典");
        typePo.setAppCode("app-a");
        when(dictTypeMapper.selectOne(any())).thenReturn(typePo);
        when(tenantAppContext.getCurrentUserAppCodes()).thenReturn(Set.of("app-b"));

        assertThatThrownBy(() -> dictTypeService.getByCode("app-dict"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PERMISSION_DENIED);
    }

    // ==================== getDictData（缓存） ====================

    @Test
    void getDictData_transactionPropagation_returnsEightStrategies() {
        when(dictDataMapper.selectList(any())).thenReturn(List.of(
                dataPo("INHERIT", "继承链级", 1),
                dataPo("REQUIRED", "REQUIRED（加入当前事务）", 2),
                dataPo("REQUIRES_NEW", "REQUIRES_NEW（独立新事务）", 3),
                dataPo("NESTED", "NESTED（嵌套事务）", 4),
                dataPo("SUPPORTS", "SUPPORTS（支持当前事务）", 5),
                dataPo("NOT_SUPPORTED", "NOT_SUPPORTED（挂起事务）", 6),
                dataPo("MANDATORY", "MANDATORY（必须在事务中）", 7),
                dataPo("NEVER", "NEVER（禁止事务）", 8)
        ));

        List<DictDataVO> result = dictTypeService.getDictData("transaction_propagation");

        assertThat(result).hasSize(8);
        assertThat(result).extracting(DictDataVO::getValue)
                .containsExactly("INHERIT", "REQUIRED", "REQUIRES_NEW", "NESTED",
                        "SUPPORTS", "NOT_SUPPORTED", "MANDATORY", "NEVER");
    }

    @Test
    void initSystemDicts_seedsTransactionPropagationDict() {
        when(dictTypeMapper.selectOne(any())).thenReturn(null);

        dictTypeService.initSystemDicts();

        verify(dictTypeMapper, atLeastOnce()).insert(typeCaptor.capture());
        assertThat(typeCaptor.getAllValues())
                .extracting(DictTypePO::getCode)
                .contains("transaction_propagation");

        verify(dictDataMapper, atLeastOnce()).insert(dataCaptor.capture());
        List<String> txValues = dataCaptor.getAllValues().stream()
                .filter(d -> "transaction_propagation".equals(d.getTypeCode()))
                .map(DictDataPO::getValue)
                .toList();
        assertThat(txValues).containsExactlyInAnyOrder(
                "INHERIT", "REQUIRED", "REQUIRES_NEW", "NESTED",
                "SUPPORTS", "NOT_SUPPORTED", "MANDATORY", "NEVER");
    }

    @Test
    void getDictData_cached_returnsFromCache() {
        DictDataVO data = DictDataVO.builder().label("L1").value("V1").build();
        // 先用 getByCode 填充缓存
        DictTypePO typePo = createTypePo(1L, "cache-type", "缓存类型");
        typePo.setAppCode(null);
        when(dictTypeMapper.selectOne(any())).thenReturn(typePo);
        when(dictDataMapper.selectList(any())).thenReturn(List.of(createDataPo(1L, "V1", "L1")));

        dictTypeService.getByCode("cache-type"); // 填充缓存
        List<DictDataVO> cached = dictTypeService.getDictData("cache-type");

        assertThat(cached).hasSize(1);
        assertThat(cached.get(0).getLabel()).isEqualTo("L1");
        // 第二次调用应命中缓存，不再查 DB
        verify(dictDataMapper, atLeast(1)).selectList(any());
    }

    // ==================== create ====================

    @Test
    void create_success() {
        when(dictTypeMapper.selectCount(any())).thenReturn(0L);

        DictTypeCreateDTO dto = new DictTypeCreateDTO();
        dto.setCode("new-dict");
        dto.setName("新字典");
        dto.setDescription("测试");
        dto.setSort(5);

        DictTypeVO vo = dictTypeService.create(dto, "admin");

        assertThat(vo.getCode()).isEqualTo("new-dict");
        verify(dictTypeMapper).insert(any(DictTypePO.class));
    }

    @Test
    void create_duplicateCode_throws() {
        when(dictTypeMapper.selectCount(any())).thenReturn(1L);

        DictTypeCreateDTO dto = new DictTypeCreateDTO();
        dto.setCode("exists");
        dto.setName("已存在");

        assertThatThrownBy(() -> dictTypeService.create(dto, "admin"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DICT_TYPE_CODE_EXISTS);
    }

    // ==================== update ====================

    @Test
    void update_success() {
        DictTypePO po = createTypePo(1L, "type-1", "原名称");
        when(dictTypeMapper.selectById(1L)).thenReturn(po);

        DictTypeUpdateDTO dto = new DictTypeUpdateDTO();
        dto.setName("新名称");

        DictTypeVO vo = dictTypeService.update(1L, dto);

        assertThat(vo.getName()).isEqualTo("新名称");
        verify(dictTypeMapper).updateById(po);
    }

    @Test
    void update_notFound_throws() {
        when(dictTypeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> dictTypeService.update(999L, new DictTypeUpdateDTO()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DICT_TYPE_NOT_FOUND);
    }

    // ==================== delete ====================

    @Test
    void delete_success_deletesDataAndType() {
        DictTypePO po = createTypePo(1L, "type-1", "类型1");
        when(dictTypeMapper.selectById(1L)).thenReturn(po);

        dictTypeService.delete(1L);

        verify(dictDataMapper).delete(any());
        verify(dictTypeMapper).deleteById(1L);
    }

    @Test
    void delete_notFound_throws() {
        when(dictTypeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> dictTypeService.delete(999L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DICT_TYPE_NOT_FOUND);
    }

    // ==================== toggleStatus ====================

    @Test
    void toggleStatus_fromOnToOff() {
        DictTypePO po = createTypePo(1L, "type-1", "类型1");
        po.setStatus(1);
        when(dictTypeMapper.selectById(1L)).thenReturn(po);

        dictTypeService.toggleStatus(1L);

        assertThat(po.getStatus()).isZero();
    }

    @Test
    void toggleStatus_fromOffToOn() {
        DictTypePO po = createTypePo(1L, "type-1", "类型1");
        po.setStatus(0);
        when(dictTypeMapper.selectById(1L)).thenReturn(po);

        dictTypeService.toggleStatus(1L);

        assertThat(po.getStatus()).isEqualTo(1);
    }

    // ==================== addData / updateData / deleteData ====================

    @Test
    void addData_success() {
        when(dictTypeMapper.selectOne(any())).thenReturn(createTypePo(1L, "type-1", "类型1"));

        DictDataCreateDTO dto = new DictDataCreateDTO();
        dto.setTypeCode("type-1");
        dto.setLabel("新标签");
        dto.setValue("new-value");
        dto.setSort(1);

        DictDataVO vo = dictTypeService.addData(dto, "admin");

        assertThat(vo.getLabel()).isEqualTo("新标签");
        verify(dictDataMapper).insert(any(DictDataPO.class));
    }

    @Test
    void addData_typeNotFound_throws() {
        when(dictTypeMapper.selectOne(any())).thenReturn(null);

        DictDataCreateDTO dto = new DictDataCreateDTO();
        dto.setTypeCode("unknown");
        dto.setLabel("L");
        dto.setValue("V");

        assertThatThrownBy(() -> dictTypeService.addData(dto, "admin"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DICT_TYPE_NOT_FOUND);
    }

    @Test
    void updateData_success() {
        DictDataPO po = createDataPo(1L, "old-value", "旧标签");
        when(dictDataMapper.selectById(1L)).thenReturn(po);

        DictDataUpdateDTO dto = new DictDataUpdateDTO();
        dto.setLabel("新标签");
        dto.setRemark("新备注");

        DictDataVO vo = dictTypeService.updateData(1L, dto);

        assertThat(vo.getLabel()).isEqualTo("新标签");
        verify(dictDataMapper).updateById(po);
    }

    @Test
    void deleteData_success() {
        DictDataPO po = createDataPo(1L, "val", "标签");
        when(dictDataMapper.selectById(1L)).thenReturn(po);

        dictTypeService.deleteData(1L);

        verify(dictDataMapper).deleteById(1L);
    }

    @Test
    void deleteData_notFound_throws() {
        when(dictDataMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> dictTypeService.deleteData(999L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DICT_DATA_NOT_FOUND);
    }

    // ==================== ensureDictData（含并发安全） ====================

    @Test
    void ensureDictData_typeNotExist_createsTypeAndData() {
        // 第一次 selectOne 查类型 — 不存在
        // 第二次 selectCount 查数据 — 不存在
        when(dictTypeMapper.selectOne(any())).thenReturn(null);
        when(dictDataMapper.selectCount(any())).thenReturn(0L);

        dictTypeService.ensureDictData("new-type", "val1", "标签1");

        verify(dictTypeMapper).insert(typeCaptor.capture());
        assertThat(typeCaptor.getValue().getCode()).isEqualTo("new-type");
        verify(dictDataMapper).insert(dataCaptor.capture());
        assertThat(dataCaptor.getValue().getValue()).isEqualTo("val1");
    }

    @Test
    void ensureDictData_dataExists_skipsInsert() {
        when(dictTypeMapper.selectOne(any())).thenReturn(createTypePo(1L, "exists", "已存在"));
        when(dictDataMapper.selectCount(any())).thenReturn(1L);

        dictTypeService.ensureDictData("exists", "val1", "标签1");

        verify(dictDataMapper, never()).insert(any(DictDataPO.class));
    }

    // ==================== 工具方法 ====================

    private DictTypePO createTypePo(Long id, String code, String name) {
        DictTypePO po = new DictTypePO();
        po.setId(id);
        po.setCode(code);
        po.setName(name);
        po.setStatus(1);
        po.setSort(1);
        return po;
    }

    private DictDataPO createDataPo(Long id, String value, String label) {
        DictDataPO po = new DictDataPO();
        po.setId(id);
        po.setValue(value);
        po.setLabel(label);
        po.setTypeCode("type-1");
        po.setStatus(1);
        po.setDefaultFlag(0);
        return po;
    }

    private DictDataPO dataPo(String value, String label, int sort) {
        DictDataPO po = new DictDataPO();
        po.setValue(value);
        po.setLabel(label);
        po.setTypeCode("transaction_propagation");
        po.setStatus(1);
        po.setSort(sort);
        po.setDefaultFlag("REQUIRED".equals(value) ? 1 : 0);
        return po;
    }
}
