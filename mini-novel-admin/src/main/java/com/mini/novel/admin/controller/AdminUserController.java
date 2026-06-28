package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.vip.entity.VipAdjustLog;
import com.mini.novel.vip.mapper.VipAdjustLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
    private final AppUserMapper appUserMapper;
    private final VipAdjustLogMapper vipAdjustLogMapper;

    public AdminUserController(AppUserMapper appUserMapper, VipAdjustLogMapper vipAdjustLogMapper) {
        this.appUserMapper = appUserMapper;
        this.vipAdjustLogMapper = vipAdjustLogMapper;
    }

    @GetMapping
    public Result<List<AppUser>> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<AppUser>()
                .orderByDesc(AppUser::getUpdatedAt)
                .last("LIMIT 200");
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AppUser::getNickname, keyword).or().like(AppUser::getMobile, keyword));
        }
        if (status != null) {
            wrapper.eq(AppUser::getStatus, status);
        }
        return Result.ok(appUserMapper.selectList(wrapper));
    }

    @GetMapping("/{id}")
    public Result<AppUser> detail(@PathVariable("id") Long id) {
        return Result.ok(appUserMapper.selectById(id));
    }

    @PutMapping("/{id}/status")
    public Result<AppUser> status(@PathVariable("id") Long id, @RequestBody UserStatusRequest request) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setStatus(request.status());
        user.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(user);
        return Result.ok(appUserMapper.selectById(id));
    }

    @PutMapping("/{id}/vip")
    public Result<AppUser> vip(@PathVariable("id") Long id, @RequestBody VipAdjustRequest request) {
        AppUser before = appUserMapper.selectById(id);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = before.getVipExpireTime() != null && before.getVipExpireTime().isAfter(now)
                ? before.getVipExpireTime()
                : now;
        LocalDateTime after = switch (request.action() == null ? "EXTEND" : request.action()) {
            case "CANCEL" -> null;
            case "PERMANENT" -> LocalDateTime.of(2099, 12, 31, 23, 59, 59);
            case "SET" -> request.expireAt();
            default -> base.plusDays(request.days() == null ? 30 : request.days());
        };

        AppUser user = new AppUser();
        user.setId(id);
        user.setVipExpireTime(after);
        user.setVipStatus(after == null ? 0 : (after.getYear() >= 2099 ? 2 : 1));
        user.setUpdatedAt(now);
        appUserMapper.updateById(user);

        VipAdjustLog log = new VipAdjustLog();
        log.setUserId(id);
        log.setAction(request.action() == null ? "EXTEND" : request.action());
        log.setBeforeExpireTime(before.getVipExpireTime());
        log.setAfterExpireTime(after);
        log.setBeforeStatus(String.valueOf(before.getVipStatus()));
        log.setAfterStatus(String.valueOf(user.getVipStatus()));
        log.setDays(request.days());
        log.setReason(request.reason());
        log.setOperatorId(request.operatorId() == null ? 1L : request.operatorId());
        log.setCreatedAt(now);
        vipAdjustLogMapper.insert(log);
        return Result.ok(appUserMapper.selectById(id));
    }

    @GetMapping("/{id}/vip-logs")
    public Result<List<VipAdjustLog>> vipLogs(@PathVariable("id") Long id) {
        return Result.ok(vipAdjustLogMapper.selectList(new LambdaQueryWrapper<VipAdjustLog>()
                .eq(VipAdjustLog::getUserId, id)
                .orderByDesc(VipAdjustLog::getCreatedAt)
                .last("LIMIT 100")));
    }

    public record UserStatusRequest(Integer status) {
    }

    public record VipAdjustRequest(String action, Integer days, LocalDateTime expireAt, String reason, Long operatorId) {
    }
}
