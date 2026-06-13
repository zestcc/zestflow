package com.zestflow.admin.service.sso.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.service.UserService;
import com.zestflow.admin.service.sso.oidc.AbstractOidcSsoProvider;
import com.zestflow.admin.service.sso.oidc.OidcEndpointResolver;
import com.zestflow.admin.service.sso.oidc.OidcJwtValidator;
import com.zestflow.admin.service.sso.oidc.OidcTokenClient;
import com.zestflow.admin.service.sso.oidc.PkceUtils;
import com.zestflow.admin.service.sso.store.SsoPkceStore;
import com.zestflow.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * ZestSSO 提供方 — 默认 Discovery + logout-url API。
 */
@Slf4j
@Component
public class ZestSsoProvider extends AbstractOidcSsoProvider {

    public static final String PROVIDER_ID = "zest-sso";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public ZestSsoProvider(SsoPkceStore pkceStore,
                           OidcEndpointResolver endpointResolver,
                           OidcTokenClient tokenClient,
                           OidcJwtValidator jwtValidator,
                           UserService userService,
                           RestClient.Builder restClientBuilder,
                           ObjectMapper objectMapper) {
        super(pkceStore, endpointResolver, tokenClient, jwtValidator, userService);
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    protected String displayName() {
        return "ZestSSO";
    }

    @Override
    public String buildLogoutUrl(SsoProperties props) {
        SsoProperties.ZestSsoProperties zestCfg = props.getZestSso();
        if (zestCfg != null && zestCfg.isUseLogoutUrlApi()) {
            return fetchLogoutUrlFromApi(props, zestCfg.getLogoutUrlApiPath());
        }
        return super.buildLogoutUrl(props);
    }

    private String fetchLogoutUrlFromApi(SsoProperties props, String apiPath) {
        String base = props.getIssuer().replaceAll("/$", "");
        String path = StringUtils.hasText(apiPath) ? apiPath : "/api/public/logout-url";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String redirect = props.getPostLogoutRedirectUri();
        try {
            String body = restClientBuilder.build()
                    .get()
                    .uri(base + path + "?redirect_uri=" + PkceUtils.urlEncode(redirect))
                    .retrieve()
                    .body(String.class);
            return parseLogoutUrlResponse(body, objectMapper);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("ZestSSO logout-url 调用异常", ex);
            throw new BizException(ErrorCode.VALIDATION_ERROR, "SSO 登出 URL 获取失败");
        }
    }

    /** 解析 ZestSSO ApiResponse&lt;String&gt; 登出 URL */
    static String parseLogoutUrlResponse(String body, ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "SSO 登出 URL 获取失败");
            }
            String url = root.path("data").asText(null);
            if (!StringUtils.hasText(url)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "SSO 登出 URL 获取失败");
            }
            return url;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "SSO 登出 URL 获取失败");
        }
    }
}
