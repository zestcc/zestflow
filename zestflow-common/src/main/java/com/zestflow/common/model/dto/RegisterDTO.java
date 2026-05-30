package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行器唯一标识 */
    private String executorId;

    /** 应用名（分组标识） */
    private String appName;

    /** 主机地址 */
    private String host;

    /** 端口 */
    private int port;

    /** 模块编码 */
    private String moduleCode;

    /** 模块名称（为空则默认等于 moduleCode） */
    private String moduleName;

    /** 扩展元数据 JSON */
    private String metadata;

    /** 该执行器提供的所有 @ZestExecute 元件清单 */
    private java.util.List<ComponentDTO> components;
}
