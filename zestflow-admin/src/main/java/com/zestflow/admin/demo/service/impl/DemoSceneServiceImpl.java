package com.zestflow.admin.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.demo.model.dto.DemoSceneCreateDTO;
import com.zestflow.admin.demo.model.dto.DemoSceneUpdateDTO;
import com.zestflow.admin.demo.model.entity.DemoScenePO;
import com.zestflow.admin.demo.model.vo.DemoSceneVO;
import com.zestflow.admin.demo.repository.DemoSceneMapper;
import com.zestflow.admin.demo.service.DemoSceneService;
import com.zestflow.admin.service.TenantAppContext;
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
@ConditionalOnProperty(prefix = "zestflow.demo", name = "enabled", havingValue = "true", matchIfMissing = false)
@Service
@RequiredArgsConstructor
public class DemoSceneServiceImpl implements DemoSceneService {

    private final DemoSceneMapper sceneMapper;
    private final TenantAppContext tenantAppContext;

    @Value("${zestflow.demo.app-code:demo-app}")
    private String defaultAppCode;

    @Override
    public IPage<DemoSceneVO> queryPage(String keyword, String appCode, int page, int size) {
        LambdaQueryWrapper<DemoScenePO> wrapper = new LambdaQueryWrapper<DemoScenePO>()
                .eq(StringUtils.hasText(appCode), DemoScenePO::getAppCode, appCode)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(DemoScenePO::getName, keyword)
                        .or().like(DemoScenePO::getSceneCode, keyword)
                        .or().like(DemoScenePO::getDescription, keyword))
                .orderByDesc(DemoScenePO::getCreatedAt);

        Page<DemoScenePO> poPage = sceneMapper.selectPage(new Page<>(page, size), wrapper);
        return poPage.convert(this::toVO);
    }

    @Override
    public List<DemoSceneVO> listAll(String appCode) {
        return sceneMapper.selectList(
                new LambdaQueryWrapper<DemoScenePO>()
                        .eq(StringUtils.hasText(appCode), DemoScenePO::getAppCode, appCode)
                        .orderByDesc(DemoScenePO::getCreatedAt))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public DemoSceneVO getById(Long id) {
        DemoScenePO po = sceneMapper.selectById(id);
        return po != null ? toVO(po) : null;
    }

    @Override
    public DemoSceneVO getByCode(String sceneCode) {
        DemoScenePO po = sceneMapper.selectOne(
                new LambdaQueryWrapper<DemoScenePO>()
                        .eq(DemoScenePO::getSceneCode, sceneCode));
        return po != null ? toVO(po) : null;
    }

    @Override
    public DemoSceneVO create(DemoSceneCreateDTO dto) {
        DemoScenePO po = new DemoScenePO();
        po.setSceneCode(CodeGenerator.generate("SCN"));
        po.setName(dto.getName());
        po.setDescription(dto.getDescription());
        po.setRequestPath(dto.getRequestPath());
        po.setRequestMethod(StringUtils.hasText(dto.getRequestMethod()) ? dto.getRequestMethod().toUpperCase() : "POST");
        po.setRequestHeaders(dto.getRequestHeaders());
        po.setBodyType(StringUtils.hasText(dto.getBodyType()) ? dto.getBodyType() : "JSON");
        po.setRequestBody(dto.getRequestBody());
        po.setResponseExample(dto.getResponseExample());
        po.setChainCode(dto.getChainCode());
        po.setRateLimit(dto.getRateLimit() != null ? dto.getRateLimit() : 30);
        po.setTenantId(tenantAppContext.getCurrentTenantId());
        po.setAppCode(defaultAppCode);
        sceneMapper.insert(po);
        return toVO(po);
    }

    @Override
    public DemoSceneVO update(Long id, DemoSceneUpdateDTO dto) {
        DemoScenePO po = sceneMapper.selectById(id);
        if (po == null) return null;

        if (dto.getName() != null) po.setName(dto.getName());
        if (dto.getDescription() != null) po.setDescription(dto.getDescription());
        if (dto.getRequestPath() != null) po.setRequestPath(dto.getRequestPath());
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

    private DemoSceneVO toVO(DemoScenePO po) {
        if (po == null) return null;
        DemoSceneVO vo = new DemoSceneVO();
        vo.setId(po.getId());
        vo.setSceneCode(po.getSceneCode());
        vo.setName(po.getName());
        vo.setDescription(po.getDescription());
        vo.setRequestPath(po.getRequestPath());
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
