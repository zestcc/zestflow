package com.zestflow.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.SysConfigCreateDTO;
import com.zestflow.admin.model.dto.SysConfigUpdateDTO;
import com.zestflow.admin.model.vo.SysConfigVO;

import java.util.List;
import java.util.Optional;

public interface SysConfigService {

    IPage<SysConfigVO> list(String keyword, String category, Integer status, Integer page, Integer size);

    List<String> listCategories();

    SysConfigVO getById(Long id);

    Optional<String> getValue(String configKey);

    SysConfigVO create(SysConfigCreateDTO dto, String username);

    SysConfigVO update(Long id, SysConfigUpdateDTO dto);

    void delete(Long id);

    void toggleStatus(Long id);
}
