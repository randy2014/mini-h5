package com.mini.novel.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.api.model.VipStatusVo;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.vip.entity.VipPlan;
import com.mini.novel.vip.mapper.VipPlanMapper;
import com.mini.novel.vip.service.VipAccessService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vip")
public class VipController {
    private final VipPlanMapper vipPlanMapper;
    private final AppUserMapper appUserMapper;
    private final VipAccessService vipAccessService;

    public VipController(VipPlanMapper vipPlanMapper, AppUserMapper appUserMapper, VipAccessService vipAccessService) {
        this.vipPlanMapper = vipPlanMapper;
        this.appUserMapper = appUserMapper;
        this.vipAccessService = vipAccessService;
    }

    @GetMapping("/plans")
    public Result<List<VipPlan>> plans() {
        return Result.ok(vipPlanMapper.selectList(new LambdaQueryWrapper<VipPlan>()
                .eq(VipPlan::getEnabled, true)
                .orderByAsc(VipPlan::getSort)));
    }

    @GetMapping("/status")
    public Result<VipStatusVo> status(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        VipStatusVo vo = new VipStatusVo();
        vo.setActive(vipAccessService.hasActiveVip(userId));
        vo.setVipExpireTime(user.getVipExpireTime());
        return Result.ok(vo);
    }
}
