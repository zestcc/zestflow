package com.zestflow.admin.playground.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaygroundRecordStorageHelperTest {

    @Test
    void truncateJson_shouldReturnNull_whenNull() {
        assertThat(PlaygroundRecordStorageHelper.truncateJson(null)).isNull();
    }

    @Test
    void truncateJson_shouldReturnSame_whenUnderLimit() {
        String json = "{\"ok\":true}";
        assertThat(PlaygroundRecordStorageHelper.truncateJson(json)).isEqualTo(json);
    }

    @Test
    void truncateJson_shouldAppendMarker_whenOverLimit() {
        int over = PlaygroundRecordStorageHelper.MAX_JSON_CHARS + 100;
        String json = "x".repeat(over);
        String result = PlaygroundRecordStorageHelper.truncateJson(json);
        assertThat(result).endsWith("...[truncated]");
        assertThat(result.length()).isEqualTo(PlaygroundRecordStorageHelper.MAX_JSON_CHARS);
    }
}
