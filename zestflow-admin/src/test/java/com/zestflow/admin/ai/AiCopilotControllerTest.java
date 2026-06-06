package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.dto.AiSuggestRequest;
import com.zestflow.admin.ai.model.dto.AiTenantConfigSaveDTO;
import com.zestflow.admin.ai.model.dto.AiValidateRequest;
import com.zestflow.admin.ai.model.vo.AiConfigStatusVO;
import com.zestflow.admin.ai.model.vo.AiValidationVO;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.service.PermissionService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiCopilotControllerTest {

    @Mock private AiCopilotService aiCopilotService;
    @Mock private TenantAiConfigService tenantAiConfigService;
    @Mock private AiChainKeyHintService chainKeyHintService;
    @Mock private AiChainTemplateService chainTemplateService;
    @Mock private TenantAppContext tenantAppContext;
    @Mock private PermissionService permissionService;
    @Mock private ExecutorProxyService executorProxyService;
    @Mock private Authentication authentication;

    private AiCopilotController controller;

    @BeforeEach
    void setUp() {
        controller = new AiCopilotController(aiCopilotService, tenantAiConfigService,
                chainKeyHintService, chainTemplateService,
                tenantAppContext, permissionService, executorProxyService);
        when(tenantAppContext.getCurrentTenantId()).thenReturn(1L);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getConfig_returnsTenantStatus() {
        AiConfigStatusVO vo = AiConfigStatusVO.builder().globallyEnabled(true).copilotAvailable(false).build();
        when(tenantAiConfigService.getConfigStatus(1L)).thenReturn(vo);

        assertThat(controller.getConfig().getData().isGloballyEnabled()).isTrue();
    }

    @Test
    void validate_delegatesToService() {
        AiValidateRequest req = new AiValidateRequest();
        req.setAppCode("demo-app");
        req.setChainData("{\"nodes\":[]}");

        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, true, 1L));

        AiValidationVO validation = AiValidationVO.builder().valid(true).errors(List.of()).build();
        when(aiCopilotService.validate(req)).thenReturn(validation);

        assertThat(controller.validate(req).getData().isValid()).isTrue();
    }

    @Test
    void suggest_deniesWithoutEditorPermission() {
        AiSuggestRequest req = new AiSuggestRequest();
        req.setAppCode("demo-app");
        req.setUserMessage("test");

        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(new SecurityUtils.AuthDetails(2L, false, 1L));
        when(permissionService.hasAppPermission(2L, "demo-app", "APP_EDITOR")).thenReturn(false);

        assertThatThrownBy(() -> controller.suggest(req))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
    }

    @Test
    void saveTenantConfig_delegatesToService() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);

        AiTenantConfigSaveDTO dto = new AiTenantConfigSaveDTO();
        dto.setEnabled(true);
        dto.setPreset("deepseek");
        when(tenantAiConfigService.saveTenantConfig(eq(1L), any())).thenReturn(
                com.zestflow.admin.ai.model.vo.AiTenantConfigVO.builder().enabled(true).preset("deepseek").build());

        assertThat(controller.saveTenantConfig(dto).getData().getEnabled()).isTrue();
    }
}
