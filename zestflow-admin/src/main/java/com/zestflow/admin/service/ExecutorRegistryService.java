package com.zestflow.admin.service;

import com.zestflow.admin.model.vo.ExecutorRegistryVO;

import java.util.List;
import java.util.Map;

public interface ExecutorRegistryService {

    List<ExecutorRegistryVO> listAll();

    ExecutorRegistryVO getByExecutorId(String executorId);

    void updateStatus(String executorId, Integer status);

    List<Map<String, String>> listDistinctApps();

    /** 查询有在线执行器的 appCode 列表 */
    List<Map<String, String>> listDistinctOnlineApps();

    /** 合并应用下在线执行器声明的 chain_key */
    java.util.Set<String> listDeclaredChainKeysByApp(String appCode);

    /** 应用是否仍声明指定 chain_key */
    boolean isChainKeyDeclared(String appCode, String chainKey);
}
