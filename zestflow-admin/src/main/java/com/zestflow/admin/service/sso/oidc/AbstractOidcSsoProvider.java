package com.zestflow.admin.service.sso.oidc;

import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.SsoCallbackDTO;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.SsoAuthorizeVO;
import com.zestflow.admin.model.vo.SsoConfigVO;
import com.zestflow.admin.service.UserService;
import com.zestflow.admin.service.sso.spi.SsoProvider;
import com.zestflow.admin.service.sso.store.SsoPkceStore;
import com.zestflow.common.exception.BizException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * OIDC + PKCE 通用流程基类。
 */
@RequiredArgsConstructor
public abstract class AbstractOidcSsoProvider implements SsoProvider {

    protected final SsoPkceStore pkceStore;
    protected final OidcEndpointResolver endpointResolver;
    protected final OidcTokenClient tokenClient;
    protected final OidcJwtValidator jwtValidator;
    protected final UserService userService;

    protected abstract String displayName();

    @Override
    public SsoConfigVO buildPublicConfig(SsoProperties props) {
        return SsoConfigVO.builder()
                .enabled(props.isEnabled())
                .provider(providerId())
                .displayName(displayName())
                .issuer(props.getIssuer())
                .clientId(props.getClientId())
                .build();
    }

    @Override
    public SsoAuthorizeVO buildAuthorizeUrl(SsoProperties props) {
        ensureEnabled(props);
        OidcEndpoints endpoints = endpointResolver.resolve(props);
        String state = PkceUtils.randomBase64Url(16);
        String codeVerifier = PkceUtils.randomBase64Url(32);
        String codeChallenge = PkceUtils.sha256Base64Url(codeVerifier);
        pkceStore.save(state, codeVerifier);

        String scopes = String.join(" ", props.getScopes());
        String url = endpoints.authorizationEndpoint()
                + "?response_type=code"
                + "&client_id=" + PkceUtils.urlEncode(props.getClientId())
                + "&redirect_uri=" + PkceUtils.urlEncode(props.getRedirectUri())
                + "&scope=" + PkceUtils.urlEncode(scopes)
                + "&state=" + PkceUtils.urlEncode(state)
                + "&code_challenge=" + PkceUtils.urlEncode(codeChallenge)
                + "&code_challenge_method=S256";

        return SsoAuthorizeVO.builder()
                .authorizationUrl(url)
                .state(state)
                .build();
    }

    @Override
    public String buildLogoutUrl(SsoProperties props) {
        OidcEndpoints endpoints = endpointResolver.resolve(props);
        String redirect = props.getPostLogoutRedirectUri();
        if (StringUtils.hasText(endpoints.endSessionEndpoint())) {
            return endpoints.endSessionEndpoint()
                    + "?post_logout_redirect_uri=" + PkceUtils.urlEncode(redirect);
        }
        return props.getIssuer().replaceAll("/$", "")
                + "/connect/logout?post_logout_redirect_uri=" + PkceUtils.urlEncode(redirect);
    }

    @Override
    public LoginVO handleCallback(SsoCallbackDTO dto, SsoProperties props) {
        ensureEnabled(props);
        String codeVerifier = pkceStore.consume(dto.getState());
        if (!StringUtils.hasText(codeVerifier)) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "无效的 state 或已过期");
        }
        OidcEndpoints endpoints = endpointResolver.resolve(props);
        String idToken = tokenClient.exchangeCodeForIdToken(dto.getCode(), codeVerifier, endpoints, props);
        Claims claims = jwtValidator.parseAndValidate(idToken, endpoints, props);
        return userService.loginBySso(providerId(), claims);
    }

    protected void ensureEnabled(SsoProperties props) {
        if (!props.isEnabled()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "SSO 未启用");
        }
    }
}
