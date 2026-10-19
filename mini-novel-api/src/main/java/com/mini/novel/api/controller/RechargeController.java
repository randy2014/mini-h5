package com.mini.novel.api.controller;

import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.common.result.Result;
import com.mini.novel.vip.config.RechargeProperties;
import com.mini.novel.vip.entity.CoinRechargeOrder;
import com.mini.novel.vip.service.RechargeService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recharge")
public class RechargeController {
    private final RechargeService rechargeService;
    private final RechargeProperties properties;
    private final CurrentUserResolver currentUserResolver;

    public RechargeController(RechargeService rechargeService, RechargeProperties properties,
                              CurrentUserResolver currentUserResolver) {
        this.rechargeService = rechargeService;
        this.properties = properties;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/packages")
    public Result<Map<String, Object>> packages() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("packages", properties.getPackages());
        data.put("wxQrUrl", properties.getWxQrUrl());
        data.put("alipayQrUrl", properties.getAlipayQrUrl());
        data.put("notice", properties.getNotice());
        return Result.ok(data);
    }

    @PostMapping("/orders")
    public Result<CoinRechargeOrder> create(@RequestBody CreateRequest request) {
        Long userId = currentUserResolver.requireUser(null).getId();
        return Result.ok(rechargeService.createOrder(userId, request.packageId(), request.payMethod(), request.remark()));
    }

    @GetMapping("/orders/{orderNo}")
    public Result<CoinRechargeOrder> query(@PathVariable String orderNo) {
        Long userId = currentUserResolver.requireUser(null).getId();
        return Result.ok(rechargeService.queryOrder(userId, orderNo));
    }

    @GetMapping("/orders")
    public Result<List<CoinRechargeOrder>> myOrders() {
        Long userId = currentUserResolver.requireUser(null).getId();
        return Result.ok(rechargeService.myOrders(userId));
    }

    public record CreateRequest(Long packageId, Integer payMethod, String remark) {
    }
}
