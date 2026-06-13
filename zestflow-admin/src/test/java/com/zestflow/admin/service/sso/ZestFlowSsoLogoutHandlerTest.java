package com.zestflow.admin.service.sso;

import com.zestflow.admin.service.sso.revocation.SsoSessionRevocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZestFlowSsoLogoutHandlerTest {

    @Mock
    private SsoSessionRevocationService revocationService;

    @InjectMocks
    private ZestFlowSsoLogoutHandler handler;

    @Test
    void onBackchannelLogout_revokesPrincipal() {
        handler.onBackchannelLogout("admin");
        verify(revocationService).revokeByUsername("admin");
    }
}
