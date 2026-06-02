package com.zestflow.common.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * 延迟分位数统计（P50 / P95 / P99 / P99.9），用于压测门禁与性能报告。
 */
public final class LatencyPercentiles {

    private final long count;
    private final long minMs;
    private final long maxMs;
    private final double avgMs;
    private final long p50Ms;
    private final long p95Ms;
    private final long p99Ms;
    private final long p999Ms;

    private LatencyPercentiles(long count, long minMs, long maxMs, double avgMs,
                               long p50Ms, long p95Ms, long p99Ms, long p999Ms) {
        this.count = count;
        this.minMs = minMs;
        this.maxMs = maxMs;
        this.avgMs = avgMs;
        this.p50Ms = p50Ms;
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
        this.p999Ms = p999Ms;
    }

    public static LatencyPercentiles fromMillis(Collection<? extends Number> samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.isEmpty()) {
            return empty();
        }
        long[] sorted = samples.stream()
                .mapToLong(Number::longValue)
                .sorted()
                .toArray();
        return fromSorted(sorted);
    }

    public static LatencyPercentiles fromSorted(long[] sorted) {
        if (sorted == null || sorted.length == 0) {
            return empty();
        }
        Arrays.sort(sorted);
        long sum = 0L;
        for (long v : sorted) {
            sum += v;
        }
        int n = sorted.length;
        return new LatencyPercentiles(
                n,
                sorted[0],
                sorted[n - 1],
                (double) sum / n,
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99),
                percentile(sorted, 0.999)
        );
    }

    public static LatencyPercentiles empty() {
        return new LatencyPercentiles(0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Nearest-rank 分位数（与 blackbox 脚本一致）。
     */
    public static long percentile(long[] sorted, double p) {
        if (sorted == null || sorted.length == 0) {
            return 0L;
        }
        if (p <= 0) {
            return sorted[0];
        }
        if (p >= 1) {
            return sorted[sorted.length - 1];
        }
        int index = (int) Math.min(sorted.length - 1, Math.floor(sorted.length * p));
        return sorted[index];
    }

    public long count() {
        return count;
    }

    public long minMs() {
        return minMs;
    }

    public long maxMs() {
        return maxMs;
    }

    public double avgMs() {
        return avgMs;
    }

    public long p50Ms() {
        return p50Ms;
    }

    public long p95Ms() {
        return p95Ms;
    }

    public long p99Ms() {
        return p99Ms;
    }

    public long p999Ms() {
        return p999Ms;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "LatencyPercentiles(empty)";
        }
        return String.format(Locale.ROOT,
                "n=%d min=%dms avg=%.1fms p50=%dms p95=%dms p99=%dms p999=%dms max=%dms",
                count, minMs, avgMs, p50Ms, p95Ms, p99Ms, p999Ms, maxMs);
    }
}
