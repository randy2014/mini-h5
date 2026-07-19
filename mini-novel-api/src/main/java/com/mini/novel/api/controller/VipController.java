package com.mini.novel.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.api.model.VipBookCategoryVo;
import com.mini.novel.api.model.VipBookPageVo;
import com.mini.novel.api.model.VipStatusVo;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.api.support.VipPublicationProgress;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.entity.NovelVipCategoryMapping;
import com.mini.novel.book.entity.VipCategory;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.book.mapper.NovelVipCategoryMappingMapper;
import com.mini.novel.book.mapper.VipCategoryMapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.vip.entity.VipPlan;
import com.mini.novel.vip.mapper.VipPlanMapper;
import com.mini.novel.vip.service.VipAccessService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vip")
public class VipController {
    static final String CATEGORY_ALL = "all";
    static final String CATEGORY_OTHER = "other";
    static final String CATEGORY_ALL_NAME = "\u5168\u90e8";
    static final String CATEGORY_OTHER_NAME = "\u5176\u4ed6";

    private final VipPlanMapper vipPlanMapper;
    private final CurrentUserResolver currentUserResolver;
    private final VipAccessService vipAccessService;
    private final NovelMapper novelMapper;
    private final VipCategoryMapper vipCategoryMapper;
    private final NovelVipCategoryMappingMapper novelVipCategoryMappingMapper;
    private final VipPublicationProgress publicationProgress;

    public VipController(VipPlanMapper vipPlanMapper, CurrentUserResolver currentUserResolver,
                         VipAccessService vipAccessService, NovelMapper novelMapper,
                         VipCategoryMapper vipCategoryMapper,
                         NovelVipCategoryMappingMapper novelVipCategoryMappingMapper,
                         VipPublicationProgress publicationProgress) {
        this.vipPlanMapper = vipPlanMapper;
        this.currentUserResolver = currentUserResolver;
        this.vipAccessService = vipAccessService;
        this.novelMapper = novelMapper;
        this.vipCategoryMapper = vipCategoryMapper;
        this.novelVipCategoryMappingMapper = novelVipCategoryMappingMapper;
        this.publicationProgress = publicationProgress;
    }

    @GetMapping("/books")
    public Result<VipBookPageVo> books(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(value = "category", defaultValue = CATEGORY_ALL) String category) {
        long safePage = Math.max(1, page);
        long safePageSize = Math.max(1, Math.min(100, pageSize));
        Map<Long, VipCategory> categories = validCategories();
        QueryWrapper<Novel> query = vipBooksQuery(category, categories.keySet())
                .orderByDesc("updated_at")
                .orderByDesc("id");
        Page<Novel> result = novelMapper.selectPage(new Page<>(safePage, safePageSize), query);
        result.getRecords().forEach(novel -> {
            normalizeVipCategory(novel, categories);
            publicationProgress.enrich(novel);
        });
        return Result.ok(VipBookPageVo.from(result));
    }

    @GetMapping("/categories")
    public Result<List<VipBookCategoryVo>> categories() {
        Map<Long, VipCategory> categories = validCategories();
        long total = novelMapper.selectCount(vipBooksQuery(CATEGORY_ALL, categories.keySet()));
        List<VipBookCategoryVo> result = new java.util.ArrayList<>();
        result.add(new VipBookCategoryVo(CATEGORY_ALL, null, CATEGORY_ALL_NAME, total));
        categories.values().forEach(category -> {
            long count = novelMapper.selectCount(vipBooksQuery(String.valueOf(category.getId()), categories.keySet()));
            if (count > 0) {
                result.add(new VipBookCategoryVo(String.valueOf(category.getId()), category.getId(),
                        category.getName().trim(), count));
            }
        });
        long otherCount = novelMapper.selectCount(vipBooksQuery(CATEGORY_OTHER, categories.keySet()));
        if (otherCount > 0) {
            result.add(new VipBookCategoryVo(CATEGORY_OTHER, null, CATEGORY_OTHER_NAME, otherCount));
        }
        return Result.ok(result);
    }

    QueryWrapper<Novel> vipBooksQuery(String category, Set<Long> validCategoryIds) {
        QueryWrapper<Novel> query = new QueryWrapper<Novel>()
                .ne("status", 0)
                .eq("vip_required", true)
                .exists("""
                        SELECT 1
                        FROM novel_source_mapping vip_mapping
                        JOIN mini_novel_crawler.crawl_source vip_source
                          ON vip_source.source_code = vip_mapping.source_code
                         AND vip_source.source_type = 'AUTHORIZED_VIP'
                        JOIN mini_novel_crawler.crawler_authorized_book vip_authorized
                          ON vip_authorized.source_code = vip_mapping.source_code
                         AND vip_authorized.source_book_id = vip_mapping.source_book_id
                        WHERE vip_mapping.novel_id = novel.id
                          AND vip_mapping.content_status = 'CONTENT_READY'
                          AND EXISTS (
                              SELECT 1
                              FROM mini_novel_crawler.crawler_authorized_book_audit vip_audit
                              WHERE vip_audit.authorized_book_id = vip_authorized.id
                                AND vip_audit.action LIKE '%APPROVE%'
                          )
                        """)
                .exists("SELECT 1 FROM chapter vip_chapter WHERE vip_chapter.novel_id = novel.id");
        String key = StringUtils.hasText(category) ? category.trim().toLowerCase(Locale.ROOT) : CATEGORY_ALL;
        if (CATEGORY_ALL.equals(key) || CATEGORY_ALL_NAME.equals(key)) {
            return query;
        }
        if (CATEGORY_OTHER.equals(key) || CATEGORY_OTHER_NAME.equals(key)) {
            return query.notExists("""
                    SELECT 1
                    FROM novel_vip_category_mapping vip_category_mapping
                    JOIN vip_category vip_category
                      ON vip_category.id = vip_category_mapping.vip_category_id
                     AND vip_category.enabled = 1
                    WHERE vip_category_mapping.novel_id = novel.id
                    """);
        }
        try {
            long categoryId = Long.parseLong(key);
            if (categoryId > 0 && validCategoryIds.contains(categoryId)) {
                return query.exists("""
                        SELECT 1
                        FROM novel_vip_category_mapping vip_category_mapping
                        WHERE vip_category_mapping.novel_id = novel.id
                          AND vip_category_mapping.vip_category_id =
                        """ + categoryId);
            }
        } catch (NumberFormatException ignored) {
            // Unknown category keys intentionally produce an empty page.
        }
        return query.apply("1 = 0");
    }

    private Map<Long, VipCategory> validCategories() {
        return vipCategoryMapper.selectList(new LambdaQueryWrapper<VipCategory>()
                        .eq(VipCategory::getEnabled, true)
                        .orderByAsc(VipCategory::getSort)
                        .orderByAsc(VipCategory::getId))
                .stream()
                .filter(category -> category.getId() != null && StringUtils.hasText(category.getName()))
                .collect(Collectors.toMap(VipCategory::getId, category -> category,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private void normalizeVipCategory(Novel novel, Map<Long, VipCategory> categories) {
        NovelVipCategoryMapping mapping = novelVipCategoryMappingMapper.selectOne(new QueryWrapper<NovelVipCategoryMapping>()
                .eq("novel_id", novel.getId())
                .last("LIMIT 1"));
        VipCategory category = mapping == null ? null : categories.get(mapping.getVipCategoryId());
        if (category == null) {
            novel.setCategoryId(null);
            novel.setCategoryName(CATEGORY_OTHER_NAME);
            return;
        }
        novel.setCategoryId(null);
        novel.setCategoryName(category.getName().trim());
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
