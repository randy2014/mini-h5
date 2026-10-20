package com.mini.novel.vip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mini.novel.vip.entity.UserSubscribe;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserSubscribeMapper extends BaseMapper<UserSubscribe> {

    // 注意：user_subscribe.status 在 2026-10 已由字符串枚举改为 TINYINT（1=生效），
    // 切勿再用 status = 'ACTIVE' 这类字符串比较（MySQL 会把 'ACTIVE' 转成 0，永远匹配不到）。
    @Select("SELECT * FROM user_subscribe WHERE user_id = #{userId} AND channel_id = #{channelId} "
            + "AND status = " + UserSubscribe.STATUS_ACTIVE + " AND end_time > NOW() ORDER BY end_time DESC LIMIT 1")
    UserSubscribe selectActive(@Param("userId") Long userId, @Param("channelId") Long channelId);
}
