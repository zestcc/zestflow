package com.zestflow.admin.service.sso.oidc;

import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.model.dto.SsoCallbackDTO;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.SsoAuthorizeVO;
import com.zestflow.admin.service.UserService;
import com.zestflow.admin.service.sso.store.InMemorySsoPkceStore;
import com.zestflow.admin.service.sso.store.SsoPkceStore;
import com.zestflow.common.exception.BizException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbstractOidcSsoProviderTest {

    @Mock
    private OidcEndpointResolver endpointResolver;
    @Mock
    private OidcTokenClient tokenClient;
    @Mock
    private OidcJwtValidator jwtValidator;
    @Mock
    private UserService userService;

    private InMemorySsoPkceStore pkceStore;
    private SsoProperties properties;
    private TestOidcProvider provider;

    @BeforeEach
    void setUp() {
        pkceStore = new InMemorySsoPkceStore();
        properties = new SsoProperties();
        properties.setEnabled(true);
        properties.setClientId("zestflow-admin");
        properties.setRedirectUri("http://localhost:5173/login/callback");
        properties.setScopes(List.of("openid", "profile"));

        when(endpointResolver.resolve(any())).thenReturn(new OidcEndpoints(
                "http://localhost:9000",
                "http://localhost:9000/oauth2/authorize",
                "http://localhost:9000/oauth2/token",
                "http://localhost:9000/oauth2/jwks",
                "http://localhost:9000/connect/logout"
        ));

        provider = new TestOidcProvider(pkceStore, endpointResolver, tokenClient, jwtValidator, userService);
    }

    @Test
    void buildAuthorizeUrl_includesPkceParams() {
        SsoAuthorizeVO vo = provider.buildAuthorizeUrl(properties);

        assertThat(vo.getAuthorizationUrl())
                .contains("response_type=code")
                .contains("client_id=zestflow-admin")
                .contains("code_challenge=")
                .contains("code_challenge_method=S256")
                .contains("state=" + vo.getState());
        assertThat(pkceStore.consume(vo.getState())).isNotBlank();
    }

    @Test
    void buildLogoutUrl_usesEndSessionEndpoint() {
        properties.setPostLogoutRedirectUri("http://localhost:5173/login");

        String url = provider.buildLogoutUrl(properties);

        assertThat(url)
                .startsWith("http://localhost:9000/connect/logout")
                .contains("post_logout_redirect_uri=");
    }

    @Test
    void handleCallback_invalidState_throws() {
        SsoCallbackDTO dto = new SsoCallbackDTO();
        dto.setCode("code-1");
        dto.setState("missing-state");

        assertThatThrownBy(() -> provider.handleCallback(dto, properties))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("state");
    }

    @Test
    void handleCallback_validFlow_delegatesToUserService() {
        pkceStore.save("state-ok", "verifier-32chars-minimum-length-ok");
        SsoCallbackDTO dto = new SsoCallbackDTO();
        dto.setCode("auth-code");
        dto.setState("state-ok");

        when(tokenClient.exchangeCodeForIdToken(eq("auth-code"), eq("verifier-32chars-minimum-length-ok"), any(), eq(properties)))
                .thenReturn("id-token-jwt");
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(jwtValidator.parseAndValidate(eq("id-token-jwt"), any(), eq(properties))).thenReturn(claims);
        LoginVO login = LoginVO.builder().token("jwt-from-sso").build();
        when(userService.loginBySso("test-oidc", claims)).thenReturn(login);

        LoginVO result = provider.handleCallback(dto, properties);

        assertThat(result.getToken()).isEqualTo("jwt-from-sso");
        verify(userService).loginBySso("test-oidc", claims);
    }

    private static final class TestOidcProvider extends AbstractOidcSsoProvider {

        TestOidcProvider(SsoPkceStore pkceStore,
                         OidcEndpointResolver endpointResolver,
                         OidcTokenClient tokenClient,
                         OidcJwtValidator jwtValidator,
                         UserService userService) {
            super(pkceStore, endpointResolver, tokenClient, jwtValidator, userService);
        }

        @Override
        public String providerId() {
            return "test-oidc";
        }

        @Override
        protected String displayName() {
            return "Test OIDC";
        }
    }
}
