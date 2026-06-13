package com.zestflow.admin.service.sso.provider;

import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.service.UserService;
import com.zestflow.admin.service.sso.oidc.AbstractOidcSsoProvider;
import com.zestflow.admin.service.sso.oidc.OidcEndpointResolver;
import com.zestflow.admin.service.sso.oidc.OidcJwtValidator;
import com.zestflow.admin.service.sso.oidc.OidcTokenClient;
import com.zestflow.admin.service.sso.store.SsoPkceStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 通用 OIDC 提供方 — 适用于 Keycloak、Authing 等标准 IdP。
 */
@Component
public class GenericOidcSsoProvider extends AbstractOidcSsoProvider {

    public static final String PROVIDER_ID = "oidc";

    private final SsoProperties ssoProperties;

    public GenericOidcSsoProvider(SsoPkceStore pkceStore,
                                  OidcEndpointResolver endpointResolver,
                                  OidcTokenClient tokenClient,
                                  OidcJwtValidator jwtValidator,
                                  UserService userService,
                                  SsoProperties ssoProperties) {
        super(pkceStore, endpointResolver, tokenClient, jwtValidator, userService);
        this.ssoProperties = ssoProperties;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    protected String displayName() {
        return StringUtils.hasText(ssoProperties.getDisplayName()) ? ssoProperties.getDisplayName() : "SSO";
    }
}
