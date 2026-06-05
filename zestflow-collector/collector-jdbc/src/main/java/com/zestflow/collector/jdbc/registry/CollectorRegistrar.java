package com.zestflow.collector.jdbc.registry;

import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 采集器注册管理器 — 对标 ExecutorRegistrar，向 Admin 注册采集服务
 * <p>
 * 启动时注册自身（host:port），定时发送心跳，关闭时注销。
 * 与 Executor 一样的生命周期管理，独立注册到 Admin 的 collector_registry 表。
 */
@Slf4j
@RequiredArgsConstructor
public class CollectorRegistrar implements ApplicationRunner {

    private final CollectorAdminClient adminClient;
    private final CollectorRegistryProperties properties;
    private final Environment environment;

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "zestflow-collector-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private static final long MAX_BACKOFF_MS = TimeUnit.SECONDS.toMillis(30);
    private static final int WARN_THRESHOLD = 5;

    private String collectorId;
    private int resolvedPort;

    @Override
    public void run(ApplicationArguments args) {
        resolvedPort = resolvePort();
        collectorId = buildCollectorId();
        RegisterDTO registerDTO = buildRegisterDTO();
        try {
            if (adminClient.register(registerDTO)) {
                registered.set(true);
                log.info("采集器首次注册成功 collectorId={}", collectorId);
            } else {
                log.warn("采集器首次注册失败，进入退避重试模式 collectorId={}", collectorId);
            }
        } catch (Throwable e) {
            log.warn("采集器首次注册异常，进入退避重试模式 collectorId={} error={}", collectorId, e.getMessage(), e);
        }
        heartbeatScheduler.schedule(this::tick, 0, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        log.info("采集器关闭，开始注销 collectorId={}", collectorId);
        heartbeatScheduler.shutdown();
        if (registered.get()) {
            adminClient.deregister(collectorId);
        }
        log.info("采集器已注销 collectorId={}", collectorId);
    }

    private void tick() {
        try {
            if (!registered.get()) {
                retryRegister();
            } else {
                sendHeartbeat();
            }
        } catch (Exception e) {
            log.error("采集器心跳线程异常 collectorId={}", collectorId, e);
            registered.set(false);
        }
        scheduleNext();
    }

    private void retryRegister() {
        int attempt = retryCount.get();
        if (adminClient.register(buildRegisterDTO())) {
            registered.set(true);
            retryCount.set(0);
            log.info("采集器重试注册成功 collectorId={}", collectorId);
        } else {
            retryCount.incrementAndGet();
            if (attempt < WARN_THRESHOLD) {
                log.warn("采集器注册失败(第{}次)，{}s 后重试 collectorId={}",
                        attempt + 1, nextDelaySeconds(), collectorId);
            } else if (attempt % 10 == 0) {
                log.error("采集器注册持续失败(第{}次)，仍在重试 collectorId={}", attempt + 1, collectorId);
            }
        }
    }

    private void sendHeartbeat() {
        HeartbeatDTO dto = HeartbeatDTO.builder()
                .executorId(collectorId)
                .build();
        if (!adminClient.heartbeat(dto)) {
            log.warn("采集器心跳发送失败 collectorId={}", collectorId);
            registered.set(false);
        }
    }

    private void scheduleNext() {
        long delayMs = nextDelayMs();
        heartbeatScheduler.schedule(this::tick, delayMs, TimeUnit.MILLISECONDS);
    }

    private long nextDelayMs() {
        if (registered.get()) {
            return TimeUnit.SECONDS.toMillis(properties.getHeartbeatInterval());
        }
        return Math.min(1000L * (1 << Math.min(retryCount.get(), 5)), MAX_BACKOFF_MS);
    }

    private long nextDelaySeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(nextDelayMs());
    }

    private RegisterDTO buildRegisterDTO() {
        String appCode = resolveAppCode();
        return RegisterDTO.builder()
                .executorId(collectorId)
                .appCode(appCode)
                .appName(appCode)
                .host(resolveHost())
                .port(resolvedPort)
                .build();
    }

    private String buildCollectorId() {
        return String.format("collector@%s:%d", resolveHost(), resolvedPort);
    }

    private String resolveAppCode() {
        String code = environment.getProperty("spring.application.name");
        return code != null && !code.isEmpty() ? code : "default";
    }

    private int resolvePort() {
        if (properties.getPort() > 0) {
            return properties.getPort();
        }
        String portStr = environment.getProperty("server.port");
        if (portStr != null && !portStr.isEmpty()) {
            return Integer.parseInt(portStr);
        }
        return 9998;
    }

    private String resolveHost() {
        if (properties.getHost() != null && !properties.getHost().isEmpty()) {
            return properties.getHost();
        }
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("无法获取本机 IP，使用 127.0.0.1", e);
            return "127.0.0.1";
        }
    }
}
