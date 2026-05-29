package com.zestflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.ChainCreateDTO;
import com.zestflow.admin.model.dto.ChainUpdateDTO;
import com.zestflow.admin.model.entity.ChainPO;
import com.zestflow.admin.model.vo.ChainVO;
import com.zestflow.admin.repository.ChainMapper;
import com.zestflow.admin.service.ChainService;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChainServiceImpl implements ChainService {

    private final ChainMapper chainMapper;

    @Override
    public IPage<ChainVO> listByModuleId(Long moduleId, String keyword, Integer status, Integer page, Integer size) {
        LambdaQueryWrapper<ChainPO> wrapper = new LambdaQueryWrapper<ChainPO>()
                .eq(ChainPO::getModuleId, moduleId);

        if (status != null) {
            wrapper.eq(ChainPO::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ChainPO::getCode, keyword)
                    .or().like(ChainPO::getName, keyword));
        }
        wrapper.orderByDesc(ChainPO::getCreatedAt);

        IPage<ChainPO> poPage = chainMapper.selectPage(new Page<>(page, size), wrapper);

        List<ChainVO> voList = poPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        Page<ChainVO> voPage = new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public ChainVO getById(Long id) {
        ChainPO po = chainMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.CHAIN_NOT_FOUND);
        }
        return toVO(po);
    }

    @Override
    public ChainVO create(ChainCreateDTO dto) {
        Long count = chainMapper.selectCount(
                new LambdaQueryWrapper<ChainPO>().eq(ChainPO::getCode, dto.getCode())
        );
        if (count > 0) {
            throw new BizException(ErrorCode.CHAIN_CODE_EXISTS);
        }

        ChainPO po = new ChainPO();
        po.setCode(dto.getCode());
        po.setName(dto.getName());
        po.setModuleId(dto.getModuleId());
        po.setDescription(dto.getDescription());
        po.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        chainMapper.insert(po);

        log.info("链创建成功 chainId={} code={} name={} moduleId={}", po.getId(), dto.getCode(), dto.getName(), dto.getModuleId());
        return toVO(po);
    }

    @Override
    public ChainVO update(Long id, ChainUpdateDTO dto) {
        ChainPO po = chainMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.CHAIN_NOT_FOUND);
        }

        if (dto.getName() != null) po.setName(dto.getName());
        if (dto.getDescription() != null) po.setDescription(dto.getDescription());
        if (dto.getStatus() != null) po.setStatus(dto.getStatus());
        po.setUpdatedAt(LocalDateTime.now());
        chainMapper.updateById(po);

        log.info("链更新成功 chainId={}", id);
        return toVO(po);
    }

    @Override
    public void delete(Long id) {
        ChainPO po = chainMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.CHAIN_NOT_FOUND);
        }
        chainMapper.deleteById(id);
        log.info("链删除成功 chainId={} code={}", id, po.getCode());
    }

    @Override
    public void toggleStatus(Long id) {
        ChainPO po = chainMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.CHAIN_NOT_FOUND);
        }
        int newStatus = po.getStatus() == 1 ? 0 : 1;
        po.setStatus(newStatus);
        po.setUpdatedAt(LocalDateTime.now());
        chainMapper.updateById(po);
        log.info("链状态切换 chainId={} newStatus={}", id, newStatus);
    }

    private ChainVO toVO(ChainPO po) {
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
