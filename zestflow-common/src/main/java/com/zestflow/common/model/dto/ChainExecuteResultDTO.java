package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Executor 向 Admin 回传的执行结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainExecuteResultDTO {

    /** 执行实例 ID */
    private String instanceId;

    /** 链编码 */
    private String chainCode;

    /** 执行状态码（对应 ChainConstants 链状态） */
    private Integer status;

    /** 执行耗时（毫秒） */
    private Long costMs;

    /** 执行结果数据 */
    private Map<String, Object> resultData;

    /** 节点执行结果明细 */
    private List<NodeResultDTO> nodeResults;

    /** 错误信息 */
    private String errorMessage;
}
