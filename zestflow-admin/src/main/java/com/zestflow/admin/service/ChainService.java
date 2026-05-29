package com.zestflow.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.ChainCreateDTO;
import com.zestflow.admin.model.dto.ChainUpdateDTO;
import com.zestflow.admin.model.vo.ChainVO;

public interface ChainService {

    IPage<ChainVO> listByModuleId(Long moduleId, String keyword, Integer status, Integer page, Integer size);

    ChainVO getById(Long id);

    ChainVO create(ChainCreateDTO dto);

    ChainVO update(Long id, ChainUpdateDTO dto);

    void delete(Long id);

    void toggleStatus(Long id);
}
