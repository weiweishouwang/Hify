package com.hify.shared.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.shared.internal.entity.UserDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 用户的数据访问接口。 */
public interface UserMapper extends BaseMapper<UserDO> {

    /**
     * 按登录名查用户主键。
     *
     * <p>表名 {@code user} 在 SQL 里加反引号 —— MySQL 里 {@code USER} 是非保留字，
     * 但加反引号更保险，也不会因为将来某个版本把它提成保留字就炸。
     *
     * @param username 登录名
     * @return 主键；不存在时为 null
     */
    @Select("SELECT id FROM `user` WHERE username = #{username} AND deleted = 0 LIMIT 1")
    Long findIdByUsername(@Param("username") String username);
}
