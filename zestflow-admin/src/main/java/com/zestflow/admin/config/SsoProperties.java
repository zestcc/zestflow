package com.zestflow.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ZestFlow SSO 集成配置。
 * <p>
 * provider 可选：zest-sso（ZestSSO）、oidc（通用 OIDC）、none（关闭）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zestflow.sso")
public class SsoProperties {

    private boolean enabled = false;

    /** 提供方标识：zest-sso | oidc | none */
    private String provider = "zest-sso";

    /** 前端按钮展示名（oidc 提供方可覆盖） */
    private String displayName;

    private String issuer = "http://localhost:9000";

    /** OIDC Discovery 地址；ZestSSO 默认 /api/public/.well-known/openid-configuration */
    private String discoveryUri = "http://localhost:9000/api/public/.well-known/openid-configuration";

    private String clientId = "zestflow-admin";
    private String clientSecret = "change-me-in-production";
    private String redirectUri = "http://localhost:5173/login/callback";
    private String jwksUri = "http://localhost:9000/oauth2/jwks";
    private List<String> scopes = List.of("openid", "profile", "email", "roles", "tenant");
    private String frontendCallbackUri = "http://localhost:5173/login/callback";
    private String postLogoutRedirectUri = "http://localhost:5173/login";

    private SsoClaimsProperties claims = new SsoClaimsProperties();
    private ZestSsoProperties zestSso = new ZestSsoProperties();

    @Data
    public static class SsoClaimsProperties {
        private String usernameClaim = "preferred_username";
        private String emailClaim = "email";
        private String rolesClaim = "roles";
        private String tenantClaim = "tenant_id";
        private String adminRole = "SSO_ADMIN";
    }

    @Data
    public static class ZestSsoProperties {
        /** 是否调用 ZestSSO /api/public/logout-url 获取登出地址 */
        private boolean useLogoutUrlApi = true;
        private String logoutUrlApiPath = "/api/public/logout-url";
    }
}
