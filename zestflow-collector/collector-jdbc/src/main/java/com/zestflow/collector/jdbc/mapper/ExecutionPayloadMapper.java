package com.zestflow.collector.jdbc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.collector.jdbc.entity.ExecutionPayloadPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExecutionPayloadMapper extends BaseMapper<ExecutionPayloadPO> {

    @Insert({
            "<script>",
            "INSERT IGNORE INTO execution_payload(ref_id, ref_type, execution_id, source_type, scene_code,",
            "  params, result, error_message, extra, tenant_id, app_code, created_at)",
            "VALUES ",
            "<foreach collection='list' item='p' separator=','>",
            "  (#{p.refId}, #{p.refType}, #{p.executionId}, #{p.sourceType}, #{p.sceneCode},",
            "   #{p.params}, #{p.result}, #{p.errorMessage}, #{p.extra}, #{p.tenantId}, #{p.appCode},",
            "   COALESCE(#{p.createdAt}, NOW()))",
            "</foreach>",
            "</script>"
    })
    int insertIgnoreBatch(@Param("list") List<ExecutionPayloadPO> list);

    @Insert("INSERT INTO execution_payload(ref_id, ref_type, execution_id, source_type, scene_code, "
            + "params, result, error_message, extra, tenant_id, app_code, created_at) "
            + "VALUES(#{refId}, #{refType}, #{executionId}, #{sourceType}, #{sceneCode}, "
            + "#{params}, #{result}, #{errorMessage}, #{extra}, #{tenantId}, #{appCode}, NOW()) "
            + "ON DUPLICATE KEY UPDATE params=VALUES(params), result=VALUES(result), "
            + "error_message=VALUES(error_message), extra=VALUES(extra), execution_id=VALUES(execution_id)")
    int upsert(ExecutionPayloadPO po);

    @Select("SELECT * FROM execution_payload WHERE ref_id = #{refId}")
    ExecutionPayloadPO selectByRefId(@Param("refId") String refId);

    @Select({
            "<script>",
            "SELECT * FROM execution_payload WHERE ref_id IN",
            "<foreach collection='refIds' item='id' open='(' separator=',' close=')'>",
            "  #{id}",
            "</foreach>",
            "</script>"
    })
    List<ExecutionPayloadPO> selectByRefIds(@Param("refIds") List<String> refIds);
}
