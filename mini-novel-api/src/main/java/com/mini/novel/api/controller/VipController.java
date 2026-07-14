package com.mini.novel.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.api.model.VipBookPageVo;
import com.mini.novel.api.model.VipStatusVo;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.api.support.VipPublicationProgress;
import com.mini.novel.common.result.Result;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.vip.entity.VipPlan;
import com.mini.novel.vip.mapper.VipPlanMapper;
import com.mini.novel.vip.service.VipAccessService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vip")
public class VipController {
    private final VipPlanMapper vipPlanMapper;
    private final CurrentUserResolver currentUserResolver;
    private final VipAccessService vipAccessService;
    private final NovelMapper novelMapper;
    private final VipPublicationProgress publicationProgress;

    public VipController(VipPlanMapper vipPlanMapper, CurrentUserResolver currentUserResolver, VipAccessService vipAccessService, NovelMapper novelMapper, VipPublicationProgress publicationProgress) {
        this.vipPlanMapper = vipPlanMapper;
        this.currentUserResolver = currentUserResolver;
        this.vipAccessService = vipAccessService;
        this.novelMapper = novelMapper;
        this.publicationProgress = publicationProgress;
    }

    @GetMapping("/books")
    public Result<VipBookPageVo> books(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "pageSize", defaultValue = "20") long pageSize) {
        long safePage = Math.max(1, page);
        long safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<Novel> result = novelMapper.selectPage(new Page<>(safePage, safePageSize), new LambdaQueryWrapper<Novel>()
                .ne(Novel::getStatus, 0)
                .eq(Novel::getVipRequired, true)
                .like(Novel::getSourceUrl, "book.xbookcn.net")
                .orderByDesc(Novel::getUpdatedAt)
                .orderByDesc(Novel::getId));
        result.getRecords().forEach(publicationProgress::enrich);
        return Result.ok(VipBookPageVo.from(result));
    }

    @GetMapping("/plans")
    public Result<List<VipPlan>> plans() {
        return Result.ok(vipPlanMapper.selectList(new LambdaQueryWrapper<VipPlan>()
                .eq(VipPlan::getEnabled, true)
                .orderByAsc(VipPlan::getSort)));
    }

    @GetMapping("/status")
    public Result<VipStatusVo> status(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AppUser user = currentUserResolver.requireUser(userId);
        VipStatusVo vo = new VipStatusVo();
        vo.setActive(vipAccessService.hasActiveVip(user.getId()));
        vo.setVipExpireTime(user.getVipExpireTime());
        return Result.ok(vo);
    }
}
