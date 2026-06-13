package com.zestflow.admin.service;

import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.PeerExecutorDTO;
import com.zestflow.common.model.dto.RegisterDTO;

import java.util.List;

public interface RegistryService {

    /** 执行器注册 */
    void register(RegisterDTO dto, Long tenantId);

    /** 执行器心跳 */
    void heartbeat(HeartbeatDTO dto);

    /** 执行器注销 */
    void deregister(String executorId);

    /** 手动变更执行器状态 */
    void updateStatus(String executorId, Integer status);

    /** 查询同应用在线 Executor 对等节点（调度路由 / Failover） */
    List<PeerExecutorDTO> listOnlinePeers(String appCode);
}
