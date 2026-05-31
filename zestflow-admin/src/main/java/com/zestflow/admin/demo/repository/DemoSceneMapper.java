package com.zestflow.admin.demo.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.admin.demo.model.entity.DemoScenePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 演示场景 Mapper
 */
@Mapper
public interface DemoSceneMapper extends BaseMapper<DemoScenePO> {
}
