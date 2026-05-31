package com.zestflow.admin.demo.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.admin.demo.model.entity.DemoRecordPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 演示记录 Mapper
 */
@Mapper
public interface DemoRecordMapper extends BaseMapper<DemoRecordPO> {
}
