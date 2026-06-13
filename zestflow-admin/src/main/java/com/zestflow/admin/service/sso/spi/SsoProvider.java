package com.zestflow.admin.service.sso.spi;

import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.model.dto.SsoCallbackDTO;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.SsoAuthorizeVO;
import com.zestflow.admin.model.vo.SsoConfigVO;

/**
 * SSO 提供方 SPI — 支持 ZestSSO、通用 OIDC 等可插拔实现。
 */
public interface SsoProvider {

    /** 提供方标识，如 zest-sso、oidc、none */
    String providerId();

    SsoConfigVO buildPublicConfig(SsoProperties props);

    SsoAuthorizeVO buildAuthorizeUrl(SsoProperties props);

    /** 单点登出 URL；未启用时由门面返回 null */
    String buildLogoutUrl(SsoProperties props);

    LoginVO handleCallback(SsoCallbackDTO dto, SsoProperties props);
}
