package com.zestflow.admin.service.sso;

import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.model.dto.SsoCallbackDTO;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.SsoAuthorizeVO;
import com.zestflow.admin.model.vo.SsoConfigVO;
import com.zestflow.admin.service.sso.spi.SsoProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SSO 认证门面 — 委托 {@link SsoProviderRegistry} 选择具体提供方。
 */
@Service
@RequiredArgsConstructor
public class SsoAuthService {

    private final SsoProperties ssoProperties;
    private final SsoProviderRegistry providerRegistry;

    public SsoConfigVO getConfig() {
        return providerRegistry.resolve().buildPublicConfig(ssoProperties);
    }

    public SsoAuthorizeVO buildAuthorizeUrl() {
        return providerRegistry.resolve().buildAuthorizeUrl(ssoProperties);
    }

    public String buildLogoutUrl() {
        if (!ssoProperties.isEnabled()) {
            return null;
        }
        return providerRegistry.resolve().buildLogoutUrl(ssoProperties);
    }

    public LoginVO handleCallback(SsoCallbackDTO dto) {
        return providerRegistry.resolve().handleCallback(dto, ssoProperties);
    }
}
