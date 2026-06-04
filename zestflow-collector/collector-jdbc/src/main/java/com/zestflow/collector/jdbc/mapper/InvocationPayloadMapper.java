package com.zestflow.collector.jdbc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.collector.jdbc.entity.InvocationPayloadPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InvocationPayloadMapper extends BaseMapper<InvocationPayloadPO> {

    @Insert("INSERT INTO invocation_payload(invocation_id, source_type, execution_id, scene_code, "
            + "request_body, response_body, request_headers, tenant_id, app_code, created_at) "
            + "VALUES(#{invocationId}, #{sourceType}, #{executionId}, #{sceneCode}, "
            + "#{requestBody}, #{responseBody}, #{requestHeaders}, #{tenantId}, #{appCode}, NOW()) "
            + "ON DUPLICATE KEY UPDATE request_body=VALUES(request_body), response_body=VALUES(response_body), "
            + "request_headers=VALUES(request_headers), execution_id=VALUES(execution_id)")
    int upsert(InvocationPayloadPO po);

    @Select("SELECT * FROM invocation_payload WHERE invocation_id = #{invocationId}")
    InvocationPayloadPO selectByInvocationId(@Param("invocationId") String invocationId);
}
