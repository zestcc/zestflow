package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.ai.model.dto.AiChainTemplateSaveDTO;
import com.zestflow.admin.ai.model.entity.AiChainTemplatePO;
import com.zestflow.admin.ai.model.vo.AiChainTemplateVO;
import com.zestflow.admin.ai.repository.AiChainTemplateMapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiChainTemplateService {

    private final AiChainTemplateMapper templateMapper;
    private final TenantAiConfigService tenantAiConfigService;

    public List<AiChainTemplateVO> list(String appCode) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        LambdaQueryWrapper<AiChainTemplatePO> q = new LambdaQueryWrapper<AiChainTemplatePO>()
                .eq(AiChainTemplatePO::getTenantId, tenantId)
                .eq(AiChainTemplatePO::getIsDeleted, 0)
                .orderByDesc(AiChainTemplatePO::getUpdatedAt);
        if (StringUtils.hasText(appCode)) {
            q.eq(AiChainTemplatePO::getAppCode, appCode);
        }
        return templateMapper.selectList(q).stream().map(this::toVo).toList();
    }

    public AiChainTemplateVO get(Long id) {
        AiChainTemplatePO po = requireOwned(id);
        return toVo(po);
    }

    public AiChainTemplateVO save(AiChainTemplateSaveDTO dto) {
        if (!StringUtils.hasText(dto.getName()) || !StringUtils.hasText(dto.getChainData())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        AiChainTemplatePO po = new AiChainTemplatePO();
        po.setTenantId(tenantAiConfigService.getCurrentTenantId());
        po.setName(dto.getName().trim());
        po.setDescription(dto.getDescription());
        po.setAppCode(dto.getAppCode());
        po.setPromptSummary(dto.getPromptSummary());
        po.setChainData(dto.getChainData());
        po.setCreatedBy(SecurityUtils.getCurrentUsername());
        po.setUpdatedBy(po.getCreatedBy());
        po.setIsDeleted(0);
        templateMapper.insert(po);
        return toVo(po);
    }

    public void delete(Long id) {
        AiChainTemplatePO po = requireOwned(id);
        po.setIsDeleted(1);
        templateMapper.updateById(po);
    }

    private AiChainTemplatePO requireOwned(Long id) {
        AiChainTemplatePO po = templateMapper.selectById(id);
        if (po == null || po.getIsDeleted() != null && po.getIsDeleted() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!tenantAiConfigService.getCurrentTenantId().equals(po.getTenantId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
        return po;
    }

    private AiChainTemplateVO toVo(AiChainTemplatePO po) {
        return AiChainTemplateVO.builder()
                .id(po.getId())
                .name(po.getName())
                .description(po.getDescription())
                .appCode(po.getAppCode())
                .promptSummary(po.getPromptSummary())
                .chainData(po.getChainData())
                .createdBy(po.getCreatedBy())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
