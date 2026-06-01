package com.zestflow.admin.repository;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zestflow.admin.model.entity.UserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<UserPO> {

    /**
     * 按用户名查找（忽略租户过滤，用于登录）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM `user` WHERE username = #{username} LIMIT 1")
    UserPO findByUsername(@Param("username") String username);

    /**
     * 查询所有用户（忽略租户过滤，用于用户管理）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM `user`")
    List<UserPO> selectAllWithoutTenant();

    /**
     * 分页查询用户（忽略租户过滤，用于用户管理）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM `user` ${ew.customSqlSegment}")
    <P extends IPage<UserPO>> P selectPageWithoutTenant(P page, @Param("ew") Wrapper<UserPO> queryWrapper);

    /**
     * 按邮箱查找（忽略租户过滤）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM `user` WHERE username = #{username}")
    Long countByUsername(@Param("username") String username);

    /**
     * 按邮箱统计（忽略租户过滤）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM `user` WHERE email = #{email}")
    Long countByEmail(@Param("email") String email);

    /**
     * 按用户名统计（排除指定ID，忽略租户过滤）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM `user` WHERE username = #{username} AND id != #{excludeId}")
    Long countByUsernameExcludingId(@Param("username") String username, @Param("excludeId") Long excludeId);

    /**
     * 按邮箱统计（排除指定ID，忽略租户过滤）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM `user` WHERE email = #{email} AND id != #{excludeId}")
    Long countByEmailExcludingId(@Param("email") String email, @Param("excludeId") Long excludeId);

    /**
     * 按ID查询（忽略租户过滤，用于用户管理跨租户查询）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM `user` WHERE id = #{id}")
    UserPO selectByIdWithoutTenant(@Param("id") Long id);

    /**
     * 按重置令牌查找（忽略租户过滤）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM `user` WHERE reset_token = #{token} LIMIT 1")
    UserPO findByResetToken(@Param("token") String token);

    /**
     * 按验证令牌查找（忽略租户过滤）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM `user` WHERE verify_token = #{token} LIMIT 1")
    UserPO findByVerifyToken(@Param("token") String token);
}
