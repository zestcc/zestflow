package com.zestflow.admin.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 事件查询结果 DTO — 映射 Collector 端返回的分页结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventQueryResult {

    private int code;
    private String message;
    private PageData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageData {
        private List<Map<String, Object>> list;
        private long total;
        private int page;
        private int pageSize;
    }
}
