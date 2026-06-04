package com.zestflow.admin.playground.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.playground.model.dto.PlaygroundSceneCreateDTO;
import com.zestflow.admin.playground.model.dto.PlaygroundSceneUpdateDTO;
import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;
import com.zestflow.admin.playground.model.vo.PlaygroundSceneVO;
import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;
import com.zestflow.admin.playground.service.PlaygroundSceneService;
import com.zestflow.admin.playground.support.PlaygroundAccessControl;
import com.zestflow.admin.playground.support.PlaygroundRequestPathValidator;
import com.zestflow.admin.playground.support.PlaygroundUrlResolver;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.admin.util.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.zestflow.common.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 演示场景服务实现
 */
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@Service
@RequiredArgsConstructor
public class PlaygroundSceneServiceImpl implements PlaygroundSceneService {

    private final PlaygroundSceneMapper sceneMapper;
    private final TenantAppContext tenantAppContext;
    private final PlaygroundAccessControl accessControl;
    private final PlaygroundUrlResolver playgroundUrlResolver;

    /** 默认应用编码，从配置 zestflow.playground.app-code 注入 */
    @Value("${zestflow.playground.app-code:playground-app}")
    private String defaultAppCode;

    @Override
    public String getDefaultAppCode() {
        return defaultAppCode;
    }

    @Override
    public IPage<PlaygroundSceneVO> queryPage(String keyword, String appCode, int page, int size) {
        LambdaQueryWrapper<PlaygroundScenePO> wrapper = new LambdaQueryWrapper<PlaygroundScenePO>()
                .eq(StringUtils.hasText(appCode), PlaygroundScenePO::getAppCode, appCode)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(PlaygroundScenePO::getName, keyword)
                        .or().like(PlaygroundScenePO::getSceneCode, keyword)
                        .or().like(PlaygroundScenePO::getDescription, keyword))
                .orderByDesc(PlaygroundScenePO::getCreatedAt);

        applyAccessibleAppFilter(wrapper);

        Page<PlaygroundScenePO> poPage = sceneMapper.selectPage(new Page<>(page, size), wrapper);
        return poPage.convert(this::toVO);
    }

    @Override
    public List<PlaygroundSceneVO> listAll(String appCode) {
        LambdaQueryWrapper<PlaygroundScenePO> wrapper = new LambdaQueryWrapper<PlaygroundScenePO>()
                .eq(StringUtils.hasText(appCode), PlaygroundScenePO::getAppCode, appCode)
                .orderByDesc(PlaygroundScenePO::getCreatedAt);
        applyAccessibleAppFilter(wrapper);
        return sceneMapper.selectList(wrapper)
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public PlaygroundSceneVO getById(Long id) {
        PlaygroundScenePO po = sceneMapper.selectById(id);
        return po != null ? toVO(po) : null;
    }

    @Override
    public PlaygroundSceneVO getByCode(String sceneCode) {
        PlaygroundScenePO po = sceneMapper.selectOne(
                new LambdaQueryWrapper<PlaygroundScenePO>()
                        .eq(PlaygroundScenePO::getTenantId, tenantAppContext.getCurrentTenantId())
                        .eq(PlaygroundScenePO::getSceneCode, sceneCode));
        return po != null ? toVO(po) : null;
    }

    @Override
    public PlaygroundSceneVO create(PlaygroundSceneCreateDTO dto) {
        String appCode = StringUtils.hasText(dto.getAppCode()) ? dto.getAppCode() : defaultAppCode;
        String storagePath = playgroundUrlResolver.normalizeForStorage(appCode, dto.getRequestPath());
        PlaygroundRequestPathValidator.validate(storagePath, playgroundUrlResolver.allowedBaseUrls(appCode));
        PlaygroundScenePO po = new PlaygroundScenePO();
        po.setSceneCode(CodeGenerator.generate("SCN"));
        po.setName(dto.getName());
        po.setDescription(dto.getDescription());
        po.setRequestPath(storagePath);
        po.setRequestMethod(StringUtils.hasText(dto.getRequestMethod()) ? dto.getRequestMethod().toUpperCase() : "POST");
        po.setRequestHeaders(dto.getRequestHeaders());
        po.setBodyType(StringUtils.hasText(dto.getBodyType()) ? dto.getBodyType() : "JSON");
        po.setRequestBody(dto.getRequestBody());
        po.setResponseExample(dto.getResponseExample());
        po.setChainCode(dto.getChainCode());
        po.setRateLimit(dto.getRateLimit() != null ? dto.getRateLimit() : 30);
        po.setTenantId(tenantAppContext.getCurrentTenantId());
        po.setAppCode(StringUtils.hasText(dto.getAppCode()) ? dto.getAppCode() : defaultAppCode);
        sceneMapper.insert(po);
        return toVO(po);
    }

    @Override
    public PlaygroundSceneVO update(Long id, PlaygroundSceneUpdateDTO dto) {
        PlaygroundScenePO po = sceneMapper.selectById(id);
        if (po == null) return null;

        if (dto.getName() != null) po.setName(dto.getName());
        if (dto.getDescription() != null) po.setDescription(dto.getDescription());
        if (dto.getRequestPath() != null) {
            String storagePath = playgroundUrlResolver.normalizeForStorage(po.getAppCode(), dto.getRequestPath());
            PlaygroundRequestPathValidator.validate(storagePath, playgroundUrlResolver.allowedBaseUrls(po.getAppCode()));
            po.setRequestPath(storagePath);
        }
        if (dto.getRequestMethod() != null) po.setRequestMethod(dto.getRequestMethod().toUpperCase());
        if (dto.getRequestHeaders() != null) po.setRequestHeaders(dto.getRequestHeaders());
        if (dto.getBodyType() != null) po.setBodyType(dto.getBodyType());
        if (dto.getRequestBody() != null) po.setRequestBody(dto.getRequestBody());
        if (dto.getResponseExample() != null) po.setResponseExample(dto.getResponseExample());
        if (dto.getChainCode() != null) po.setChainCode(dto.getChainCode());
        if (dto.getRateLimit() != null) po.setRateLimit(dto.getRateLimit());
        sceneMapper.updateById(po);
        return toVO(sceneMapper.selectById(id));
    }

    @Override
    public void delete(Long id) {
        sceneMapper.deleteById(id);
    }

    /** 非超管仅能看到已授权 appCode 下的场景；无应用权限则不可见任何场景 */
    private void applyAccessibleAppFilter(LambdaQueryWrapper<PlaygroundScenePO> wrapper) {
        if (accessControl.isIpDemoTenantSession()) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && SecurityUtils.isSuperAdmin(auth)) {
            return;
        }
        java.util.Set<String> codes = tenantAppContext.getCurrentUserAppCodes();
        if (codes == null || codes.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(PlaygroundScenePO::getAppCode, codes);
    }

    private PlaygroundSceneVO toVO(PlaygroundScenePO po) {
        if (po == null) return null;
        PlaygroundSceneVO vo = new PlaygroundSceneVO();
        vo.setId(po.getId());
        vo.setSceneCode(po.getSceneCode());
        vo.setName(po.getName());
        vo.setDescription(po.getDescription());
        vo.setRequestPath(playgroundUrlResolver.toDisplayUrl(po.getAppCode(), po.getRequestPath()));
        vo.setRequestMethod(po.getRequestMethod());
        vo.setRequestHeaders(po.getRequestHeaders());
        vo.setBodyType(po.getBodyType());
        vo.setRequestBody(po.getRequestBody());
        vo.setResponseExample(po.getResponseExample());
        vo.setChainCode(po.getChainCode());
        vo.setRateLimit(po.getRateLimit());
        vo.setAppCode(po.getAppCode());
        vo.setCreatedBy(po.getCreatedBy());
        vo.setUpdatedBy(po.getUpdatedBy());
        vo.setCreatedAt(po.getCreatedAt());
        vo.setUpdatedAt(po.getUpdatedAt());
        return vo;
    }
}
