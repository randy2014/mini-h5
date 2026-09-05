package com.mini.novel.api.controller;

import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.common.result.Result;
import com.mini.novel.vip.entity.UserCoinLog;
import com.mini.novel.vip.service.CoinService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coin")
public class CoinController {
    private final CoinService coinService;
    private final CurrentUserResolver currentUserResolver;

    public CoinController(CoinService coinService, CurrentUserResolver currentUserResolver) {
        this.coinService = coinService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/balance")
    public Result<Map<String, Long>> balance() {
        Long userId = currentUserResolver.requireUser(null).getId();
        return Result.ok(Map.of("balance", coinService.balance(userId)));
    }

    @GetMapping("/logs")
    public Result<List<UserCoinLog>> logs() {
        Long userId = currentUserResolver.requireUser(null).getId();
        return Result.ok(coinService.logs(userId));
    }
}
