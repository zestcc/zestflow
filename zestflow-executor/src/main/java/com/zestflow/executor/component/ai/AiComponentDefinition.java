package com.zestflow.executor.component.ai;

import com.zestflow.common.model.ComponentType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 组件生成请求/响应模型。
 * <p>
 * 用于在 Admin 端通过 AI 服务生成组件代码，
 * 描述业务需求后返回可注册的组件元数据和代码骨架。
 */
@Data
@Builder
public class AiComponentDefinition {

    /** 组件唯一标识 */
    private String componentId;

    /** 组件名称 */
    private String componentName;

    /** 组件类型 */
    private ComponentType componentType;

    /** 组件分组 */
    private String groupName;

    /** 自然语言描述 */
    private String description;

    /** 输入参数 Schema */
    private List<ParamSchema> inputParams;

    /** 输出参数 Schema */
    private List<ParamSchema> outputParams;

    /** 生成的代码骨架 */
    private String codeSkeleton;

    /** 完整 Java 类代码 */
    private String fullJavaCode;

    /** 依赖信息 */
    private List<String> dependencies;

    /** 元数据扩展 */
    private Map<String, Object> metadata;

    @Data
    @Builder
    public static class ParamSchema {
        private String name;
        private String type;
        private boolean required;
        private String description;
    }
}