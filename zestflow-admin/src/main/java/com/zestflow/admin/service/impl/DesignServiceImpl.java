package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.DesignCreateDTO;
import com.zestflow.admin.model.dto.DesignUpdateDTO;
import com.zestflow.admin.model.entity.ChainPO;
import com.zestflow.admin.model.entity.DesignBindingPO;
import com.zestflow.admin.model.entity.DesignPO;
import com.zestflow.admin.model.vo.ChainVO;
import com.zestflow.admin.model.vo.DesignVO;
import com.zestflow.admin.repository.ChainMapper;
import com.zestflow.admin.repository.DesignBindingMapper;
import com.zestflow.admin.repository.DesignMapper;
import com.zestflow.admin.service.DesignService;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zestflow.common.util.CodeGenerator;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DesignServiceImpl implements DesignService {

    private final DesignMapper designMapper;
    private final DesignBindingMapper designBindingMapper;
    private final ChainMapper chainMapper;

    @Override
    public IPage<DesignVO> listByModuleId(Long moduleId, String keyword, Integer status, Integer page, Integer size) {
        LambdaQueryWrapper<DesignPO> wrapper = new LambdaQueryWrapper<DesignPO>()
                .eq(DesignPO::getModuleId, moduleId);

        if (status != null) {
            wrapper.eq(DesignPO::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(DesignPO::getName, keyword);
        }
        wrapper.orderByDesc(DesignPO::getCreatedAt);

        IPage<DesignPO> poPage = designMapper.selectPage(new Page<>(page, size), wrapper);

        // 批量查询绑定链code
        List<Long> designIds = poPage.getRecords().stream().map(DesignPO::getId).collect(Collectors.toList());
        Map<Long, String> chainCodesMap = batchQueryChainCodes(designIds);

        List<DesignVO> voList = poPage.getRecords().stream()
                .map(po -> toVO(po, chainCodesMap.getOrDefault(po.getId(), ""), null))
                .collect(Collectors.toList());

        Page<DesignVO> voPage = new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public DesignVO getById(Long id) {
        DesignPO po = designMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.DESIGN_NOT_FOUND);
        }
        List<ChainVO> bindings = getBindings(id);
        String chainCodes = bindings.stream().map(ChainVO::getCode).collect(Collectors.joining(", "));
        return toVO(po, chainCodes, bindings);
    }

    @Override
    public DesignVO create(DesignCreateDTO dto) {
        DesignPO po = new DesignPO();
        po.setCode(generateDesignCode());
        po.setName(dto.getName());
        po.setModuleId(dto.getModuleId());
        po.setDescription(dto.getDescription());
        po.setDesigner(dto.getDesigner());
        po.setStatus(1);
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        designMapper.insert(po);

        log.info("设计创建成功 designId={} code={} name={} moduleId={}", po.getId(), po.getCode(), dto.getName(), dto.getModuleId());
        return toVO(po, "", null);
    }

    /**
     * 生成设计编码，格式：DSN_YYYYMMDD_XXX（每日顺序递增）
     */
    private String generateDesignCode() {
        return CodeGenerator.generate("DSN");
    }

    @Override
    public DesignVO update(Long id, DesignUpdateDTO dto) {
        DesignPO po = designMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.DESIGN_NOT_FOUND);
        }

        if (dto.getCode() != null && !dto.getCode().equals(po.getCode())) {
            Long exists = designMapper.selectCount(
                    new LambdaQueryWrapper<DesignPO>().eq(DesignPO::getCode, dto.getCode())
            );
            if (exists > 0) {
                throw new BizException(ErrorCode.DESIGN_CODE_EXISTS);
            }
            po.setCode(dto.getCode());
        }
        if (dto.getName() != null) po.setName(dto.getName());
        if (dto.getDescription() != null) po.setDescription(dto.getDescription());
        if (dto.getStatus() != null) po.setStatus(dto.getStatus());
        if (dto.getGraphData() != null) po.setGraphData(dto.getGraphData());
        if (dto.getDesigner() != null) po.setDesigner(dto.getDesigner());
        po.setUpdatedAt(LocalDateTime.now());
        designMapper.updateById(po);

        log.info("设计更新成功 designId={}", id);
        List<ChainVO> bindings = getBindings(id);
        String chainCodes = bindings.stream().map(ChainVO::getCode).collect(Collectors.joining(", "));
        return toVO(po, chainCodes, bindings);
    }

    @Override
    public void saveGraph(Long id, String graphData) {
        DesignPO po = designMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.DESIGN_NOT_FOUND);
        }
        po.setGraphData(graphData);
        po.setUpdatedAt(LocalDateTime.now());
        designMapper.updateById(po);
        log.info("设计图保存成功 designId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DesignPO po = designMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.DESIGN_NOT_FOUND);
        }
        // 级联删除绑定关系
        designBindingMapper.delete(
                new LambdaQueryWrapper<DesignBindingPO>().eq(DesignBindingPO::getDesignId, id)
        );
        designMapper.deleteById(id);
        log.info("设计删除成功 designId={} name={}", id, po.getName());
    }

    @Override
    public void toggleStatus(Long id) {
        DesignPO po = designMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.DESIGN_NOT_FOUND);
        }
        int newStatus = po.getStatus() == 1 ? 0 : 1;
        po.setStatus(newStatus);
        po.setUpdatedAt(LocalDateTime.now());
        designMapper.updateById(po);
        log.info("设计状态切换 designId={} newStatus={}", id, newStatus);
    }

    @Override
    public List<ChainVO> getBindings(Long designId) {
        List<DesignBindingPO> bindings = designBindingMapper.selectList(
                new LambdaQueryWrapper<DesignBindingPO>().eq(DesignBindingPO::getDesignId, designId)
        );
        if (bindings.isEmpty()) {
            return List.of();
        }
        Set<Long> chainIds = bindings.stream().map(DesignBindingPO::getChainId).collect(Collectors.toSet());
        List<ChainPO> chains = chainMapper.selectBatchIds(chainIds);
        return chains.stream()
                .map(this::toChainVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ChainVO> getBindable(Long designId) {
        DesignPO design = designMapper.selectById(designId);
        if (design == null) {
            throw new BizException(ErrorCode.DESIGN_NOT_FOUND);
        }

        List<DesignBindingPO> bindings = designBindingMapper.selectList(
                new LambdaQueryWrapper<DesignBindingPO>().eq(DesignBindingPO::getDesignId, designId)
        );
        Set<Long> boundChainIds = bindings.stream()
                .map(DesignBindingPO::getChainId)
                .collect(Collectors.toSet());

        List<ChainPO> candidates = chainMapper.selectList(
                new LambdaQueryWrapper<ChainPO>()
                        .eq(ChainPO::getModuleId, design.getModuleId())
                        .notIn(!boundChainIds.isEmpty(), ChainPO::getId, boundChainIds)
        );
        return candidates.stream()
                .map(this::toChainVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long designId, Long chainId) {
        DesignPO design = designMapper.selectById(designId);
        if (design == null) {
            throw new BizException(ErrorCode.DESIGN_NOT_FOUND);
        }
        ChainPO chain = chainMapper.selectById(chainId);
        if (chain == null) {
            throw new BizException(ErrorCode.CHAIN_NOT_FOUND);
        }

        Long count = designBindingMapper.selectCount(
                new LambdaQueryWrapper<DesignBindingPO>()
                        .eq(DesignBindingPO::getDesignId, designId)
                        .eq(DesignBindingPO::getChainId, chainId)
        );
        if (count > 0) {
            log.warn("设计绑定关系已存在 designId={} chainId={}", designId, chainId);
            return;
        }

        DesignBindingPO binding = new DesignBindingPO();
        binding.setDesignId(designId);
        binding.setChainId(chainId);
        binding.setCreatedAt(LocalDateTime.now());
        designBindingMapper.insert(binding);

        log.info("设计绑定成功 designId={} chainId={}", designId, chainId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long designId, Long chainId) {
        designBindingMapper.delete(
                new LambdaQueryWrapper<DesignBindingPO>()
                        .eq(DesignBindingPO::getDesignId, designId)
                        .eq(DesignBindingPO::getChainId, chainId)
        );
        log.info("设计解绑成功 designId={} chainId={}", designId, chainId);
    }

    private DesignVO toVO(DesignPO po, String boundChainCodes, List<ChainVO> boundChains) {
        return DesignVO.builder()
                .id(po.getId())
                .code(po.getCode())
                .name(po.getName())
                .moduleId(po.getModuleId())
                .status(po.getStatus())
                .description(po.getDescription())
                .graphData(po.getGraphData())
                .designer(po.getDesigner())
                .chainCount(boundChains != null ? boundChains.size() : 0)
                .boundChainCodes(boundChainCodes)
                .boundChains(boundChains)
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private Map<Long, String> batchQueryChainCodes(List<Long> designIds) {
        if (designIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DesignBindingPO> bindings = designBindingMapper.selectList(
                new LambdaQueryWrapper<DesignBindingPO>().in(DesignBindingPO::getDesignId, designIds)
        );
        if (bindings.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> chainIds = bindings.stream().map(DesignBindingPO::getChainId).collect(Collectors.toSet());
        List<ChainPO> chains = chainMapper.selectBatchIds(chainIds);
        Map<Long, String> chainCodeMap = chains.stream()
                .collect(Collectors.toMap(ChainPO::getId, ChainPO::getCode));
        return bindings.stream()
                .collect(Collectors.groupingBy(
                        DesignBindingPO::getDesignId,
                        Collectors.mapping(b -> chainCodeMap.getOrDefault(b.getChainId(), ""), Collectors.joining(", "))
                ));
    }

    private ChainVO toChainVO(ChainPO po) {
        return ChainVO.builder()
                .id(po.getId())
                .code(po.getCode())
                .name(po.getName())
                .moduleId(po.getModuleId())
                .status(po.getStatus())
                .description(po.getDescription())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
