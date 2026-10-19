package com.mini.novel.vip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mini.novel.vip.entity.CoinRechargeOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CoinRechargeOrderMapper extends BaseMapper<CoinRechargeOrder> {

    @Select("SELECT * FROM coin_recharge_order WHERE order_no = #{orderNo} FOR UPDATE")
    CoinRechargeOrder selectByOrderNoForUpdate(@Param("orderNo") String orderNo);
}
