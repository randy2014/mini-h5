package com.mini.novel.vip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.mail.MailService;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.vip.config.RechargeProperties;
import com.mini.novel.vip.entity.CoinRechargeOrder;
import com.mini.novel.vip.mapper.CoinRechargeOrderMapper;
import com.mini.novel.vip.service.CoinService;
import com.mini.novel.vip.service.RechargeService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RechargeServiceImpl implements RechargeService {
    private final CoinRechargeOrderMapper orderMapper;
    private final RechargeProperties properties;
    private final CoinService coinService;
    private final AppUserMapper appUserMapper;
    private final MailService mailService;

    @Value("${app.public-h5-base-url:https://xs2026.site}")
    private String publicBaseUrl;

    public RechargeServiceImpl(CoinRechargeOrderMapper orderMapper, RechargeProperties properties,
                               CoinService coinService, AppUserMapper appUserMapper, MailService mailService) {
        this.orderMapper = orderMapper;
        this.properties = properties;
        this.coinService = coinService;
        this.appUserMapper = appUserMapper;
        this.mailService = mailService;
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
    public Page<CoinRechargeOrder> myOrders(Long userId, long page, long pageSize) {
        long safePage = Math.max(1, page);
        long safePageSize = Math.max(1, Math.min(50, pageSize));
        return orderMapper.selectPage(new Page<>(safePage, safePageSize),
                new LambdaQueryWrapper<CoinRechargeOrder>()
                        .eq(CoinRechargeOrder::getUserId, userId)
                        .orderByDesc(CoinRechargeOrder::getId));
    }

    @Override
    public List<CoinRechargeOrder> listOrders(Integer status, String keyword, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<CoinRechargeOrder> wrapper = new LambdaQueryWrapper<CoinRechargeOrder>()
                .eq(status != null, CoinRechargeOrder::getStatus, status)
                .ge(startTime != null, CoinRechargeOrder::getCreatedAt, startTime)
                .lt(endTime != null, CoinRechargeOrder::getCreatedAt, endTime)
                .orderByDesc(CoinRechargeOrder::getId)
                .last("LIMIT 200");
        if (StringUtils.hasText(keyword)) {
            List<Long> userIds = matchingUserIds(keyword.trim());
            if (userIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(CoinRechargeOrder::getUserId, userIds);
        }
        return orderMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public CoinRechargeOrder markPaidHint(Long userId, String orderNo) {
        CoinRechargeOrder order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }
        if (order.getStatus() != null && order.getStatus() != CoinRechargeOrder.STATUS_PENDING) {
            return order;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean firstMark = order.getPaidHintAt() == null;
        order.setPaidHintAt(now);
        if (firstMark) {
            order.setApproveToken(UUID.randomUUID().toString().replace("-", ""));
        }
        order.setUpdatedAt(now);
        orderMapper.updateById(order);
        if (firstMark) {
            AppUser user = appUserMapper.selectById(order.getUserId());
            String userLabel = user == null
                    ? String.valueOf(order.getUserId())
                    : user.getNickname() + "(" + user.getMobile() + ")";
            String approveUrl = publicBaseUrl + "/api/recharge/approve?token=" + order.getApproveToken();
            mailService.sendRechargePending(order.getOrderNo(), order.getCoins(),
                    String.valueOf(order.getAmountYuan()), userLabel,
                    String.valueOf(order.getPaidHintAt()), approveUrl);
        }
        return order;
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
        order.setApproveToken(null);
        order.setUpdatedAt(now);
        orderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional
    public CoinRechargeOrder cancelOrder(String orderNo, Long operatorId) {
        CoinRechargeOrder order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值订单不存在");
        }
        if (order.getStatus() != null && order.getStatus() != CoinRechargeOrder.STATUS_PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "订单已处理，无法取消");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(CoinRechargeOrder.STATUS_CANCELLED);
        order.setConfirmedBy(operatorId);
        order.setConfirmedAt(now);
        order.setApproveToken(null);
        order.setUpdatedAt(now);
        orderMapper.updateById(order);
        return order;
    }

    @Override
    public CoinRechargeOrder findByApproveToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return orderMapper.selectByApproveToken(token);
    }

    @Override
    @Transactional
    public CoinRechargeOrder approveByToken(String token) {
        CoinRechargeOrder order = findByApproveToken(token);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "审批链接无效或已失效");
        }
        // 复用 confirmOrder：内部会再次锁单、校验待确认状态、发币并清空审批令牌
        return confirmOrder(order.getOrderNo(), null);
    }

    private List<Long> matchingUserIds(String keyword) {
        return appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                        .like(AppUser::getNickname, keyword)
                        .or()
                        .like(AppUser::getMobile, keyword)
                        .last("LIMIT 200"))
                .stream().map(AppUser::getId).toList();
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
