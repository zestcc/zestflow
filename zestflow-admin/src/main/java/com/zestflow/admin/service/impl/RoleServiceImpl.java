package com.zestflow.admin.service.impl;

import com.zestflow.admin.model.entity.RolePO;
import com.zestflow.admin.model.vo.RoleVO;
import com.zestflow.admin.repository.RoleMapper;
import com.zestflow.admin.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;

    @Override
    public List<RoleVO> listAll() {
        return roleMapper.selectList(null).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    private RoleVO toVO(RolePO po) {
        return RoleVO.builder()
                .id(po.getId())
                .code(po.getCode())
                .name(po.getName())
                .description(po.getDescription())
                .build();
    }
}
