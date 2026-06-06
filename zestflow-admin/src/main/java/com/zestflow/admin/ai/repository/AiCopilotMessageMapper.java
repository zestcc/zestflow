package com.zestflow.admin.ai.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.admin.ai.model.entity.AiCopilotMessagePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiCopilotMessageMapper extends BaseMapper<AiCopilotMessagePO> {
}
