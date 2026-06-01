package com.zestflow.admin.playground.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 演示场景 Mapper
 */
@Mapper
public interface PlaygroundSceneMapper extends BaseMapper<PlaygroundScenePO> {
}
