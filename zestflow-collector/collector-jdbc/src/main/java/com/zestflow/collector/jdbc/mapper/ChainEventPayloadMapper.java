package com.zestflow.collector.jdbc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.collector.jdbc.entity.ChainEventPayloadPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChainEventPayloadMapper extends BaseMapper<ChainEventPayloadPO> {

    @Insert({
            "<script>",
            "INSERT IGNORE INTO chain_event_payload(event_id, params, result, error_message)",
            "VALUES ",
            "<foreach collection='list' item='p' separator=','>",
            "  (#{p.eventId}, #{p.params}, #{p.result}, #{p.errorMessage})",
            "</foreach>",
            "</script>"
    })
    int insertIgnoreBatch(@Param("list") List<ChainEventPayloadPO> list);

    @Select("SELECT * FROM chain_event_payload WHERE event_id = #{eventId}")
    ChainEventPayloadPO selectByEventId(@Param("eventId") String eventId);

    @Select({
            "<script>",
            "SELECT * FROM chain_event_payload WHERE event_id IN",
            "<foreach collection='eventIds' item='id' open='(' separator=',' close=')'>",
            "  #{id}",
            "</foreach>",
            "</script>"
    })
    List<ChainEventPayloadPO> selectByEventIds(@Param("eventIds") List<String> eventIds);
}
