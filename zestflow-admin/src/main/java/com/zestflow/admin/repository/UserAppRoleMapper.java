package com.zestflow.admin.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.admin.model.entity.UserAppRolePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAppRoleMapper extends BaseMapper<UserAppRolePO> {
}
