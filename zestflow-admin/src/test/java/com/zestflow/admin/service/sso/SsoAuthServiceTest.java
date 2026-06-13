package com.zestflow.admin.service.sso;

import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.model.dto.SsoCallbackDTO;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.SsoAuthorizeVO;
import com.zestflow.admin.model.vo.SsoConfigVO;
import com.zestflow.admin.service.sso.provider.DisabledSsoProvider;
import com.zestflow.admin.service.sso.spi.SsoProvider;
import com.zestflow.admin.service.sso.spi.SsoProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsoAuthServiceTest {

    @Mock
    private SsoProvider zestSsoProvider;

    private SsoProperties properties;
    private SsoAuthService ssoAuthService;

    @BeforeEach
    void setUp() {
        properties = new SsoProperties();
        DisabledSsoProvider disabledSsoProvider = new DisabledSsoProvider();
        when(zestSsoProvider.providerId()).thenReturn("zest-sso");
        SsoProviderRegistry registry = new SsoProviderRegistry(
                properties,
                List.of(zestSsoProvider, disabledSsoProvider),
                disabledSsoProvider);
        ssoAuthService = new SsoAuthService(properties, registry);
    }

    @Test
    void getConfig_whenDisabled_returnsDisabledConfig() {
        properties.setEnabled(false);

        SsoConfigVO config = ssoAuthService.getConfig();

        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getProvider()).isEqualTo("none");
    }

    @Test
    void buildAuthorizeUrl_delegatesToResolvedProvider() {
        properties.setEnabled(true);
        properties.setProvider("zest-sso");
        SsoAuthorizeVO vo = SsoAuthorizeVO.builder()
                .authorizationUrl("http://localhost:9000/oauth2/authorize")
                .state("state-1")
                .build();
        when(zestSsoProvider.buildAuthorizeUrl(properties)).thenReturn(vo);

        SsoAuthorizeVO result = ssoAuthService.buildAuthorizeUrl();

        assertThat(result.getState()).isEqualTo("state-1");
        verify(zestSsoProvider).buildAuthorizeUrl(properties);
    }

    @Test
    void buildLogoutUrl_whenDisabled_returnsNull() {
        properties.setEnabled(false);

        assertThat(ssoAuthService.buildLogoutUrl()).isNull();
    }

    @Test
    void handleCallback_delegatesToResolvedProvider() {
        properties.setEnabled(true);
        properties.setProvider("zest-sso");
        SsoCallbackDTO dto = new SsoCallbackDTO();
        dto.setCode("code-1");
        dto.setState("state-1");
        LoginVO login = LoginVO.builder().token("jwt-1").build();
        when(zestSsoProvider.handleCallback(dto, properties)).thenReturn(login);

        LoginVO result = ssoAuthService.handleCallback(dto);

        assertThat(result.getToken()).isEqualTo("jwt-1");
        verify(zestSsoProvider).handleCallback(dto, properties);
    }
}
