package com.mini.novel.vip.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.vip.entity.CoinRechargeOrder;
import java.time.LocalDateTime;
import java.util.List;

public interface RechargeService {

    CoinRechargeOrder createOrder(Long userId, Long packageId, Integer payMethod, String remark);

    CoinRechargeOrder queryOrder(Long userId, String orderNo);

    Page<CoinRechargeOrder> myOrders(Long userId, long page, long pageSize);

    List<CoinRechargeOrder> listOrders(Integer status, String keyword, LocalDateTime startTime, LocalDateTime endTime);

    CoinRechargeOrder markPaidHint(Long userId, String orderNo);

    CoinRechargeOrder confirmOrder(String orderNo, Long operatorId);

    CoinRechargeOrder cancelOrder(String orderNo, Long operatorId);
}
