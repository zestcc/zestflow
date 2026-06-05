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

    /** 主机地址 */
    private String host;

    /** 端口 */
    private int port;

    /** 应用编码（分组标识） */
    private String appCode;

    /** 应用名称（为空则默认等于 appCode） */
    @Builder.Default
    private String appName = "";

    /** 扩展元数据 JSON */
    private String metadata;


    /** 该执行器提供的所有 @ZestExecute 元件清单 */
    private java.util.List<ComponentDTO> components;

    /** 应用声明的 chain_key 列表（@ZestChain 扫描） */
    private java.util.List<String> declaredChainKeys;
}
