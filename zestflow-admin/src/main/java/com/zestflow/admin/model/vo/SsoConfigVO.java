package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SsoConfigVO {

    private boolean enabled;
    /** 提供方标识：zest-sso | oidc | none */
    private String provider;
    /** 前端按钮展示名 */
    private String displayName;
    private String issuer;
    private String clientId;
    private String authorizationUrl;
}
