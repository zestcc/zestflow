package com.zestflow.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.DesignCreateDTO;
import com.zestflow.admin.model.dto.DesignUpdateDTO;
import com.zestflow.admin.model.vo.ChainVO;
import com.zestflow.admin.model.vo.DesignVO;

import java.util.List;

public interface DesignService {

    IPage<DesignVO> listByModuleId(Long moduleId, String keyword, Integer status, Integer page, Integer size);

    DesignVO getById(Long id);

    DesignVO create(DesignCreateDTO dto);

    DesignVO update(Long id, DesignUpdateDTO dto);

    void saveGraph(Long id, String graphData);

    void delete(Long id);

    void toggleStatus(Long id);

    List<ChainVO> getBindings(Long designId);

    List<ChainVO> getBindable(Long designId);

    void bind(Long designId, Long chainId);

    void unbind(Long designId, Long chainId);
}
