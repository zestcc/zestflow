package com.zestflow.collector.jdbc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.collector.jdbc.entity.ChainEventPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 链执行事件 Mapper
 */
public interface ChainEventMapper extends BaseMapper<ChainEventPO> {

    /**
     * 批量插入（INSERT IGNORE 保证幂等性）
     */
    @Insert({
            "<script>",
            "INSERT IGNORE INTO chain_event(event_id, event_type, chain_id, chain_name,",
            "  node_id, node_name, executor_id, app_name, params, result,",
            "  error_message, cost_ms, status, timestamp, metadata, create_time)",
            "VALUES ",
            "<foreach collection='list' item='e' separator=','>",
            "  (#{e.eventId}, #{e.eventType}, #{e.chainId}, #{e.chainName},",
            "   #{e.nodeId}, #{e.nodeName}, #{e.executorId}, #{e.appName}, #{e.params}, #{e.result},",
            "   #{e.errorMessage}, #{e.costMs}, #{e.status}, #{e.timestamp}, #{e.metadata}, NOW())",
            "</foreach>",
            "</script>"
    })
    int insertIgnoreBatch(@Param("list") List<ChainEventPO> list);
}
