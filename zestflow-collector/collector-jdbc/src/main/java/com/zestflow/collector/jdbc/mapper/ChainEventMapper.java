package com.zestflow.collector.jdbc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.collector.jdbc.entity.ChainEventPO;
import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventStatsQuery;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 链执行事件 Mapper
 */
@Mapper
public interface ChainEventMapper extends BaseMapper<ChainEventPO> {

    /**
     * 批量插入（INSERT IGNORE 保证幂等性）
     */
    @Insert({
            "<script>",
            "INSERT IGNORE INTO chain_event(event_id, event_type, execution_id, chain_id, chain_name,",
            "  node_id, node_name, executor_id, app_code, app_name, params, result,",
            "  error_message, cost_ms, status, timestamp, metadata, create_time)",
            "VALUES ",
            "<foreach collection='list' item='e' separator=','>",
            "  (#{e.eventId}, #{e.eventType}, #{e.executionId}, #{e.chainId}, #{e.chainName},",
            "   #{e.nodeId}, #{e.nodeName}, #{e.executorId}, #{e.appCode}, #{e.appName}, #{e.params}, #{e.result},",
            "   #{e.errorMessage}, #{e.costMs}, #{e.status}, #{e.timestamp}, #{e.metadata}, NOW())",
            "</foreach>",
            "</script>"
    })
    int insertIgnoreBatch(@Param("list") List<ChainEventPO> list);

    /**
     * 查询执行轨迹列表（GROUP BY execution_id）
     */
    @Select({
            "<script>",
            "SELECT e.execution_id AS executionId,",
            "  ANY_VALUE(e.chain_name) AS chainName,",
            "  ANY_VALUE(e.executor_id) AS executorId,",
            "  ANY_VALUE(e.app_name) AS appName,",
            "  ANY_VALUE(e.app_code) AS appCode,",
            "  MIN(e.timestamp) AS timestamp,",
            "  MAX(CASE WHEN e.event_type IN ('CHAIN_COMPLETED','CHAIN_FAILED','CHAIN_TIMEOUT')",
            "       THEN e.cost_ms ELSE NULL END) AS costMs,",
            "  MAX(CASE WHEN e.event_type IN ('CHAIN_FAILED','CHAIN_TIMEOUT') THEN 0",
            "       WHEN e.event_type = 'CHAIN_COMPLETED' THEN 1 ELSE -1 END) AS status,",
            "  COUNT(*) AS eventCount",
            "FROM chain_event e",
            "WHERE e.execution_id IS NOT NULL",
            "<if test='query.chainName != null and query.chainName != \"\"'>",
            "  AND e.chain_name LIKE CONCAT('%', #{query.chainName}, '%')",
            "</if>",
            "<if test='query.executorId != null and query.executorId != \"\"'>",
            "  AND e.executor_id = #{query.executorId}",
            "</if>",
            "<if test='query.status != null'>",
            "  AND e.status = #{query.status}",
            "</if>",
            "GROUP BY e.execution_id",
            "ORDER BY timestamp DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<ChainEventPO> selectExecutionTraces(@Param("query") EventQuery query,
                                              @Param("limit") int limit,
                                              @Param("offset") int offset);

    /**
     * 统计执行轨迹总数
     */
    @Select({
            "<script>",
            "SELECT COUNT(DISTINCT e.execution_id)",
            "FROM chain_event e",
            "WHERE e.execution_id IS NOT NULL",
            "<if test='query.chainName != null and query.chainName != \"\"'>",
            "  AND e.chain_name LIKE CONCAT('%', #{query.chainName}, '%')",
            "</if>",
            "<if test='query.executorId != null and query.executorId != \"\"'>",
            "  AND e.executor_id = #{query.executorId}",
            "</if>",
            "<if test='query.status != null'>",
            "  AND e.status = #{query.status}",
            "</if>",
            "</script>"
    })
    long countExecutionTraces(@Param("query") EventQuery query);

    /**
     * 查询指定 executionId 的所有事件（按时间升序）
     */
    @Select("SELECT * FROM chain_event WHERE execution_id = #{executionId} ORDER BY timestamp ASC")
    List<ChainEventPO> selectByExecutionId(@Param("executionId") String executionId);

    /**
     * 聚合统计：总事件数、平均耗时、成功/失败/超时数
     */
    @Select({
            "<script>",
            "SELECT",
            "  COUNT(*) AS totalCount,",
            "  AVG(CASE WHEN e.event_type IN ('CHAIN_COMPLETED','CHAIN_FAILED','CHAIN_TIMEOUT')",
            "       THEN e.cost_ms ELSE NULL END) AS avgCostMs,",
            "  SUM(CASE WHEN e.event_type = 'CHAIN_COMPLETED' THEN 1 ELSE 0 END) AS successCount,",
            "  SUM(CASE WHEN e.event_type IN ('CHAIN_FAILED','CHAIN_TIMEOUT') THEN 1 ELSE 0 END) AS failCount",
            "FROM chain_event e",
            "WHERE 1=1",
            "<if test='query.startTime != null'>",
            "  AND e.timestamp &gt;= #{query.startTime}",
            "</if>",
            "<if test='query.endTime != null'>",
            "  AND e.timestamp &lt;= #{query.endTime}",
            "</if>",
            "</script>"
    })
    Map<String, Object> selectAggregatedStats(@Param("query") EventStatsQuery query);
}
