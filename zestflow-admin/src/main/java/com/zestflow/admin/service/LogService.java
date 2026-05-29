package com.zestflow.admin.service;

import com.zestflow.admin.client.dto.EventQueryDTO;

import java.util.Map;

/**
 * 日志查询服务
 */
public interface LogService {

    /**
     * 查询事件日志（分页）
     */
    Map<String, Object> queryEvents(EventQueryDTO query);
}
