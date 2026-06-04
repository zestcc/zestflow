package com.zestflow.admin.demo.ip;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;
import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.repository.TenantMapper;
import com.zestflow.admin.config.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 公网 IP 试玩沙箱 — 首次见到 IP 时创建临时租户并从母版租户克隆演示场景。
 * <p>
 * 仅在 {@code zestflow.tenant.ip-demo-mode=enabled} 时装配；生产 prod 配置禁止开启。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "zestflow.tenant", name = "ip-demo-mode", havingValue = "enabled")
public class DemoTenantProvisioner {

    /** 系统母版租户 — initData.sql tenant id=1 */
    public static final long TEMPLATE_TENANT_ID = 1L;

    public static final String DEMO_APP_CODE = "demo-app";

    private static final String TENANT_CODE_PREFIX = "demo-";

    private final TenantMapper tenantMapper;
    private final TenantIpMappingMapper tenantIpMappingMapper;
    private final PlaygroundSceneMapper playgroundSceneMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 查找或创建 IP 对应的试玩租户映射。
     */
    public TenantIpMappingPO resolveOrProvision(String clientIp) {
        TenantIpMappingPO existing = findMapping(clientIp);
        if (existing != null) {
            return existing;
        }
        synchronized (("ip-demo:" + clientIp).intern()) {
            existing = findMapping(clientIp);
            if (existing != null) {
                return existing;
            }
            return transactionTemplate.execute(status -> provisionLocked(clientIp));
        }
    }

    TenantIpMappingPO provisionLocked(String clientIp) {
        TenantIpMappingPO racing = findMapping(clientIp);
        if (racing != null) {
            return racing;
        }

        String tenantCode = buildTenantCode(clientIp);
        TenantPO tenant = new TenantPO();
        tenant.setName("试玩沙箱-" + tenantCode.substring(TENANT_CODE_PREFIX.length()));
        tenant.setCode(tenantCode);
        tenant.setDescription("IP 试玩自动创建");
        tenant.setStatus(1);
        tenant.setLastActiveAt(LocalDateTime.now());
        tenant.setCreatedBy("ip-demo");
        tenant.setUpdatedBy("ip-demo");
        try {
            tenantMapper.insert(tenant);
        } catch (DuplicateKeyException e) {
            TenantPO existing = tenantMapper.selectOne(
                    new LambdaQueryWrapper<TenantPO>().eq(TenantPO::getCode, tenantCode).last("LIMIT 1"));
            if (existing == null) {
                throw e;
            }
            tenant = existing;
            TenantIpMappingPO mapped = findMapping(clientIp);
            if (mapped != null) {
                return mapped;
            }
        }

        int cloned = clonePlaygroundScenes(tenant.getId());
        log.info("IP 试玩租户已创建 ip={} tenantId={} code={} scenesCloned={}",
                clientIp, tenant.getId(), tenantCode, cloned);

        TenantIpMappingPO mapping = new TenantIpMappingPO();
        mapping.setIpAddress(clientIp);
        mapping.setTenantId(tenant.getId());
        mapping.setLastActiveAt(LocalDateTime.now());
        try {
            tenantIpMappingMapper.insert(mapping);
            return mapping;
        } catch (DuplicateKeyException e) {
            log.debug("IP 映射并发写入，回读 ip={}", clientIp);
            TenantIpMappingPO concurrent = findMapping(clientIp);
            if (concurrent != null) {
                return concurrent;
            }
            throw e;
        }
    }

    private TenantIpMappingPO findMapping(String clientIp) {
        return tenantIpMappingMapper.selectOne(
                new LambdaQueryWrapper<TenantIpMappingPO>()
                        .eq(TenantIpMappingPO::getIpAddress, clientIp)
                        .last("LIMIT 1"));
    }

    static String buildTenantCode(String clientIp) {
        return TENANT_CODE_PREFIX + sha256Prefix(clientIp, 8);
    }

    private int clonePlaygroundScenes(long targetTenantId) {
        List<PlaygroundScenePO> templates = playgroundSceneMapper.selectList(
                new LambdaQueryWrapper<PlaygroundScenePO>()
                        .eq(PlaygroundScenePO::getTenantId, TEMPLATE_TENANT_ID)
                        .eq(PlaygroundScenePO::getAppCode, DEMO_APP_CODE));

        if (templates.isEmpty()) {
            log.warn("母版租户无 demo-app 演示场景，跳过克隆 templateTenantId={}", TEMPLATE_TENANT_ID);
            return 0;
        }

        Long previousTenant = TenantContextHolder.getTenantId();
        try {
            TenantContextHolder.setTenantId(targetTenantId);
            LocalDateTime now = LocalDateTime.now();
            for (PlaygroundScenePO src : templates) {
                PlaygroundScenePO copy = new PlaygroundScenePO();
                copy.setSceneCode(src.getSceneCode());
                copy.setName(src.getName());
                copy.setDescription(src.getDescription());
                copy.setRequestPath(src.getRequestPath());
                copy.setRequestMethod(src.getRequestMethod());
                copy.setRequestHeaders(src.getRequestHeaders());
                copy.setBodyType(src.getBodyType());
                copy.setRequestBody(src.getRequestBody());
                copy.setResponseExample(src.getResponseExample());
                copy.setChainCode(src.getChainCode());
                copy.setRateLimit(src.getRateLimit());
                copy.setTenantId(targetTenantId);
                copy.setAppCode(src.getAppCode());
                copy.setCreatedBy("ip-demo");
                copy.setUpdatedBy("ip-demo");
                copy.setCreatedAt(now);
                copy.setUpdatedAt(now);
                playgroundSceneMapper.insert(copy);
            }
            return templates.size();
        } finally {
            if (previousTenant != null) {
                TenantContextHolder.setTenantId(previousTenant);
            } else {
                TenantContextHolder.clear();
            }
        }
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
}
