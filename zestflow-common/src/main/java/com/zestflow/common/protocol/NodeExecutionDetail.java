package com.zestflow.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 单次链执行中某个节点的详情（入参/出参/错误），按需加载。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExecutionDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private String executionId;
    /** 图节点 componentId 或结构节点 shape */
    private String nodeId;
    private String nodeName;
    /** flow-start / flow-end / flow-task 等，前端传入 */
    private String nodeShape;

    /** 节点入参（开始节点 = 链入参） */
    private String params;
    /** 节点出参（结束节点 = 链最终结果） */
    private String result;
    private String errorMessage;
    private Long costMs;
    /** 1=成功 0=失败 -1=未执行/未知 */
    private Integer status;

    /** 该节点相关事件时间线（轻量，无 payload） */
    private List<com.zestflow.common.model.dto.ChainEvent> timeline;
}
