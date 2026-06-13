package com.zestflow.admin.service.sso.provider;

import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.dto.SsoCallbackDTO;
import com.zestflow.admin.model.vo.LoginVO;
import com.zestflow.admin.model.vo.SsoAuthorizeVO;
import com.zestflow.admin.model.vo.SsoConfigVO;
import com.zestflow.admin.service.sso.spi.SsoProvider;
import com.zestflow.common.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * SSO 关闭时的空实现。
 */
@Component
public class DisabledSsoProvider implements SsoProvider {

    @Override
    public String providerId() {
        return "none";
    }

    @Override
    public SsoConfigVO buildPublicConfig(SsoProperties props) {
        return SsoConfigVO.builder()
                .enabled(false)
                .provider("none")
                .displayName("SSO")
                .build();
    }

    @Override
    public SsoAuthorizeVO buildAuthorizeUrl(SsoProperties props) {
        throw new BizException(ErrorCode.VALIDATION_ERROR, "SSO 未启用");
    }

    @Override
    public String buildLogoutUrl(SsoProperties props) {
        return null;
    }

    @Override
    public LoginVO handleCallback(SsoCallbackDTO dto, SsoProperties props) {
        throw new BizException(ErrorCode.VALIDATION_ERROR, "SSO 未启用");
    }
}
