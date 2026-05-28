package com.zestflow.admin.service;

import com.zestflow.admin.model.vo.ExecutorRegistryVO;

import java.util.List;

public interface ExecutorRegistryService {

    List<ExecutorRegistryVO> listByModuleId(Long moduleId);

    void updateStatus(Long id, Integer status);
}
