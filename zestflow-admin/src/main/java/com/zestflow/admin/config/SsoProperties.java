package com.zestflow.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ZestFlow SSO 集成配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zestflow.sso")
public class SsoProperties {

    private boolean enabled = false;
    private String issuer = "http://localhost:9000";
    private String clientId = "zestflow-admin";
    private String clientSecret = "change-me-in-production";
    private String redirectUri = "http://localhost:5173/login/callback";
    private String jwksUri = "http://localhost:9000/oauth2/jwks";
    private List<String> scopes = List.of("openid", "profile", "email", "roles", "tenant");
    private String frontendCallbackUri = "http://localhost:5173/login/callback";
    private String postLogoutRedirectUri = "http://localhost:5173/login";
}
