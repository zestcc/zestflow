package com.zestflow.executor.registry;

import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.model.dto.ComponentDTO;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import com.zestflow.common.registry.HeartbeatFailureTracker;
import com.zestflow.executor.chain.ChainDeclarationRegistry;
import com.zestflow.executor.scanner.ComponentScanner;
import com.zestflow.executor.server.ExecutorServer;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
public class ExecutorRegistrar implements ApplicationRunner {

    private final AdminClient adminClient;
    private final ExecutorProperties properties;
    private final ExecutorServer executorServer;
    private final Environment environment;
    private final ComponentScanner componentScanner;
    private final ChainDeclarationRegistry chainDeclarationRegistry;

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final HeartbeatFailureTracker heartbeatFailures = new HeartbeatFailureTracker();
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "zestflow-heartbeat");
                t.setDaemon(true);
                return t;
            });

    /** 最大退避间隔 30s */
    private static final long MAX_BACKOFF_MS = TimeUnit.SECONDS.toMillis(30);

    /** 前几次重试打印 WARN，之后降级为间歇性 ERROR 防日志洪刷 */
    private static final int WARN_THRESHOLD = 5;

    private String executorId;

    public String getExecutorId() {
        return executorId;
    }

    public boolean isRegistered() {
        return registered.get();
    }

    @Override
    public void run(ApplicationArguments args) {
        initExecutorId();
        RegisterDTO registerDTO = buildRegisterDTO();
        if (adminClient.register(registerDTO)) {
            registered.set(true);
            heartbeatFailures.reset();
            log.info("执行器首次注册成功 executorId={}", executorId);
        } else {
            log.warn("执行器首次注册失败，进入退避重试模式 executorId={}", executorId);
        }
        // 无论首次注册是否成功，都启动心跳循环（失败则下次 tick 触发重试注册）
        heartbeatScheduler.schedule(this::tick, 0, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        log.info("执行器关闭，开始注销 executorId={}", executorId);
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (registered.get()) {
            adminClient.deregister(executorId);
        }
        log.info("执行器已注销 executorId={}", executorId);
    }

    /**
     * 单次心跳循环：注册/心跳 → 计算下次间隔 → 重新调度
     */
    private void tick() {
        try {
            if (!registered.get()) {
                retryRegister();
            } else {
                sendHeartbeat();
            }
        } catch (Exception e) {
            log.warn("心跳线程异常 executorId={} error={}", executorId, e.getMessage());
            if (markUnregisteredIfHeartbeatExhausted()) {
                log.warn("心跳连续异常达到阈值，降级为重新注册 executorId={}", executorId);
            }
        }
        scheduleNext();
    }

    /**
     * 重试注册：指数退避，无限重试，日志级别随次数渐变
     */
    private void retryRegister() {
        int attempt = retryCount.get();
        if (adminClient.register(buildRegisterDTO())) {
            registered.set(true);
            retryCount.set(0);
            heartbeatFailures.reset();
            log.info("执行器重试注册成功 executorId={}", executorId);
        } else {
            retryCount.incrementAndGet();
            if (attempt < WARN_THRESHOLD) {
                log.warn("注册失败(第{}次)，{}s 后重试 executorId={}",
                        attempt + 1, nextDelaySeconds(), executorId);
            } else if (attempt % 10 == 0) {
                log.error("注册持续失败(第{}次)，仍在重试 executorId={}", attempt + 1, executorId);
            }
        }
    }

    /**
     * 发送心跳；连续失败达到阈值后才降级为 register（对标 Nacos/xxl-job 瞬时容错）。
     */
    private void sendHeartbeat() {
        HeartbeatDTO dto = HeartbeatDTO.builder()
                .executorId(executorId)
                .declaredChainKeys(new ArrayList<>(chainDeclarationRegistry.getDeclaredKeys()))
                .build();

        if (adminClient.heartbeat(dto)) {
            heartbeatFailures.onSuccess();
            return;
        }
        if (markUnregisteredIfHeartbeatExhausted()) {
            log.warn("心跳连续失败达到阈值({})，降级为重新注册 executorId={}",
                    RegistryConstants.HEARTBEAT_FAILURE_THRESHOLD_BEFORE_REREGISTER, executorId);
        } else {
            log.debug("心跳失败({}/{})，下次重试 executorId={}",
                    heartbeatFailures.consecutiveFailures(),
                    RegistryConstants.HEARTBEAT_FAILURE_THRESHOLD_BEFORE_REREGISTER, executorId);
        }
    }

    private boolean markUnregisteredIfHeartbeatExhausted() {
        if (!heartbeatFailures.onFailure()) {
            return false;
        }
        registered.set(false);
        heartbeatFailures.reset();
        return true;
    }

    /**
     * 计算下次 tick 延迟：注册阶段指数退避，正常阶段固定间隔
     */
    private void scheduleNext() {
        long delayMs = nextDelayMs();
        heartbeatScheduler.schedule(this::tick, delayMs, TimeUnit.MILLISECONDS);
    }

    private long nextDelayMs() {
        if (registered.get()) {
            return TimeUnit.SECONDS.toMillis(properties.getHeartbeatInterval());
        }
        // 指数退避：1s, 2s, 4s, 8s, 16s, 30s（上限）
        return Math.min(1000L * (1 << Math.min(retryCount.get(), 5)), MAX_BACKOFF_MS);
    }

    private long nextDelaySeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(nextDelayMs());
    }

    private RegisterDTO buildRegisterDTO() {
        String appCode = resolveAppCode();
        String appName = properties.getAppName() != null ? properties.getAppName() : appCode;
        return RegisterDTO.builder()
                .executorId(executorId)
                .host(properties.getHost())
                .port(executorServer.getPort())
                .appCode(appCode)
                .appName(appName)
                .components(buildComponentDTOs())
                .declaredChainKeys(new ArrayList<>(chainDeclarationRegistry.getDeclaredKeys()))
                .build();
    }

    /**
     * 从 ComponentScanner 提取所有 @ZestExecute 元件清单，
     * 注册时随 RegisterDTO 透传给 Admin
     */
    private List<ComponentDTO> buildComponentDTOs() {
        List<ComponentDTO> list = new ArrayList<>();
        for (ComponentScanner.ComponentMeta meta : componentScanner.getRegistry().values()) {
            list.add(ComponentDTO.builder()
                    .componentId(meta.getExecuteId())
                    .componentName(meta.getName() != null ? meta.getName() : "")
                    .description(meta.getDescription() != null ? meta.getDescription() : "")
                    .groupName(meta.getGroupName() != null ? meta.getGroupName() : "")
                    .timeout(meta.getTimeout())
                    .async(meta.isAsync())
                    .componentType(meta.getComponentType().name())
                    .build());
        }
        return list;
    }

    private String resolveAppCode() {
        if (properties.getAppCode() != null && !properties.getAppCode().isEmpty()) {
            return properties.getAppCode();
        }
        String springName = environment.getProperty("spring.application.name");
        if (springName != null && !springName.isEmpty()) {
            return springName;
        }
        return "default";
    }

    private void initExecutorId() {
        this.executorId = String.format("%s@%s:%d",
                resolveAppCode(),
                properties.getHost(),
                executorServer.getPort());
    }
}
