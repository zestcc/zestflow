package com.zestflow.admin.service.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.SsoCallbackDTO;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.SsoAuthorizeVO;
import com.zestflow.admin.model.vo.SsoConfigVO;
import com.zestflow.admin.service.UserService;
import com.zestflow.common.exception.BizException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SsoAuthService {

    private static final String SSO_PROVIDER = "zest-sso";

    private final SsoProperties ssoProperties;
    private final SsoPkceStore pkceStore;
    private final OidcJwtValidator oidcJwtValidator;
    private final UserService userService;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public SsoConfigVO getConfig() {
        return SsoConfigVO.builder()
                .enabled(ssoProperties.isEnabled())
                .issuer(ssoProperties.getIssuer())
                .clientId(ssoProperties.getClientId())
                .build();
    }

    public SsoAuthorizeVO buildAuthorizeUrl() {
        if (!ssoProperties.isEnabled()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "SSO 未启用");
        }
        String state = randomBase64(16);
        String codeVerifier = randomBase64(32);
        String codeChallenge = sha256Base64Url(codeVerifier);
        pkceStore.save(state, codeVerifier);

        String scopes = String.join(" ", ssoProperties.getScopes());
        String url = ssoProperties.getIssuer().replaceAll("/$", "") + "/oauth2/authorize"
                + "?response_type=code"
                + "&client_id=" + encode(ssoProperties.getClientId())
                + "&redirect_uri=" + encode(ssoProperties.getRedirectUri())
                + "&scope=" + encode(scopes)
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(codeChallenge)
                + "&code_challenge_method=S256";

        return SsoAuthorizeVO.builder()
                .authorizationUrl(url)
                .state(state)
                .build();
    }

    public String buildLogoutUrl() {
        if (!ssoProperties.isEnabled()) {
            return null;
        }
        String redirect = ssoProperties.getPostLogoutRedirectUri();
        return ssoProperties.getIssuer().replaceAll("/$", "")
                + "/connect/logout?post_logout_redirect_uri=" + encode(redirect);
    }

    public LoginVO handleCallback(SsoCallbackDTO dto) {
        if (!ssoProperties.isEnabled()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "SSO 未启用");
        }
        String codeVerifier = pkceStore.consume(dto.getState());
        if (!StringUtils.hasText(codeVerifier)) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "无效的 state 或已过期");
        }

        String idToken = exchangeCodeForIdToken(dto.getCode(), codeVerifier);
        Claims claims = oidcJwtValidator.parseAndValidate(idToken);
        return userService.loginBySso(claims);
    }

    private String exchangeCodeForIdToken(String code, String codeVerifier) {
        String tokenEndpoint = ssoProperties.getIssuer().replaceAll("/$", "") + "/oauth2/token";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", ssoProperties.getRedirectUri());
        form.add("client_id", ssoProperties.getClientId());
        form.add("client_secret", ssoProperties.getClientSecret());
        form.add("code_verifier", codeVerifier);

        try {
            String body = restClientBuilder.build()
                    .post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(body);
            String idToken = json.path("id_token").asText(null);
            if (!StringUtils.hasText(idToken)) {
                log.warn("Token 响应缺少 id_token: {}", body);
                throw new BizException(ErrorCode.INVALID_CREDENTIALS, "SSO Token 交换失败");
            }
            return idToken;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("SSO Token 交换异常", ex);
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "SSO Token 交换失败");
        }
    }

    private String randomBase64(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String sha256Base64Url(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
