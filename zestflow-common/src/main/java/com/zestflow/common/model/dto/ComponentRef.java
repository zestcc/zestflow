package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 元件引用，描述节点绑定的其他元件（参数绑定器/校验器/前置后置处理器等）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentRef implements Serializable {

    /** 元件 ID（@ZestComponent ID） */
    private String componentId;

    /** 元件名称 */
    private String componentName;
}
