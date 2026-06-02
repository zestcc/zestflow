package com.zestflow.admin.playground.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.playground.model.dto.PlaygroundRecordQueryDTO;
import com.zestflow.admin.playground.model.entity.PlaygroundRecordPO;
import com.zestflow.admin.playground.model.vo.PlaygroundRecordVO;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.playground.repository.PlaygroundRecordMapper;
import com.zestflow.admin.playground.service.PlaygroundRecordService;
import com.zestflow.admin.playground.support.PlaygroundAccessControl;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 演示记录服务实现
 */
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@Service
@RequiredArgsConstructor
public class PlaygroundRecordServiceImpl implements PlaygroundRecordService {

    private final PlaygroundRecordMapper recordMapper;
    private final PlaygroundAccessControl accessControl;
    private final TenantAppContext tenantAppContext;

    @Override
    public IPage<PlaygroundRecordVO> queryPage(PlaygroundRecordQueryDTO dto) {
        LambdaQueryWrapper<PlaygroundRecordPO> wrapper = new LambdaQueryWrapper<PlaygroundRecordPO>()
                .eq(dto.getSceneId() != null, PlaygroundRecordPO::getSceneId, dto.getSceneId())
                .eq(StringUtils.hasText(dto.getSceneCode()), PlaygroundRecordPO::getSceneCode, dto.getSceneCode())
                .eq(StringUtils.hasText(dto.getChainCode()), PlaygroundRecordPO::getChainCode, dto.getChainCode())
                .eq(dto.getStatus() != null, PlaygroundRecordPO::getStatus, dto.getStatus())
                .eq(StringUtils.hasText(dto.getAppCode()), PlaygroundRecordPO::getAppCode, dto.getAppCode())
                .and(StringUtils.hasText(dto.getKeyword()), w -> w
                        .like(PlaygroundRecordPO::getSceneName, dto.getKeyword())
                        .or().like(PlaygroundRecordPO::getSceneCode, dto.getKeyword()))
                .apply(StringUtils.hasText(dto.getStartTime()),
                        "created_at >= {0}", dto.getStartTime())
                .apply(StringUtils.hasText(dto.getEndTime()),
                        "created_at <= {0}", dto.getEndTime())
                .orderByDesc(PlaygroundRecordPO::getCreatedAt);

        applyRecordScope(wrapper);

        Page<PlaygroundRecordPO> poPage = recordMapper.selectPage(
                new Page<>(dto.getPage(), dto.getSize()), wrapper);
        return poPage.convert(this::toVO);
    }

    @Override
    public PlaygroundRecordVO getById(Long id) {
        PlaygroundRecordPO po = recordMapper.selectById(id);
        if (po == null) {
            return null;
        }
        assertCanAccessRecord(po);
        return toVO(po);
    }

    /** 非超管仅能查看本人创建的记录；并限制在已授权 appCode 内 */
    private void applyRecordScope(LambdaQueryWrapper<PlaygroundRecordPO> wrapper) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean superAdmin = auth != null && auth.isAuthenticated() && SecurityUtils.isSuperAdmin(auth);
        if (!superAdmin) {
            wrapper.eq(PlaygroundRecordPO::getCreatedBy, accessControl.currentUsername());
            Set<String> codes = tenantAppContext.getCurrentUserAppCodes();
            if (codes == null || codes.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(PlaygroundRecordPO::getAppCode, codes);
            }
        }
    }

    private void assertCanAccessRecord(PlaygroundRecordPO po) {
        if (accessControl.isSuperAdmin()) {
            return;
        }
        if (!accessControl.currentUsername().equals(po.getCreatedBy())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
        Set<String> codes = tenantAppContext.getCurrentUserAppCodes();
        if (codes != null && !codes.isEmpty() && po.getAppCode() != null && !codes.contains(po.getAppCode())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
    }

    @Override
    public PlaygroundRecordPO saveRecord(PlaygroundRecordPO po) {
        recordMapper.insert(po);
        return po;
    }

    private PlaygroundRecordVO toVO(PlaygroundRecordPO po) {
        if (po == null) return null;
        PlaygroundRecordVO vo = new PlaygroundRecordVO();
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
