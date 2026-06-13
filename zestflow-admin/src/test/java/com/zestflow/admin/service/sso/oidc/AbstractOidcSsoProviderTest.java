package com.zestflow.admin.service.sso.oidc;

import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.model.vo.SsoAuthorizeVO;
import com.zestflow.admin.service.UserService;
import com.zestflow.admin.service.sso.store.InMemorySsoPkceStore;
import com.zestflow.admin.service.sso.store.SsoPkceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
