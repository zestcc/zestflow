package com.zestflow.admin.service;

import com.zestflow.admin.model.vo.ExecutorRegistryVO;

import java.util.List;
import java.util.Map;

public interface ExecutorRegistryService {

    List<ExecutorRegistryVO> listAll();

    ExecutorRegistryVO getByExecutorId(String executorId);

    void updateStatus(String executorId, Integer status);

    List<Map<String, String>> listDistinctApps();
}
