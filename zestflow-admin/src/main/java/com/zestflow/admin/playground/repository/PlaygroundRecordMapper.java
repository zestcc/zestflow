package com.zestflow.admin.playground.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.admin.playground.model.entity.PlaygroundRecordPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 演示记录 Mapper
 */
@Mapper
public interface PlaygroundRecordMapper extends BaseMapper<PlaygroundRecordPO> {
}
