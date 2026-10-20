package com.mini.novel.vip.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.mini.novel.vip.entity.UserSubscribe;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/**
 * 防止 2026-10「字符串枚举 → 整数枚举」重构后再次把 status 写回字符串比较。
 * 根因：user_subscribe.status 已改为 TINYINT（1=生效），
 * status = 'ACTIVE' 会被 MySQL 转成 0，导致已订阅用户被判定为不可访问。
 */
class UserSubscribeMapperSqlTest {

    @Test
    void selectActiveUsesIntegerStatusEnum() throws Exception {
        Method m = UserSubscribeMapper.class.getMethod("selectActive", Long.class, Long.class);
        Select select = m.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        String sql = String.join(" ", select.value());

        assertThat(sql).contains("status = " + UserSubscribe.STATUS_ACTIVE);
        assertThat(sql).doesNotContain("'ACTIVE'", "'EXPIRED'", "'CANCELLED'");
    }
}
