package com.zestflow.admin.playground.support;

/**
 * 试验场执行记录入库前裁剪 — 对标 xxl-job 日志长度上限，防止超大 JSON 撑爆存储。
 */
public final class PlaygroundRecordStorageHelper {

    /** 软上限 512KB（MEDIUMTEXT 16MB 内，兼顾查询与磁盘） */
    public static final int MAX_JSON_CHARS = 512 * 1024;

    private static final String TRUNCATED_SUFFIX = "...[truncated]";

    private PlaygroundRecordStorageHelper() {
    }

    /**
     * 超长 JSON 截断并追加标记，保证 INSERT 不因单字段过大失败。
     */
    public static String truncateJson(String json) {
        if (json == null || json.length() <= MAX_JSON_CHARS) {
            return json;
        }
        int keep = MAX_JSON_CHARS - TRUNCATED_SUFFIX.length();
        if (keep < 0) {
            keep = 0;
        }
        return json.substring(0, keep) + TRUNCATED_SUFFIX;
    }
}
