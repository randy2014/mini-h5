package com.mini.novel.vip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mini.novel.vip.entity.UserCoinBalance;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserCoinBalanceMapper extends BaseMapper<UserCoinBalance> {

    @Select("SELECT * FROM user_coin_balance WHERE user_id = #{userId} FOR UPDATE")
    UserCoinBalance selectByUserIdForUpdate(@Param("userId") Long userId);
}
