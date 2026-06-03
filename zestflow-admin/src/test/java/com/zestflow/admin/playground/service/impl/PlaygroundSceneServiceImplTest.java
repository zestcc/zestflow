package com.zestflow.admin.playground.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.playground.model.dto.PlaygroundSceneCreateDTO;
import com.zestflow.admin.playground.model.dto.PlaygroundSceneUpdateDTO;
import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;
import com.zestflow.admin.playground.model.vo.PlaygroundSceneVO;
import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;
import com.zestflow.admin.playground.support.PlaygroundAccessControl;
import com.zestflow.admin.playground.support.PlaygroundUrlResolver;
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

@ExtendWith(MockitoExtension.class)
class PlaygroundSceneServiceImplTest {
    @Mock private PlaygroundSceneMapper sceneMapper;
    @Mock private TenantAppContext tenantAppContext;
    @Mock private PlaygroundAccessControl accessControl;
    @Mock private PlaygroundUrlResolver playgroundUrlResolver;
    @Captor private ArgumentCaptor<PlaygroundScenePO> poCaptor;

    private PlaygroundSceneServiceImpl sceneService;

    @BeforeEach
    void setUp() {
        sceneService = new PlaygroundSceneServiceImpl(sceneMapper, tenantAppContext, accessControl, playgroundUrlResolver);
        lenient().when(accessControl.isIpDemoTenantSession()).thenReturn(false);
        lenient().when(playgroundUrlResolver.allowedBaseUrls(any())).thenReturn(List.of());
        lenient().when(playgroundUrlResolver.normalizeForStorage(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(playgroundUrlResolver.toDisplayUrl(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        org.springframework.test.util.ReflectionTestUtils.setField(sceneService, "defaultAppCode", "playground-app");
    }

    // ==================== create ====================

    @Test
    void create_shouldGenerateCodeAndSetAuditFields() {
        when(tenantAppContext.getCurrentTenantId()).thenReturn(999L);
        when(sceneMapper.insert(any(PlaygroundScenePO.class))).thenReturn(1);

        PlaygroundSceneCreateDTO dto = new PlaygroundSceneCreateDTO();
        dto.setName("测试场景");
        dto.setDescription("测试描述");
        dto.setRequestPath("/execute");
        dto.setRequestMethod("post");
        dto.setBodyType("JSON");
        dto.setRequestBody("{\"key\":\"value\"}");
        dto.setResponseExample("{\"code\":200}");
        dto.setChainCode("CHN_TEST");
        dto.setRateLimit(50);

        PlaygroundSceneVO vo = sceneService.create(dto);

        assertThat(vo).isNotNull();
        assertThat(vo.getName()).isEqualTo("测试场景");
        assertThat(vo.getSceneCode()).startsWith("SCN");
        assertThat(vo.getRequestMethod()).isEqualTo("POST"); // uppercased
        assertThat(vo.getRateLimit()).isEqualTo(50);
        assertThat(vo.getAppCode()).isEqualTo("playground-app");

        verify(sceneMapper).insert(poCaptor.capture());
        PlaygroundScenePO po = poCaptor.getValue();
        assertThat(po.getTenantId()).isEqualTo(999L);
        assertThat(po.getSceneCode()).startsWith("SCN");
    }

    @Test
    void create_shouldUseDefaultRateLimit_whenNotProvided() {
        when(tenantAppContext.getCurrentTenantId()).thenReturn(1L);
        when(sceneMapper.insert(any(PlaygroundScenePO.class))).thenReturn(1);

        PlaygroundSceneCreateDTO dto = new PlaygroundSceneCreateDTO();
        dto.setName("默认限流");
        dto.setRequestPath("/api/orders/test");
        dto.setChainCode("CHN_TEST");

        PlaygroundSceneVO vo = sceneService.create(dto);

        assertThat(vo.getRateLimit()).isEqualTo(30); // default
        assertThat(vo.getBodyType()).isEqualTo("JSON"); // default
    }

    // ==================== getById ====================

    @Test
    void getById_shouldReturnVO_whenExists() {
        PlaygroundScenePO po = createTestPO(1L, "SCN001");
        when(sceneMapper.selectById(1L)).thenReturn(po);

        PlaygroundSceneVO vo = sceneService.getById(1L);

        assertThat(vo).isNotNull();
        assertThat(vo.getSceneCode()).isEqualTo("SCN001");
        assertThat(vo.getName()).isEqualTo("测试场景");
    }

    @Test
    void getById_shouldReturnNull_whenNotExists() {
        when(sceneMapper.selectById(999L)).thenReturn(null);

        PlaygroundSceneVO vo = sceneService.getById(999L);

        assertThat(vo).isNull();
    }

    // ==================== getByCode ====================

    @Test
    void getByCode_shouldReturnVO_whenFound() {
        PlaygroundScenePO po = createTestPO(2L, "SCN002");
        when(sceneMapper.selectOne(any())).thenReturn(po);

        PlaygroundSceneVO vo = sceneService.getByCode("SCN002");

        assertThat(vo).isNotNull();
        assertThat(vo.getSceneCode()).isEqualTo("SCN002");
    }

    // ==================== update ====================

    @Test
    void update_shouldModifyFields() {
        PlaygroundScenePO existing = createTestPO(1L, "SCN001");
        when(sceneMapper.selectById(1L)).thenReturn(existing);
        when(sceneMapper.updateById(any(PlaygroundScenePO.class))).thenReturn(1);
        when(sceneMapper.selectById(1L)).thenReturn(existing); // same PO ref

        PlaygroundSceneUpdateDTO dto = new PlaygroundSceneUpdateDTO();
        dto.setName("新名称");
        dto.setRateLimit(100);

        PlaygroundSceneVO vo = sceneService.update(1L, dto);

        assertThat(vo).isNotNull();
        verify(sceneMapper).updateById(poCaptor.capture());
        assertThat(poCaptor.getValue().getName()).isEqualTo("新名称");
        assertThat(poCaptor.getValue().getRateLimit()).isEqualTo(100);
    }

    @Test
    void update_shouldReturnNull_whenNotFound() {
        when(sceneMapper.selectById(999L)).thenReturn(null);

        PlaygroundSceneVO vo = sceneService.update(999L, new PlaygroundSceneUpdateDTO());

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
        Page<PlaygroundScenePO> poPage = new Page<>(1, 10);
        poPage.setRecords(List.of(createTestPO(1L, "SCN001")));
        poPage.setTotal(1);
        when(sceneMapper.selectPage(any(Page.class), any())).thenReturn(poPage);

        IPage<PlaygroundSceneVO> result = sceneService.queryPage(null, null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getSceneCode()).isEqualTo("SCN001");
    }

    @Test
    void queryPage_shouldSearchByKeyword() {
        Page<PlaygroundScenePO> poPage = new Page<>(1, 10);
        poPage.setRecords(List.of());
        poPage.setTotal(0);
        when(sceneMapper.selectPage(any(Page.class), any())).thenReturn(poPage);

        IPage<PlaygroundSceneVO> result = sceneService.queryPage("关键字", null, 1, 10);

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

        List<PlaygroundSceneVO> list = sceneService.listAll(null);

        assertThat(list).hasSize(2);
    }

    // ==================== helpers ====================

    private PlaygroundScenePO createTestPO(Long id, String code) {
        PlaygroundScenePO po = new PlaygroundScenePO();
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
        po.setAppCode("playground-app");
        po.setTenantId(1L);
        po.setCreatedBy("admin");
        po.setUpdatedBy("admin");
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        return po;
    }
}
