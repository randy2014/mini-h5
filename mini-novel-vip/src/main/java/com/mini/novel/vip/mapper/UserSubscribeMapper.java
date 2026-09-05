package com.mini.novel.vip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mini.novel.vip.entity.UserSubscribe;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserSubscribeMapper extends BaseMapper<UserSubscribe> {

    @Select("SELECT * FROM user_subscribe WHERE user_id = #{userId} AND channel_id = #{channelId} "
            + "AND status = 'ACTIVE' AND end_time > NOW() ORDER BY end_time DESC LIMIT 1")
    UserSubscribe selectActive(@Param("userId") Long userId, @Param("channelId") Long channelId);
}
