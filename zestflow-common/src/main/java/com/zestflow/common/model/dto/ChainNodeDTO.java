package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 链定义中的节点 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainNodeDTO {

    /** 节点唯一标识 */
    private String id;

    /** 节点显示名称 */
    private String label;

    /** 节点类型：NORMAL / CONDITION / SCRIPT / SUB_CHAIN / ITERATOR */
    private String type;

    /** 映射的 @ZestComponent 名称（NORMAL/CONDITION 类型必填） */
    private String component;

    /** 脚本内容（SCRIPT 类型必填，格式 "groovy:..." 或 "js:..."） */
    private String script;

    /** 子链编码（SUB_CHAIN 类型必填） */
    private String subChainCode;

    /** 节点运行时配置 */
    private Map<String, Object> config;
}
