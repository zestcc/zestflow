package com.zestflow.admin.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutorRegistryVO {

    private Long id;
    private String executorId;
    private String appCode;
    private String appName;
    private String executorHost;
    private Integer executorPort;
    private Integer status;
    private LocalDateTime lastHeartbeat;
    /** 当前实例 @ZestChain 声明的 chain_key 列表 */
    private java.util.List<String> declaredChainKeys;
    private String updatedBy;
    private LocalDateTime createdAt;
}
