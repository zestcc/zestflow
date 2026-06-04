package com.zestflow.admin.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.config.TenantModeConfig;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.repository.TenantMapper;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 统一租户开户 — IP 试玩 / 公开 API / 管理员创建均调用 {@link #provision(TenantProvisionRequest)}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioner {

    private static final String IP_TENANT_CODE_PREFIX = "demo-";

    private final TenantMapper tenantMapper;
    private final TenantIpMappingMapper tenantIpMappingMapper;
    private final TenantTemplateCloner templateCloner;
    private final TenantModeConfig tenantModeConfig;

    /**
     * IP 零门槛试玩：查映射，无则走统一开户（trial + 试玩 TTL）。
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantIpMappingPO resolveOrProvisionByIp(String clientIp) {
        TenantIpMappingPO existing = findIpMapping(clientIp);
        if (existing != null) {
            return existing;
        }
        synchronized (("ip-provision:" + clientIp).intern()) {
            existing = findIpMapping(clientIp);
            if (existing != null) {
                return existing;
            }
            String code = buildIpTenantCode(clientIp);
            TenantProvisionRequest request = TenantProvisionRequest.builder()
                    .name("试玩沙箱-" + code.substring(IP_TENANT_CODE_PREFIX.length()))
                    .code(code)
                    .description("IP 试玩自动创建")
                    .tenantType(TenantTypes.TRIAL)
                    .provisionSource(ProvisionSources.IP)
                    .ttl(Duration.ofMinutes(tenantModeConfig.getIpTenantTimeoutMinutes()))
                    .ipAddress(clientIp)
                    .createdBy("ip-provision")
                    .build();
            TenantProvisionResult result = provisionInternal(request);
            return result.getIpMapping();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public TenantProvisionResult provision(TenantProvisionRequest request) {
        validateRequest(request);
        return provisionInternal(request);
    }

    private TenantProvisionResult provisionInternal(TenantProvisionRequest request) {
        ensureCodeAvailable(request.getCode());

        LocalDateTime now = LocalDateTime.now();
        TenantPO tenant = new TenantPO();
        tenant.setName(request.getName());
        tenant.setCode(request.getCode());
        tenant.setDescription(request.getDescription());
        tenant.setStatus(1);
        tenant.setTenantType(request.getTenantType());
        tenant.setProvisionSource(request.getProvisionSource());
        tenant.setLastActiveAt(now);
        tenant.setCreatedBy(request.getCreatedBy());
        tenant.setUpdatedBy(request.getCreatedBy());
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);

        if (request.isTrial() && request.getTtl() != null) {
            tenant.setExpiresAt(now.plus(request.getTtl()));
        }

        try {
            tenantMapper.insert(tenant);
        } catch (DuplicateKeyException e) {
            if (ProvisionSources.IP.equals(request.getProvisionSource())) {
                TenantPO existing = tenantMapper.selectOne(
                        new LambdaQueryWrapper<TenantPO>().eq(TenantPO::getCode, request.getCode()).last("LIMIT 1"));
                if (existing != null) {
                    TenantIpMappingPO mapped = findIpMapping(request.getIpAddress());
                    if (mapped != null) {
                        return TenantProvisionResult.builder()
                                .tenant(existing)
                                .ipMapping(mapped)
                                .cloneSummary(TenantCloneSummary.empty())
                                .build();
                    }
                    return TenantProvisionResult.builder()
                            .tenant(existing)
                            .ipMapping(bindIpMapping(request.getIpAddress(), existing.getId()))
                            .cloneSummary(TenantCloneSummary.empty())
                            .build();
                }
            }
            throw new BizException(ErrorCode.TENANT_CODE_EXISTS);
        }

        TenantCloneSummary cloneSummary = TenantCloneSummary.empty();
        long templateId = templateCloner.resolveTemplateTenantId(request.getTemplateTenantId());
        if (!tenant.getId().equals(templateId)) {
            cloneSummary = templateCloner.cloneFromTemplate(tenant.getId(), templateId);
        }

        TenantIpMappingPO ipMapping = null;
        if (StringUtils.hasText(request.getIpAddress())) {
            ipMapping = bindIpMapping(request.getIpAddress(), tenant.getId());
        }

        log.info("租户开户完成 tenantId={} code={} type={} source={} itemsCloned={} expiresAt={}",
                tenant.getId(), tenant.getCode(), tenant.getTenantType(), tenant.getProvisionSource(),
                cloneSummary.totalItems(), tenant.getExpiresAt());

        return TenantProvisionResult.builder()
                .tenant(tenant)
                .ipMapping(ipMapping)
                .cloneSummary(cloneSummary)
                .build();
    }

    private TenantIpMappingPO bindIpMapping(String ipAddress, Long tenantId) {
        TenantIpMappingPO mapping = new TenantIpMappingPO();
        mapping.setIpAddress(ipAddress);
        mapping.setTenantId(tenantId);
        mapping.setLastActiveAt(LocalDateTime.now());
        try {
            tenantIpMappingMapper.insert(mapping);
            return mapping;
        } catch (DuplicateKeyException e) {
            TenantIpMappingPO concurrent = findIpMapping(ipAddress);
            if (concurrent != null) {
                return concurrent;
            }
            throw e;
        }
    }

    private void validateRequest(TenantProvisionRequest request) {
        if (request == null || !StringUtils.hasText(request.getName()) || !StringUtils.hasText(request.getCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        if (!TenantTypes.STANDARD.equals(request.getTenantType()) && !TenantTypes.TRIAL.equals(request.getTenantType())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        if (request.isTrial() && (request.getTtl() == null || request.getTtl().isZero() || request.getTtl().isNegative())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void ensureCodeAvailable(String code) {
        Long count = tenantMapper.selectCount(
                new LambdaQueryWrapper<TenantPO>().eq(TenantPO::getCode, code));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.TENANT_CODE_EXISTS);
        }
    }

    private TenantIpMappingPO findIpMapping(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return null;
        }
        return tenantIpMappingMapper.selectOne(
                new LambdaQueryWrapper<TenantIpMappingPO>()
                        .eq(TenantIpMappingPO::getIpAddress, clientIp)
                        .last("LIMIT 1"));
    }

    public static String buildIpTenantCode(String clientIp) {
        return IP_TENANT_CODE_PREFIX + sha256Prefix(clientIp, 8);
    }

    static String sha256Prefix(String value, int hexChars) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, Math.min(hexChars / 2, hash.length));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public void touchTenantActivity(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return;
        }
        TenantPO update = new TenantPO();
        update.setId(tenantId);
        update.setLastActiveAt(LocalDateTime.now());
        tenantMapper.updateById(update);
    }
}
