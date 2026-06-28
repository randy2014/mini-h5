package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.mapper.CategoryMapper;
import com.mini.novel.book.mapper.ChapterMapper;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.crawler.mapper.CrawlTaskMapper;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.vip.entity.VipOrder;
import com.mini.novel.vip.mapper.VipOrderMapper;
import com.mini.novel.vip.mapper.VipPlanMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {
    private final NovelMapper novelMapper;
    private final ChapterMapper chapterMapper;
    private final CategoryMapper categoryMapper;
    private final AppUserMapper appUserMapper;
    private final VipPlanMapper vipPlanMapper;
    private final VipOrderMapper vipOrderMapper;
    private final CrawlTaskMapper crawlTaskMapper;

    public AdminDashboardController(
            NovelMapper novelMapper,
            ChapterMapper chapterMapper,
            CategoryMapper categoryMapper,
            AppUserMapper appUserMapper,
            VipPlanMapper vipPlanMapper,
            VipOrderMapper vipOrderMapper,
            CrawlTaskMapper crawlTaskMapper) {
        this.novelMapper = novelMapper;
        this.chapterMapper = chapterMapper;
        this.categoryMapper = categoryMapper;
        this.appUserMapper = appUserMapper;
        this.vipPlanMapper = vipPlanMapper;
        this.vipOrderMapper = vipOrderMapper;
        this.crawlTaskMapper = crawlTaskMapper;
    }

    @GetMapping
    public Result<Map<String, Object>> dashboard() {
        LocalDateTime now = LocalDateTime.now();
        Long activeVipUsers = appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .gt(AppUser::getVipExpireTime, now));
        Long paidOrders = vipOrderMapper.selectCount(new LambdaQueryWrapper<VipOrder>()
                .eq(VipOrder::getPayStatus, 1));
        BigDecimal paidAmount = vipOrderMapper.selectList(new LambdaQueryWrapper<VipOrder>()
                        .eq(VipOrder::getPayStatus, 1))
                .stream()
                .map(VipOrder::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("novelCount", novelMapper.selectCount(null));
        data.put("chapterCount", chapterMapper.selectCount(null));
        data.put("categoryCount", categoryMapper.selectCount(null));
        data.put("userCount", appUserMapper.selectCount(null));
        data.put("vipPlanCount", vipPlanMapper.selectCount(null));
        data.put("activeVipUsers", activeVipUsers);
        data.put("paidOrderCount", paidOrders);
        data.put("paidAmount", paidAmount);
        data.put("crawlTaskCount", crawlTaskMapper.selectCount(null));
        data.put("vipChapterCount", chapterMapper.selectCount(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getVip, true)));
        data.put("latestNovels", novelMapper.selectList(new LambdaQueryWrapper<Novel>()
                .orderByDesc(Novel::getUpdatedAt)
                .last("LIMIT 6")));
        return Result.ok(data);
    }
}
