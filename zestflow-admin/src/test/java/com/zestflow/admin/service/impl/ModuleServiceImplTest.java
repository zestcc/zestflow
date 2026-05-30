package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.ModuleCreateDTO;
import com.zestflow.admin.model.dto.ModuleUpdateDTO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.entity.ModulePO;
import com.zestflow.admin.model.vo.ModuleVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ModuleMapper;
import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleServiceImplTest {

    @Mock private ModuleMapper moduleMapper;
    @Mock private ExecutorRegistryMapper executorRegistryMapper;
    @Captor private ArgumentCaptor<ModulePO> moduleCaptor;

    private ModuleServiceImpl moduleService;

    @BeforeEach
    void setUp() {
        moduleService = new ModuleServiceImpl(moduleMapper, executorRegistryMapper);
    }

    @Test
    void listAll() {
        ModulePO po1 = buildModulePO(1L, "mod-a", "模块A", 1, 1);
        ModulePO po2 = buildModulePO(2L, "mod-b", "模块B", 1, 2);
        when(moduleMapper.selectList(any())).thenReturn(List.of(po1, po2));

        ExecutorRegistryPO exec = new ExecutorRegistryPO();
        exec.setModuleId(1L);
        exec.setStatus(1);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(exec));

        List<ModuleVO> list = moduleService.listAll();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getCode()).isEqualTo("mod-a");
        assertThat(list.get(0).getExecutorTotal()).isEqualTo(1);
        assertThat(list.get(0).getExecutorHealthy()).isEqualTo(1);
    }

    @Test
    void listAll_noExecutors() {
        ModulePO po = buildModulePO(1L, "mod-a", "模块A", 1, 1);
        when(moduleMapper.selectList(any())).thenReturn(List.of(po));
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of());

        List<ModuleVO> list = moduleService.listAll();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getExecutorTotal()).isEqualTo(0);
    }

    @Test
    void getById() {
        ModulePO po = buildModulePO(1L, "mod-a", "模块A", 1, 1);
        when(moduleMapper.selectById(1L)).thenReturn(po);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of());

        ModuleVO vo = moduleService.getById(1L);

        assertThat(vo.getCode()).isEqualTo("mod-a");
        assertThat(vo.getName()).isEqualTo("模块A");
    }

    @Test
    void getById_notFound() {
        when(moduleMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> moduleService.getById(999L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MODULE_NOT_FOUND);
    }

    @Test
    void create() {
        when(moduleMapper.selectCount(any())).thenReturn(0L);

        ModuleCreateDTO dto = new ModuleCreateDTO();
        dto.setCode("new-mod");
        dto.setName("新模块");
        dto.setDescription("描述");

        ModuleVO vo = moduleService.create(dto);

        verify(moduleMapper).insert(moduleCaptor.capture());
        ModulePO inserted = moduleCaptor.getValue();
        assertThat(inserted.getCode()).isEqualTo("new-mod");
        assertThat(inserted.getName()).isEqualTo("新模块");
        assertThat(inserted.getStatus()).isEqualTo(1);
    }

    @Test
    void create_duplicateCode() {
        when(moduleMapper.selectCount(any())).thenReturn(1L);

        ModuleCreateDTO dto = new ModuleCreateDTO();
        dto.setCode("dup-mod");

        assertThatThrownBy(() -> moduleService.create(dto))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MODULE_CODE_EXISTS);
    }

    @Test
    void create_withSortOrder() {
        when(moduleMapper.selectCount(any())).thenReturn(0L);

        ModuleCreateDTO dto = new ModuleCreateDTO();
        dto.setCode("mod");
        dto.setName("模块");
        dto.setSortOrder(5);

        moduleService.create(dto);

        verify(moduleMapper).insert(moduleCaptor.capture());
        assertThat(moduleCaptor.getValue().getSortOrder()).isEqualTo(5);
    }

    @Test
    void update() {
        ModulePO po = buildModulePO(1L, "mod-a", "模块A", 1, 1);
        when(moduleMapper.selectById(1L)).thenReturn(po);

        ModuleUpdateDTO dto = new ModuleUpdateDTO();
        dto.setName("新名称");

        moduleService.update(1L, dto);

        verify(moduleMapper).updateById(moduleCaptor.capture());
        assertThat(moduleCaptor.getValue().getName()).isEqualTo("新名称");
    }

    @Test
    void update_notFound() {
        when(moduleMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> moduleService.update(999L, new ModuleUpdateDTO()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MODULE_NOT_FOUND);
    }

    @Test
    void update_disableCascadesExecutors() {
        ModulePO po = buildModulePO(1L, "mod-a", "模块A", 1, 1);
        when(moduleMapper.selectById(1L)).thenReturn(po);

        ExecutorRegistryPO exec = new ExecutorRegistryPO();
        exec.setId(10L);
        exec.setStatus(1);
        when(executorRegistryMapper.selectList(any())).thenReturn(List.of(exec));

        ModuleUpdateDTO dto = new ModuleUpdateDTO();
        dto.setStatus(0);

        moduleService.update(1L, dto);

        verify(executorRegistryMapper).update(any(), any());
    }

    @Test
    void delete() {
        ModulePO po = buildModulePO(1L, "mod-a", "模块A", 1, 1);
        when(moduleMapper.selectById(1L)).thenReturn(po);

        moduleService.delete(1L);

        verify(moduleMapper).deleteById(1L);
    }

    @Test
    void delete_notFound() {
        when(moduleMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> moduleService.delete(999L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MODULE_NOT_FOUND);
    }

    private ModulePO buildModulePO(Long id, String code, String name, Integer status, Integer sortOrder) {
        ModulePO po = new ModulePO();
        po.setId(id);
        po.setCode(code);
        po.setName(name);
        po.setStatus(status);
        po.setSortOrder(sortOrder);
        return po;
    }
}
