package com.zestflow.admin.service;

import com.zestflow.admin.model.dto.ModuleCreateDTO;
import com.zestflow.admin.model.dto.ModuleUpdateDTO;
import com.zestflow.admin.model.vo.ModuleVO;

import java.util.List;

public interface ModuleService {

    List<ModuleVO> listAll();

    ModuleVO getById(Long id);

    ModuleVO create(ModuleCreateDTO dto);

    ModuleVO update(Long id, ModuleUpdateDTO dto);

    void delete(Long id);
}
