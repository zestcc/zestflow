package com.zestflow.admin.controller;

import com.zestflow.admin.config.LoginRateLimiter;
import com.zestflow.admin.model.dto.SsoCallbackDTO;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.SsoAuthorizeVO;
import com.zestflow.admin.model.vo.SsoConfigVO;
import com.zestflow.admin.model.vo.UserVO;
import com.zestflow.admin.service.TenantService;
import com.zestflow.admin.service.UserService;
import com.zestflow.admin.service.sso.SsoAuthService;
import com.zestflow.admin.util.JwtUtils;
import com.zestflow.common.model.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerSsoTest {

    @Mock
    private UserService userService;
    @Mock
    private LoginRateLimiter loginRateLimiter;
    @Mock
    private TenantService tenantService;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private SsoAuthService ssoAuthService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(userService, loginRateLimiter, tenantService, jwtUtils, ssoAuthService);
    }

    @Test
    void ssoConfig_returnsServiceConfig() {
        SsoConfigVO config = SsoConfigVO.builder()
                .enabled(true)
                .provider("zest-sso")
                .displayName("ZestSSO")
                .build();
        when(ssoAuthService.getConfig()).thenReturn(config);

        Result<SsoConfigVO> result = authController.ssoConfig();

        assertThat(result.getData().isEnabled()).isTrue();
        assertThat(result.getData().getProvider()).isEqualTo("zest-sso");
        verify(ssoAuthService).getConfig();
    }

    @Test
    void ssoAuthorize_returnsAuthorizationUrl() {
        SsoAuthorizeVO vo = SsoAuthorizeVO.builder()
                .authorizationUrl("http://localhost:9000/oauth2/authorize?state=s1")
                .state("s1")
                .build();
        when(ssoAuthService.buildAuthorizeUrl()).thenReturn(vo);

        Result<SsoAuthorizeVO> result = authController.ssoAuthorize();

        assertThat(result.getData().getState()).isEqualTo("s1");
        verify(ssoAuthService).buildAuthorizeUrl();
    }

    @Test
    void ssoCallback_delegatesToService() {
        SsoCallbackDTO dto = new SsoCallbackDTO();
        dto.setCode("code-1");
        dto.setState("state-1");
        LoginVO login = LoginVO.builder()
                .token("jwt-token")
                .user(UserVO.builder().username("sso_user").build())
                .build();
        when(ssoAuthService.handleCallback(dto)).thenReturn(login);

        Result<LoginVO> result = authController.ssoCallback(dto);

        assertThat(result.getData().getToken()).isEqualTo("jwt-token");
        verify(ssoAuthService).handleCallback(dto);
    }

    @Test
    void ssoLogoutUrl_whenDisabled_returnsNull() {
        when(ssoAuthService.buildLogoutUrl()).thenReturn(null);

        Result<String> result = authController.ssoLogoutUrl();

        assertThat(result.getData()).isNull();
        verify(ssoAuthService).buildLogoutUrl();
    }

    @Test
    void ssoLogoutUrl_whenEnabled_returnsUrl() {
        when(ssoAuthService.buildLogoutUrl()).thenReturn("http://localhost:9000/connect/logout?x=1");

        Result<String> result = authController.ssoLogoutUrl();

        assertThat(result.getData()).contains("connect/logout");
    }
}
