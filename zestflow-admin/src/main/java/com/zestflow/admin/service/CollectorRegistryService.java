package com.zestflow.admin.service;

import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;

import java.util.List;

/**
 * 采集器注册服务接口
 */
public interface CollectorRegistryService {

    /** 采集器注册 */
    void register(RegisterDTO dto);

    /** 采集器心跳 */
    void heartbeat(HeartbeatDTO dto);

    /** 采集器注销 */
    void deregister(String collectorId);

    /** 按采集器 ID 查询 */
    CollectorRegistryVO getByCollectorId(String collectorId);

    /** 查询所有在线采集器 */
    List<CollectorRegistryVO> listAllOnline();

    /** 查询所有采集器（全量） */
    List<CollectorRegistryVO> listAll();

    /** 手动更新采集器状态 */
    void updateStatus(Long id, Integer status);
}
