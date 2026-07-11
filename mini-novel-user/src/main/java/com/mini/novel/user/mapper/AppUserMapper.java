package com.mini.novel.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mini.novel.user.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
    @Select("SELECT * FROM app_user WHERE mobile = #{mobile} LIMIT 1 FOR UPDATE")
    AppUser selectByMobileForUpdate(@Param("mobile") String mobile);

    @Select("SELECT * FROM app_user WHERE id = #{id} LIMIT 1 FOR UPDATE")
    AppUser selectByIdForUpdate(@Param("id") Long id);
}
