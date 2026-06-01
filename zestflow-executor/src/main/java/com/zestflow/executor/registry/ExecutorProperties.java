package com.zestflow.executor.registry;

import com.zestflow.common.constant.RegistryConstants;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

@Data
@ConfigurationProperties(prefix = "zestflow.executor")
public class ExecutorProperties implements EnvironmentAware {

    /** 应用编码（为空则使用 spring.application.name） */
    private String appCode;

    /** 应用名称（为空则默认等于 appCode） */
    private String appName;

    private Environment environment;

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        if (appCode == null || appCode.isEmpty()) {
            appCode = environment != null ? environment.getProperty("spring.application.name", "default") : "default";
        }
        if (appName == null || appName.isEmpty()) {
            appName = appCode;
        }
        if (host == null || host.isEmpty()) {
            host = detectLocalHost();
        }
    }

    /** 自动探测本机内网 IPv4 */
    private static String detectLocalHost() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .flatMap(iface -> Collections.list(iface.getInetAddresses()).stream())
                    .filter(addr -> addr instanceof Inet4Address)
                    .filter(addr -> !addr.isLoopbackAddress())
                    .map(InetAddress::getHostAddress)
                    .findFirst()
                    .orElse("127.0.0.1");
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /** Admin 服务地址，多个用逗号分隔，如 http://localhost:8080 */
    private String adminAddresses = "http://localhost:8080";

    /** 认证令牌（与 Admin 端共享） */
    private String accessToken;

    /** 心跳间隔（秒） */
    private int heartbeatInterval = RegistryConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS;

    /** 执行器暴露的 Host（为空则自动探测本机 IP） */
    private String host;

    /** 执行器暴露的 Port */
    private int port = 9999;

    /** 是否启用统一执行端点（Spring MVC /execute），默认关闭 */
    private boolean executeEndpointEnabled = false;

    /** 租户 ID（默认 1，多租户环境下 Admin 分配） */
    private long tenantId = 1L;

    /** 注册/心跳请求超时（毫秒） */
    private int timeoutMs = RegistryConstants.DEFAULT_REGISTRY_TIMEOUT_MS;

    // ========== 链执行配置（zestflow.executor.chain.*） ==========

    /** 链加载重试次数 */
    private int chainLoadRetryTimes = 3;

    /** 链加载重试间隔（毫秒） */
    private long chainLoadRetryIntervalMs = 5000;

    /** 是否启用自动热更新 */
    private boolean chainAutoReload = true;

    /** 热更新检查间隔（毫秒） */
    private long chainReloadCheckIntervalMs = 60000;

    /** 同层并行节点数上限 */
    private int chainParallelThreshold = 3;

    /** 数据持久化目录（链定义、设计图等） */
    private String dataDir = "./zestflow-data";

    /** 链默认超时（毫秒） */
    private long chainDefaultTimeoutMs = 60000;

    /** 节点默认重试次数 */
    private int nodeDefaultRetryCount = 0;

    /** 节点默认重试间隔（毫秒） */
    private long nodeDefaultRetryIntervalMs = 1000;
}
