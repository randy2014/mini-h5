package com.mini.novel.admin.controller;

import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.vip.entity.CoinRechargeOrder;
import com.mini.novel.vip.service.RechargeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/recharges")
public class AdminRechargeController {
    private final RechargeService rechargeService;
    private final AppUserMapper appUserMapper;

    public AdminRechargeController(RechargeService rechargeService, AppUserMapper appUserMapper) {
        this.rechargeService = rechargeService;
        this.appUserMapper = appUserMapper;
    }

    @GetMapping
    public Result<List<RechargeOrderVo>> list(@RequestParam(required = false) Integer status) {
        return Result.ok(rechargeService.listOrders(status).stream().map(this::toVo).toList());
    }

    @PostMapping("/{orderNo}/confirm")
    public Result<RechargeOrderVo> confirm(@PathVariable String orderNo,
                                           @RequestHeader(value = "X-Operator-Id", required = false) Long operatorId) {
        return Result.ok(toVo(rechargeService.confirmOrder(orderNo, operatorId)));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<RechargeOrderVo> cancel(@PathVariable String orderNo,
                                          @RequestHeader(value = "X-Operator-Id", required = false) Long operatorId) {
        return Result.ok(toVo(rechargeService.cancelOrder(orderNo, operatorId)));
    }

    private RechargeOrderVo toVo(CoinRechargeOrder order) {
        AppUser user = appUserMapper.selectById(order.getUserId());
        return new RechargeOrderVo(
                order.getId(), order.getOrderNo(), order.getUserId(),
                user == null ? null : user.getNickname(),
                user == null ? null : user.getMobile(),
                order.getPackageId(), order.getAmountYuan(), order.getCoins(),
                order.getStatus(), order.getPayMethod(), order.getRemark(),
                order.getConfirmedAt(), order.getCreatedAt());
    }

    public record RechargeOrderVo(Long id, String orderNo, Long userId, String nickname, String mobile,
                                  Long packageId, BigDecimal amountYuan, Long coins, Integer status,
                                  Integer payMethod, String remark, LocalDateTime confirmedAt,
                                  LocalDateTime createdAt) {
    }
}
