package com.zestflow.admin.service;

import com.zestflow.admin.client.dto.EventQueryDTO;
import com.zestflow.admin.client.dto.EventQueryResult;

/**
 * 日志查询服务
 */
public interface LogService {

    /**
     * 查询事件日志（分页）
     */
    EventQueryResult queryEvents(EventQueryDTO query);
}
