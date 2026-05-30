package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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

    /** 节点类型：EXECUTOR / PREDICATE / SELECTOR / LOADER / PARSER / SCRIPT / SUB_CHAIN / ITERATOR */
    private String type;

    /** 映射的 @ZestComponent 名称 */
    private String component;

    /** 绑定元件名称 */
    private String componentName;

    /** 元件分组 */
    private String groupName;

    /** 描述 */
    private String description;

    /** 脚本内容（SCRIPT 类型必填，格式 "groovy:..." 或 "js:..."） */
    private String script;

    /** 子链编码（SUB_CHAIN 类型必填） */
    private String subChainCode;

    /** 参数解析器链（按顺序匹配，第一个 supports 的生效） */
    private List<ComponentRef> paramResolvers;

    /** 参数校验器 */
    private ComponentRef paramValidator;

    /** 前置处理器列表 */
    private List<ComponentRef> preComponents;

    /** 后置处理器列表 */
    private List<ComponentRef> postComponents;

    /** 节点运行时配置 */
    private Map<String, Object> config;
}
