package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 执行元件 DTO，Executor 注册时透传给 Admin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 元件 ID（@ZestExecute value 或 类名.方法名） */
    private String componentId;

    /** 元件显示名称 */
    private String componentName;

    /** 元件描述 */
    private String description;

    /** 所属分组（@ZestComponent value） */
    private String groupName;

    /** 超时时间(ms)，-1 使用默认 */
    private long timeout;

    /** 是否异步 */
    private boolean async;

    /** 元件类型：EXECUTOR / PREDICATE / SELECTOR / LOADER / PARSER */
    private String componentType;
}
