package com.zestflow.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.admin.model.entity.ChainSnapshotPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChainSnapshotMapper extends BaseMapper<ChainSnapshotPO> {
}
