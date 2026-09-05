package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.vip.entity.UserCoinLog;
import com.mini.novel.vip.mapper.UserCoinLogMapper;
import com.mini.novel.vip.service.CoinService;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/coins")
public class AdminCoinController {
    private final AppUserMapper appUserMapper;
    private final CoinService coinService;
    private final UserCoinLogMapper coinLogMapper;

    public AdminCoinController(AppUserMapper appUserMapper, CoinService coinService,
                               UserCoinLogMapper coinLogMapper) {
        this.appUserMapper = appUserMapper;
        this.coinService = coinService;
        this.coinLogMapper = coinLogMapper;
    }

    @GetMapping("/users")
    public Result<List<AppUser>> users(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<AppUser>()
                .orderByDesc(AppUser::getUpdatedAt)
                .last("LIMIT 50");
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AppUser::getNickname, keyword).or().like(AppUser::getMobile, keyword));
        }
        return Result.ok(appUserMapper.selectList(wrapper));
    }

    @GetMapping("/balance")
    public Result<Map<String, Long>> balance(@RequestParam Long userId) {
        return Result.ok(Map.of("balance", coinService.balance(userId)));
    }

    @PostMapping("/recharge")
    public Result<Void> recharge(@RequestBody RechargeRequest request) {
        coinService.recharge(request.userId(), request.amount(), request.operatorId(), request.remark());
        return Result.ok();
    }

    @GetMapping("/logs")
    public Result<List<UserCoinLog>> logs() {
        return Result.ok(coinLogMapper.selectList(new LambdaQueryWrapper<UserCoinLog>()
                .eq(UserCoinLog::getBizType, UserCoinLog.BIZ_RECHARGE)
                .orderByDesc(UserCoinLog::getId)
                .last("LIMIT 200")));
    }

    public record RechargeRequest(Long userId, long amount, Long operatorId, String remark) {
    }
}
