package com.zestflow.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.admin.model.entity.ChainPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChainMapper extends BaseMapper<ChainPO> {
}
