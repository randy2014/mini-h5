package com.mini.novel.vip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.vip.config.RechargeProperties;
import com.mini.novel.vip.entity.CoinRechargeOrder;
import com.mini.novel.vip.mapper.CoinRechargeOrderMapper;
import com.mini.novel.vip.service.CoinService;
import com.mini.novel.vip.service.RechargeService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RechargeServiceImpl implements RechargeService {
    private final CoinRechargeOrderMapper orderMapper;
    private final RechargeProperties properties;
    private final CoinService coinService;

    public RechargeServiceImpl(CoinRechargeOrderMapper orderMapper, RechargeProperties properties, CoinService coinService) {
        this.orderMapper = orderMapper;
        this.properties = properties;
        this.coinService = coinService;
    }

    @Override
    public CoinRechargeOrder createOrder(Long userId, Long packageId, Integer payMethod, String remark) {
        RechargeProperties.RechargePackage pack = findPackage(packageId);
        LocalDateTime now = LocalDateTime.now();
        CoinRechargeOrder order = new CoinRechargeOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setPackageId(pack.getId());
        order.setAmountYuan(pack.getAmountYuan());
        order.setCoins(pack.getCoins());
        order.setStatus(CoinRechargeOrder.STATUS_PENDING);
        order.setPayMethod(payMethod == null ? CoinRechargeOrder.PAY_ALIPAY : payMethod);
        order.setRemark(remark);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);
        return order;
    }

    @Override
    public CoinRechargeOrder queryOrder(Long userId, String orderNo) {
        CoinRechargeOrder order = orderMapper.selectOne(new LambdaQueryWrapper<CoinRechargeOrder>()
                .eq(CoinRechargeOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该订单");
        }
        return order;
    }

    @Override
    public List<CoinRechargeOrder> myOrders(Long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<CoinRechargeOrder>()
                .eq(CoinRechargeOrder::getUserId, userId)
                .orderByDesc(CoinRechargeOrder::getId)
                .last("LIMIT 100"));
    }

    @Override
    public List<CoinRechargeOrder> listOrders(Integer status) {
        return orderMapper.selectList(new LambdaQueryWrapper<CoinRechargeOrder>()
                .eq(status != null, CoinRechargeOrder::getStatus, status)
                .orderByDesc(CoinRechargeOrder::getId)
                .last("LIMIT 200"));
    }

    @Override
    @Transactional
    public CoinRechargeOrder confirmOrder(String orderNo, Long operatorId) {
        CoinRechargeOrder order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值订单不存在");
        }
        if (order.getStatus() != null && order.getStatus() != CoinRechargeOrder.STATUS_PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "订单已处理，请勿重复确认");
        }
        LocalDateTime now = LocalDateTime.now();
        coinService.recharge(order.getUserId(), order.getCoins(), operatorId,
                "充值订单 " + order.getOrderNo() + " 人工确认到账");
        order.setStatus(CoinRechargeOrder.STATUS_CONFIRMED);
        order.setConfirmedBy(operatorId);
        order.setConfirmedAt(now);
        order.setUpdatedAt(now);
        orderMapper.updateById(order);
        return order;
    }

    private RechargeProperties.RechargePackage findPackage(Long packageId) {
        if (packageId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请选择充值套餐");
        }
        return properties.getPackages().stream()
                .filter(p -> packageId.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "充值套餐不存在"));
    }

    private String generateOrderNo() {
        return "RC" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
