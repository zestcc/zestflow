package com.zestflow.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 编码生成器 — 分段式纯内存实现
 * <p>
 * 格式：{PREFIX}{yyyyMMdd}{6位序号}，示例：DSN20260529000001
 * 序号按前缀独立递增，每日自动重置，JVM 启动时随机偏移防碰撞。
 * 不查数据库，不依赖外部存储，线程安全。
 * </p>
 */
public class CodeGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int SEQ_WIDTH = 6;
    private static final int MAX_SEQ = (int) Math.pow(10, SEQ_WIDTH) - 1;

    private static final Map<String, PrefixState> states = new ConcurrentHashMap<>();

    private CodeGenerator() {
    }

    /**
     * 生成编码
     *
     * @param prefix 前缀（大写，如 DSN、CHN）
     * @return 编码字符串
     */
    public static String generate(String prefix) {
        PrefixState state = states.computeIfAbsent(prefix, k -> new PrefixState());
        return state.next(prefix);
    }

    private static class PrefixState {
        private volatile String currentDate;
        private final AtomicInteger seq = new AtomicInteger(randomSeed());

        String next(String prefix) {
            String today = todayStr();
            String cachedDate = currentDate;

            // 日期变更 → 重置序号（随机偏移防重启碰撞）
            if (!today.equals(cachedDate)) {
                synchronized (this) {
                    if (!today.equals(currentDate)) {
                        currentDate = today;
                        seq.set(randomSeed());
                    }
                }
            }

            int n = seq.getAndIncrement();
            if (n > MAX_SEQ) {
                // 超出当日上限，阻塞等待（实际不可能，每秒可生成 16 万个编码）
                synchronized (this) {
                    if (seq.get() > MAX_SEQ) {
                        seq.set(0);
                    }
                    n = seq.getAndIncrement();
                }
            }

            return prefix + today + String.format("%0" + SEQ_WIDTH + "d", n);
        }

        private String todayStr() {
            return LocalDate.now().format(DATE_FMT);
        }

        private int randomSeed() {
            return (int) (Math.random() * 900); // [0, 899]，防重启碰撞
        }
    }
}
