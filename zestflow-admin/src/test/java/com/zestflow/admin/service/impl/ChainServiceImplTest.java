package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.ChainCreateDTO;
import com.zestflow.admin.model.dto.ChainUpdateDTO;
import com.zestflow.admin.model.entity.ChainPO;
import com.zestflow.admin.model.vo.ChainVO;
import com.zestflow.admin.repository.ChainMapper;
import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChainServiceImplTest {

    @Mock private ChainMapper chainMapper;

    private ChainServiceImpl chainService;

    /** 类型明确的包装方法，绕开 BaseMapper insert/updateById 重载歧义 */
    private ChainPO anyChainPO() { return any(); }

    @BeforeEach
    void setUp() {
        chainService = new ChainServiceImpl(chainMapper);
    }

    @Test
    void createChain() {
        ChainCreateDTO dto = new ChainCreateDTO();
        dto.setName("测试链");
        dto.setModuleId(10L);

        ChainVO vo = chainService.create(dto);

        assertThat(vo.getCode()).startsWith("CHN");
        assertThat(vo.getName()).isEqualTo("测试链");
        verify(chainMapper).insert(anyChainPO());
    }

    @Test
    void getById() {
        ChainPO po = new ChainPO();
        po.setId(1L);
        po.setCode("chain-test");
        po.setName("测试链");
        when(chainMapper.selectById(1L)).thenReturn(po);

        ChainVO vo = chainService.getById(1L);

        assertThat(vo.getCode()).isEqualTo("chain-test");
    }

    @Test
    void getById_notFound() {
        when(chainMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> chainService.getById(999L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAIN_NOT_FOUND);
    }

    @Test
    void updateChain() {
        ChainPO po = new ChainPO();
        po.setId(1L);
        po.setName("旧名称");
        when(chainMapper.selectById(1L)).thenReturn(po);

        ChainUpdateDTO dto = new ChainUpdateDTO();
        dto.setName("新名称");

        ChainVO vo = chainService.update(1L, dto);

        assertThat(vo.getName()).isEqualTo("新名称");
        verify(chainMapper).updateById(argThat((ChainPO p) -> p.getName().equals("新名称")));
    }

    @Test
    void deleteChain() {
        ChainPO po = new ChainPO();
        po.setId(1L);
        po.setCode("chain-test");
        when(chainMapper.selectById(1L)).thenReturn(po);

        chainService.delete(1L);

        verify(chainMapper).deleteById(1L);
    }

    @Test
    void toggleStatus() {
        ChainPO po = new ChainPO();
        po.setId(1L);
        po.setStatus(0);
        when(chainMapper.selectById(1L)).thenReturn(po);

        chainService.toggleStatus(1L);

        assertThat(po.getStatus()).isEqualTo(1); // 0 → 1
        verify(chainMapper).updateById(po);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listByModuleId() {
        when(chainMapper.selectPage(any(), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    IPage<ChainPO> page = mock(IPage.class);
                    when(page.getRecords()).thenReturn(java.util.List.of());
                    when(page.getCurrent()).thenReturn(1L);
                    when(page.getSize()).thenReturn(10L);
                    when(page.getTotal()).thenReturn(0L);
                    return page;
                });

        IPage<ChainVO> result = chainService.listByModuleId(10L, null, null, 1, 10);

        assertThat(result).isNotNull();
    }
}
