package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.dto.*;
import com.zestflow.admin.ai.model.vo.*;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.service.PermissionService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI Copilot REST API
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiCopilotController {

    private final AiCopilotService aiCopilotService;
    private final TenantAiConfigService tenantAiConfigService;
    private final AiChainKeyHintService chainKeyHintService;
    private final AiChainTemplateService chainTemplateService;
    private final TenantAppContext tenantAppContext;
    private final PermissionService permissionService;
    private final ExecutorProxyService executorProxyService;

    @GetMapping("/config")
    public Result<AiConfigStatusVO> getConfig() {
        Long tenantId = tenantAppContext.getCurrentTenantId();
        return Result.success(tenantAiConfigService.getConfigStatus(tenantId));
    }

    @GetMapping("/providers")
    public Result<List<AiProviderVO>> listProviders() {
        Long tenantId = tenantAppContext.getCurrentTenantId();
        return Result.success(tenantAiConfigService.listProvidersForTenant(tenantId));
    }

    @PostMapping("/test")
    public Result<AiTestConnectionResponse> testConnection(@RequestBody AiTestConnectionRequest request) {
        requireSuperAdminOrTenantAdmin();
        return Result.success(aiCopilotService.testConnection(request));
    }

    @GetMapping("/tenant-config")
    public Result<AiTenantConfigVO> getTenantConfig() {
        requireSuperAdminOrTenantAdmin();
        Long tenantId = tenantAppContext.getCurrentTenantId();
        return Result.success(tenantAiConfigService.getTenantConfig(tenantId));
    }

    @PutMapping("/tenant-config")
    public Result<AiTenantConfigVO> saveTenantConfig(@RequestBody AiTenantConfigSaveDTO dto) {
        requireSuperAdminOrTenantAdmin();
        Long tenantId = tenantAppContext.getCurrentTenantId();
        return Result.success(tenantAiConfigService.saveTenantConfig(tenantId, dto));
    }

    @GetMapping("/context/components")
    public Result<String> listComponents(@RequestParam String appCode) {
        requireAppEditor(appCode);
        String json = executorProxyService.getFromExecutor(appCode, "/api/components", "?page=1&size=9999");
        return Result.success(json);
    }

    @GetMapping("/context/chain-keys")
    public Result<AiChainKeyHintsVO> chainKeys(@RequestParam String appCode) {
        requireAppEditor(appCode);
        return Result.success(chainKeyHintService.getHints(appCode));
    }

    @GetMapping("/templates")
    public Result<List<AiChainTemplateVO>> listTemplates(@RequestParam(required = false) String appCode) {
        if (StringUtils.hasText(appCode)) {
            requireAppEditor(appCode);
        }
        return Result.success(chainTemplateService.list(appCode));
    }

    @GetMapping("/templates/{id}")
    public Result<AiChainTemplateVO> getTemplate(@PathVariable Long id) {
        return Result.success(chainTemplateService.get(id));
    }

    @PostMapping("/templates")
    public Result<AiChainTemplateVO> saveTemplate(@RequestBody AiChainTemplateSaveDTO dto) {
        if (StringUtils.hasText(dto.getAppCode())) {
            requireAppEditor(dto.getAppCode());
        }
        return Result.success(chainTemplateService.save(dto));
    }

    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        chainTemplateService.delete(id);
        return Result.success();
    }

    @PostMapping("/design/explain")
    public Result<AiExplainResponse> explain(@RequestBody AiExplainRequest request) {
        requireAppEditor(request.getAppCode());
        return Result.success(aiCopilotService.explain(request));
    }

    @PostMapping("/design/suggest")
    public Result<AiSuggestResponse> suggest(@RequestBody AiSuggestRequest request) {
        requireAppEditor(request.getAppCode());
        return Result.success(aiCopilotService.suggest(request));
    }

    @PostMapping("/design/validate")
    public Result<AiValidationVO> validate(@RequestBody AiValidateRequest request) {
        if (StringUtils.hasText(request.getAppCode())) {
            requireAppEditor(request.getAppCode());
        }
        return Result.success(aiCopilotService.validate(request));
    }

    @PostMapping("/expression/suggest")
    public Result<AiExpressionSuggestResponse> expressionSuggest(@RequestBody AiExpressionSuggestRequest request) {
        requireAppEditor(request.getAppCode());
        return Result.success(aiCopilotService.expressionSuggest(request));
    }

    @PostMapping("/logs/diagnose")
    public Result<AiDiagnoseResponse> diagnose(@RequestBody AiDiagnoseRequest request) {
        requireAppEditor(request.getAppCode());
        return Result.success(aiCopilotService.diagnose(request));
    }

    @PostMapping("/component/scaffold")
    public Result<AiComponentScaffoldResponse> componentScaffold(@RequestBody AiComponentScaffoldRequest request) {
        requireAppEditor(request.getAppCode());
        return Result.success(aiCopilotService.componentScaffold(request));
    }

    @PostMapping("/sessions/{id}/feedback")
    public Result<Void> sessionFeedback(@PathVariable Long id, @RequestBody AiSessionFeedbackDTO dto) {
        aiCopilotService.recordFeedback(id, dto);
        return Result.success();
    }

    private void requireAppEditor(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (SecurityUtils.isSuperAdmin(auth)) {
            return;
        }
        Long userId = SecurityUtils.getUserId(auth);
        if (userId == null || !permissionService.hasAppPermission(userId, appCode, "APP_EDITOR")) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
    }

    /** 超管或当前租户已登录用户（配置按 tenant_id 隔离） */
    private void requireSuperAdminOrTenantAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }
}
