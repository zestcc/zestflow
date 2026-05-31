package com.zestflow.admin.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.demo.model.dto.DemoRecordQueryDTO;
import com.zestflow.admin.demo.model.entity.DemoRecordPO;
import com.zestflow.admin.demo.model.vo.DemoRecordVO;
import com.zestflow.admin.demo.repository.DemoRecordMapper;
import com.zestflow.admin.demo.service.DemoRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 演示记录服务实现
 */
@ConditionalOnProperty(prefix = "zestflow.demo", name = "enabled", havingValue = "true", matchIfMissing = false)
@Service
@RequiredArgsConstructor
public class DemoRecordServiceImpl implements DemoRecordService {

    private final DemoRecordMapper recordMapper;

    @Override
    public IPage<DemoRecordVO> queryPage(DemoRecordQueryDTO dto) {
        LambdaQueryWrapper<DemoRecordPO> wrapper = new LambdaQueryWrapper<DemoRecordPO>()
                .eq(dto.getSceneId() != null, DemoRecordPO::getSceneId, dto.getSceneId())
                .eq(StringUtils.hasText(dto.getSceneCode()), DemoRecordPO::getSceneCode, dto.getSceneCode())
                .eq(StringUtils.hasText(dto.getChainCode()), DemoRecordPO::getChainCode, dto.getChainCode())
                .eq(dto.getStatus() != null, DemoRecordPO::getStatus, dto.getStatus())
                .eq(StringUtils.hasText(dto.getAppCode()), DemoRecordPO::getAppCode, dto.getAppCode())
                .and(StringUtils.hasText(dto.getKeyword()), w -> w
                        .like(DemoRecordPO::getSceneName, dto.getKeyword())
                        .or().like(DemoRecordPO::getSceneCode, dto.getKeyword()))
                .apply(StringUtils.hasText(dto.getStartTime()),
                        "created_at >= {0}", dto.getStartTime())
                .apply(StringUtils.hasText(dto.getEndTime()),
                        "created_at <= {0}", dto.getEndTime())
                .orderByDesc(DemoRecordPO::getCreatedAt);

        Page<DemoRecordPO> poPage = recordMapper.selectPage(
                new Page<>(dto.getPage(), dto.getSize()), wrapper);
        return poPage.convert(this::toVO);
    }

    @Override
    public DemoRecordVO getById(Long id) {
        DemoRecordPO po = recordMapper.selectById(id);
        return po != null ? toVO(po) : null;
    }

    @Override
    public DemoRecordPO saveRecord(DemoRecordPO po) {
        recordMapper.insert(po);
        return po;
    }

    private DemoRecordVO toVO(DemoRecordPO po) {
        if (po == null) return null;
        DemoRecordVO vo = new DemoRecordVO();
        vo.setId(po.getId());
        vo.setSceneId(po.getSceneId());
        vo.setSceneName(po.getSceneName());
        vo.setSceneCode(po.getSceneCode());
        vo.setRequestMethod(po.getRequestMethod());
        vo.setRequestPath(po.getRequestPath());
        vo.setRequestHeaders(po.getRequestHeaders());
        vo.setBodyType(po.getBodyType());
        vo.setRequestBody(po.getRequestBody());
        vo.setResponseStatus(po.getResponseStatus());
        vo.setResponseBody(po.getResponseBody());
        vo.setResponseHeaders(po.getResponseHeaders());
        vo.setChainCode(po.getChainCode());
        vo.setInstanceId(po.getInstanceId());
        vo.setStatus(po.getStatus());
        vo.setCostMs(po.getCostMs());
        vo.setErrorMsg(po.getErrorMsg());
        vo.setCreatedBy(po.getCreatedBy());
        vo.setUpdatedBy(po.getUpdatedBy());
        vo.setCreatedAt(po.getCreatedAt());
        vo.setUpdatedAt(po.getUpdatedAt());
        return vo;
    }
}
