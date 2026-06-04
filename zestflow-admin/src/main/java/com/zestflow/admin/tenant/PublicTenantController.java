package com.zestflow.admin.tenant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zestflow.admin.config.TenantModeConfig;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.tenant.dto.PublicTenantProvisionDTO;
import com.zestflow.admin.tenant.vo.TenantProvisionVO;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 公开租户开户 — 与 IP 试玩共用 {@link TenantProvisioner}，适合自助试用 / 集成开户。
 */
@Slf4j
@RestController
@RequestMapping("/api/public/tenants")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "zestflow.tenant", name = "public-provision-enabled", havingValue = "true")
public class PublicTenantController {

    private static final int MAX_PROVISION_PER_HOUR = 10;

    private final TenantProvisioner tenantProvisioner;
    private final TenantModeConfig tenantModeConfig;

    private final Cache<String, Integer> provisionRateCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    @PostMapping("/provision")
    public Result<TenantProvisionVO> provision(@Valid @RequestBody(required = false) PublicTenantProvisionDTO dto,
                                               HttpServletRequest request) {
        if (!"multi".equals(tenantModeConfig.getMode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "多租户模式未开启");
        }
        enforceRateLimit(request);

        PublicTenantProvisionDTO body = dto != null ? dto : new PublicTenantProvisionDTO();
        String code = StringUtils.hasText(body.getCode())
                ? body.getCode().trim()
                : "trial-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String name = StringUtils.hasText(body.getName()) ? body.getName().trim() : "试玩租户-" + code;

        TenantProvisionRequest provisionRequest = TenantProvisionRequest.builder()
                .name(name)
                .code(code)
                .description(body.getDescription())
                .tenantType(TenantTypes.TRIAL)
                .provisionSource(ProvisionSources.API)
                .ttl(Duration.ofMinutes(tenantModeConfig.getIpTenantTimeoutMinutes()))
                .createdBy("public-api")
                .build();

        TenantProvisionResult result = tenantProvisioner.provision(provisionRequest);
        log.info("公开 API 开户 tenantId={} code={}", result.getTenant().getId(), result.getTenant().getCode());

        return Result.success(toVo(result));
    }

    private void enforceRateLimit(HttpServletRequest request) {
        String ip = resolveClientIp(request);
        Integer count = provisionRateCache.getIfPresent(ip);
        if (count != null && count >= MAX_PROVISION_PER_HOUR) {
            throw new BizException(ErrorCode.TENANT_PROVISION_RATE_LIMITED, "开户过于频繁，请稍后再试");
        }
        provisionRateCache.put(ip, (count != null ? count : 0) + 1);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        String xr = request.getHeader("X-Real-IP");
        if (xr != null && !xr.isBlank()) {
            return xr;
        }
        return request.getRemoteAddr();
    }

    private static TenantProvisionVO toVo(TenantProvisionResult result) {
        TenantCloneSummary summary = result.getCloneSummary() != null
                ? result.getCloneSummary() : TenantCloneSummary.empty();
        return TenantProvisionVO.builder()
                .tenantId(result.getTenant().getId())
                .tenantCode(result.getTenant().getCode())
                .tenantName(result.getTenant().getName())
                .tenantType(result.getTenant().getTenantType())
                .provisionSource(result.getTenant().getProvisionSource())
                .expiresAt(result.getTenant().getExpiresAt())
                .roles(summary.getRoles())
                .dictTypes(summary.getDictTypes())
                .dictData(summary.getDictData())
                .playgroundScenes(summary.getPlaygroundScenes())
                .schedules(summary.getSchedules())
                .scenesCloned(result.getScenesCloned())
                .itemsCloned(result.getItemsCloned())
                .build();
    }
}
