package com.zestflow.executor.registry;

import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.protocol.ChainFailurePolicy;
import com.zestflow.common.protocol.ChainHttpResponseMode;
import jakarta.annotation.PostConstruct;
import com.zestflow.executor.ai.ExecutorAiProperties;
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

    /** 认证令牌（Netty 端点 X-Access-Token，与 Admin 端共享） */
    private String accessToken;

    /** Admin 机器接口令牌（注册/心跳/链同步，请求头 X-Registry-Token） */
    private String registryToken;

    /** 心跳间隔（秒） */
    private int heartbeatInterval = RegistryConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS;

    /** 执行器暴露的 Host（为空则自动探测本机 IP） */
    private String host;

    /** 执行器暴露的 Port（默认 20550，与 YAML 文档同步） */
    private int port = 20550;

    /** 是否启用统一执行端点（Spring MVC /execute），默认关闭 */
    private boolean executeEndpointEnabled = false;

    /** 是否启用链 HTTP 路由（Mode 2：chainData.config.http.path 别名），默认关闭 */
    private boolean chainRouteEnabled = false;

    /** Mode 1/2 成功响应模式：BODY= PARSER 返回值，DETAIL= 完整执行明细 */
    private ChainHttpResponseMode executeResponseMode = ChainHttpResponseMode.BODY;

    /** Mode 1/2 失败策略：PROPAGATE= 抛异常，ERROR_HANDLER= 调 errorHandler 元件，WRAPPED= success:false 包装 */
    private ChainFailurePolicy executeFailurePolicy = ChainFailurePolicy.PROPAGATE;

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

    /**
     * 未配置 {@link #accessToken} 时，AI API（{@code /api/ai/*}）是否仅允许本机访问。
     */
    private boolean aiLocalhostOnly = true;

    /** Executor 侧 AI（LLM suggest + Hybrid RAG） */
    private ExecutorAiProperties ai = new ExecutorAiProperties();

    /** 链默认超时（毫秒） */
    private long chainDefaultTimeoutMs = 60000;

    /** 节点默认重试次数 */
    private int nodeDefaultRetryCount = 0;

    /** 节点默认重试间隔（毫秒） */
    private long nodeDefaultRetryIntervalMs = 1000;

    /** 链执行线程池核心线程数（0 表示 CPU 核数） */
    private int executePoolCoreSize = 0;

    /** 链执行线程池最大线程数（0 表示 CPU 核数 × 2） */
    private int executePoolMaxSize = 0;

    /** 链执行线程池队列容量 */
    private int executePoolQueueCapacity = 256;

    /** 关闭时等待在途链执行完成的最长时间（毫秒） */
    private long shutdownGracePeriodMs = 15_000;

    /** 是否启用执行幂等（idempotencyKey / traceId） */
    private boolean idempotencyEnabled = true;

    /** 幂等结果缓存 TTL（毫秒） */
    private long idempotencyTtlMs = 300_000;

    /** 并发重复请求等待在途执行的最长时间（毫秒） */
    private long idempotencyWaitMs = 60_000;

    /** 调度分片序号（0..shardTotal-1），多 Executor 同 app 时必填 */
    private int shardIndex = 0;

    /** 本实例声明的分片总数，与 zf_schedule.shard_total 对齐 */
    private int shardTotal = 1;

    public int resolveExecutePoolCoreSize() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return executePoolCoreSize > 0 ? executePoolCoreSize : cpus;
    }

    public int resolveExecutePoolMaxSize() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return executePoolMaxSize > 0 ? executePoolMaxSize : cpus * 2;
    }
}
