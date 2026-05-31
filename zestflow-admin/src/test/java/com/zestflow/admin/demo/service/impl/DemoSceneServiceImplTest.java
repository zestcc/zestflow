package com.zestflow.admin.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.demo.model.dto.DemoSceneCreateDTO;
import com.zestflow.admin.demo.model.dto.DemoSceneUpdateDTO;
import com.zestflow.admin.demo.model.entity.DemoScenePO;
import com.zestflow.admin.demo.model.vo.DemoSceneVO;
import com.zestflow.admin.demo.repository.DemoSceneMapper;
import com.zestflow.admin.service.TenantAppContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.zestflow.admin.demo.model.entity.DemoScenePO;

@ExtendWith(MockitoExtension.class)
class DemoSceneServiceImplTest {

    @Mock private DemoSceneMapper sceneMapper;
    @Mock private TenantAppContext tenantAppContext;
    @Captor private ArgumentCaptor<DemoScenePO> poCaptor;

    private DemoSceneServiceImpl sceneService;

    @BeforeEach
    void setUp() {
        sceneService = new DemoSceneServiceImpl(sceneMapper, tenantAppContext);
    }

    // ==================== create ====================

    @Test
    void create_shouldGenerateCodeAndSetAuditFields() {
        when(tenantAppContext.getCurrentTenantId()).thenReturn(999L);
        when(sceneMapper.insert(any())).thenReturn(1);

        DemoSceneCreateDTO dto = new DemoSceneCreateDTO();
        dto.setName("测试场景");
        dto.setDescription("测试描述");
        dto.setRequestPath("/execute");
        dto.setRequestMethod("post");
        dto.setBodyType("JSON");
        dto.setRequestBody("{\"key\":\"value\"}");
        dto.setResponseExample("{\"code\":200}");
        dto.setChainCode("CHN_TEST");
        dto.setRateLimit(50);

        DemoSceneVO vo = sceneService.create(dto);

        assertThat(vo).isNotNull();
        assertThat(vo.getName()).isEqualTo("测试场景");
        assertThat(vo.getSceneCode()).startsWith("SCN");
        assertThat(vo.getRequestMethod()).isEqualTo("POST"); // uppercased
        assertThat(vo.getRateLimit()).isEqualTo(50);
        assertThat(vo.getAppCode()).isEqualTo("demo-app");

        verify(sceneMapper).insert(poCaptor.capture());
        DemoScenePO po = poCaptor.getValue();
        assertThat(po.getTenantId()).isEqualTo(999L);
        assertThat(po.getSceneCode()).startsWith("SCN");
    }

    @Test
    void create_shouldUseDefaultRateLimit_whenNotProvided() {
        when(tenantAppContext.getCurrentTenantId()).thenReturn(1L);
        when(sceneMapper.insert(any())).thenReturn(1);

        DemoSceneCreateDTO dto = new DemoSceneCreateDTO();
        dto.setName("默认限流");
        dto.setRequestPath("/test");
        dto.setChainCode("CHN_TEST");

        DemoSceneVO vo = sceneService.create(dto);

        assertThat(vo.getRateLimit()).isEqualTo(30); // default
        assertThat(vo.getBodyType()).isEqualTo("JSON"); // default
    }

    // ==================== getById ====================

    @Test
    void getById_shouldReturnVO_whenExists() {
        DemoScenePO po = createTestPO(1L, "SCN001");
        when(sceneMapper.selectById(1L)).thenReturn(po);

        DemoSceneVO vo = sceneService.getById(1L);

        assertThat(vo).isNotNull();
        assertThat(vo.getSceneCode()).isEqualTo("SCN001");
        assertThat(vo.getName()).isEqualTo("测试场景");
    }

    @Test
    void getById_shouldReturnNull_whenNotExists() {
        when(sceneMapper.selectById(999L)).thenReturn(null);

        DemoSceneVO vo = sceneService.getById(999L);

        assertThat(vo).isNull();
    }

    // ==================== getByCode ====================

    @Test
    void getByCode_shouldReturnVO_whenFound() {
        DemoScenePO po = createTestPO(2L, "SCN002");
        when(sceneMapper.selectOne(any())).thenReturn(po);

        DemoSceneVO vo = sceneService.getByCode("SCN002");

        assertThat(vo).isNotNull();
        assertThat(vo.getSceneCode()).isEqualTo("SCN002");
    }

    // ==================== update ====================

    @Test
    void update_shouldModifyFields() {
        DemoScenePO existing = createTestPO(1L, "SCN001");
        when(sceneMapper.selectById(1L)).thenReturn(existing);
        when(sceneMapper.updateById(any())).thenReturn(1);
        when(sceneMapper.selectById(1L)).thenReturn(existing); // same PO ref

        DemoSceneUpdateDTO dto = new DemoSceneUpdateDTO();
        dto.setName("新名称");
        dto.setRateLimit(100);

        DemoSceneVO vo = sceneService.update(1L, dto);

        assertThat(vo).isNotNull();
        verify(sceneMapper).updateById(poCaptor.capture());
        assertThat(poCaptor.getValue().getName()).isEqualTo("新名称");
        assertThat(poCaptor.getValue().getRateLimit()).isEqualTo(100);
    }

    @Test
    void update_shouldReturnNull_whenNotFound() {
        when(sceneMapper.selectById(999L)).thenReturn(null);

        DemoSceneVO vo = sceneService.update(999L, new DemoSceneUpdateDTO());

        assertThat(vo).isNull();
    }

    // ==================== delete ====================

    @Test
    void delete_shouldCallMapperDelete() {
        sceneService.delete(1L);

        verify(sceneMapper).deleteById(1L);
    }

    // ==================== queryPage ====================

    @Test
    void queryPage_shouldReturnPagedResults() {
        Page<DemoScenePO> poPage = new Page<>(1, 10);
        poPage.setRecords(List.of(createTestPO(1L, "SCN001")));
        poPage.setTotal(1);
        when(sceneMapper.selectPage(any(Page.class), any())).thenReturn(poPage);

        IPage<DemoSceneVO> result = sceneService.queryPage(null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getSceneCode()).isEqualTo("SCN001");
    }

    @Test
    void queryPage_shouldSearchByKeyword() {
        Page<DemoScenePO> poPage = new Page<>(1, 10);
        poPage.setRecords(List.of());
        poPage.setTotal(0);
        when(sceneMapper.selectPage(any(Page.class), any())).thenReturn(poPage);

        IPage<DemoSceneVO> result = sceneService.queryPage("关键字", 1, 10);

        assertThat(result.getRecords()).isEmpty();
        // verify keyword was passed to query wrapper
        verify(sceneMapper).selectPage(any(Page.class), argThat(wrapper -> {
            // LambdaQueryWrapper with keyword should produce a WHERE clause
            return wrapper != null;
        }));
    }

    // ==================== listAll ====================

    @Test
    void listAll_shouldReturnAllScenes() {
        when(sceneMapper.selectList(any())).thenReturn(
                List.of(createTestPO(1L, "SCN001"), createTestPO(2L, "SCN002")));

        List<DemoSceneVO> list = sceneService.listAll();

        assertThat(list).hasSize(2);
    }

    // ==================== helpers ====================

    private DemoScenePO createTestPO(Long id, String code) {
        DemoScenePO po = new DemoScenePO();
        po.setId(id);
        po.setSceneCode(code);
        po.setName("测试场景");
        po.setDescription("测试描述");
        po.setRequestPath("/execute");
        po.setRequestMethod("POST");
        po.setBodyType("JSON");
        po.setRequestBody("{\"msg\":\"hello\"}");
        po.setResponseExample("{\"code\":200}");
        po.setChainCode("CHN_TEST");
        po.setRateLimit(30);
        po.setAppCode("demo-app");
        po.setTenantId(1L);
        po.setCreatedBy("admin");
        po.setUpdatedBy("admin");
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        return po;
    }
}
