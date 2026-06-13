package com.zestflow.admin.service.sso;

import cn.zest.sso.client.SsoLogoutHandler;
import com.zestflow.admin.service.sso.revocation.SsoSessionRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "zestflow.sso", name = "enabled", havingValue = "true")
public class ZestFlowSsoLogoutHandler implements SsoLogoutHandler {

    private final SsoSessionRevocationService revocationService;

    @Override
    public void onBackchannelLogout(String principal) {
        revocationService.revokeByUsername(principal);
    }
}
