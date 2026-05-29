package com.zestflow.admin.service.impl;

import com.zestflow.admin.client.dto.EventQueryDTO;
import com.zestflow.admin.service.LogService;
import com.zestflow.collector.model.dto.EventQuery;
import com.zestflow.collector.spi.EventQueryService;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ChainEvent.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日志查询服务实现 — 直连 DB 查询 chain_event 表
 */
@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private final EventQueryService eventQueryService;

    @Override
    public Map<String, Object> queryEvents(EventQueryDTO query) {
        EventQuery eventQuery = toEventQuery(query);
        List<ChainEvent> list = eventQueryService.queryEvents(eventQuery);
        long total = eventQueryService.countEvents(eventQuery);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", query.getPage());
        result.put("pageSize", query.getPageSize());
        return result;
    }

    private static EventQuery toEventQuery(EventQueryDTO dto) {
        EventQuery query = new EventQuery();
        query.setChainId(dto.getChainId());
        query.setExecutorId(dto.getExecutorId());
        query.setAppName(dto.getAppName());
        if (dto.getEventTypes() != null) {
            query.setEventTypes(dto.getEventTypes().stream()
                    .map(EventType::valueOf).collect(Collectors.toList()));
        }
        query.setStartTime(dto.getStartTime());
        query.setEndTime(dto.getEndTime());
        query.setStatus(dto.getStatus());
        query.setKeyword(dto.getKeyword());
        query.setPage(dto.getPage());
        query.setPageSize(dto.getPageSize());
        return query;
    }
}
