package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.vip.entity.VipAdjustLog;
import com.mini.novel.vip.entity.VipInvitationCode;
import com.mini.novel.vip.entity.VipInvitationRecord;
import com.mini.novel.vip.entity.VipOperationAudit;
import com.mini.novel.vip.mapper.VipAdjustLogMapper;
import com.mini.novel.vip.service.VipInvitationService;
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
    private final VipInvitationService vipInvitationService;

    public AdminUserController(AppUserMapper appUserMapper, VipAdjustLogMapper vipAdjustLogMapper,
                               VipInvitationService vipInvitationService) {
        this.appUserMapper = appUserMapper;
        this.vipAdjustLogMapper = vipAdjustLogMapper;
        this.vipInvitationService = vipInvitationService;
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
    public Result<VipInvitationService.VipAdminResult> vip(@PathVariable("id") Long id, @RequestBody VipAdjustRequest request) {
        return Result.ok(vipInvitationService.adjustVip(id, request.action(), request.days(),
                request.expireAt(), request.operatorId(), request.reason(), request.requestId()));
    }

    @GetMapping("/{id}/vip-logs")
    public Result<List<VipAdjustLog>> vipLogs(@PathVariable("id") Long id) {
        return Result.ok(vipAdjustLogMapper.selectList(new LambdaQueryWrapper<VipAdjustLog>()
                .eq(VipAdjustLog::getUserId, id)
                .orderByDesc(VipAdjustLog::getCreatedAt)
                .last("LIMIT 100")));
    }

    @GetMapping("/{id}/invite-code")
    public Result<VipInvitationCode> inviteCode(@PathVariable("id") Long id) {
        return Result.ok(vipInvitationService.currentCode(id));
    }

    @PutMapping("/invite-codes/{codeId}/enable")
    public Result<VipInvitationCode> enableInviteCode(@PathVariable("codeId") Long codeId, @RequestBody AdminReasonRequest request) {
        return Result.ok(vipInvitationService.enableCode(codeId, request.operatorId(), request.reason(), request.requestId()));
    }

    @PutMapping("/invite-codes/{codeId}/disable")
    public Result<VipInvitationCode> disableInviteCode(@PathVariable("codeId") Long codeId, @RequestBody AdminReasonRequest request) {
        return Result.ok(vipInvitationService.disableCode(codeId, request.operatorId(), request.reason(), request.requestId()));
    }

    @PutMapping("/{id}/invite-code/reissue")
    public Result<VipInvitationCode> reissueInviteCode(@PathVariable("id") Long id, @RequestBody AdminReasonRequest request) {
        return Result.ok(vipInvitationService.reissueCode(id, request.operatorId(), request.reason(), request.requestId()));
    }

    @PutMapping("/invite-codes/{codeId}/quota")
    public Result<VipInvitationCode> inviteCodeQuota(@PathVariable("codeId") Long codeId, @RequestBody InviteQuotaRequest request) {
        return Result.ok(vipInvitationService.updateQuota(codeId, request.totalQuota(), request.operatorId(), request.reason(), request.requestId()));
    }

    @GetMapping("/{id}/invite-records")
    public Result<List<VipInvitationRecord>> inviteRecords(@PathVariable("id") Long id) {
        return Result.ok(vipInvitationService.records(id));
    }

    @GetMapping("/{id}/operation-logs")
    public Result<List<VipOperationAudit>> operationLogs(@PathVariable("id") Long id) {
        return Result.ok(vipInvitationService.audits(id));
    }

    public record UserStatusRequest(Integer status) {
    }

    public record VipAdjustRequest(String action, Integer days, String expireAt, String reason, Long operatorId, String requestId) {
    }

    public record AdminReasonRequest(Long operatorId, String reason, String requestId) {
    }

    public record InviteQuotaRequest(Integer totalQuota, Long operatorId, String reason, String requestId) {
    }
}
