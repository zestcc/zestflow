package com.zestflow.admin.playground;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 试验场执行记录 Mapper
 */
@Mapper
public interface PlaygroundLogMapper extends BaseMapper<PlaygroundLogPO> {
}
