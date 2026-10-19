package com.mini.novel.vip.service;

import com.mini.novel.vip.entity.CoinRechargeOrder;
import java.util.List;

public interface RechargeService {

    CoinRechargeOrder createOrder(Long userId, Long packageId, Integer payMethod, String remark);

    CoinRechargeOrder queryOrder(Long userId, String orderNo);

    List<CoinRechargeOrder> myOrders(Long userId);

    List<CoinRechargeOrder> listOrders(Integer status);

    CoinRechargeOrder confirmOrder(String orderNo, Long operatorId);
}
