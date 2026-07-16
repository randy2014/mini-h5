package com.mini.novel.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.api.model.VipBookCategoryVo;
import com.mini.novel.api.model.VipBookPageVo;
import com.mini.novel.api.model.VipStatusVo;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.api.support.VipPublicationProgress;
import com.mini.novel.common.result.Result;
import com.mini.novel.book.entity.Category;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.mapper.CategoryMapper;
import com.mini.novel.book.mapper.NovelMapper;
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
    static final String CATEGORY_OTHER_NAME = "其他";
    private final VipPlanMapper vipPlanMapper;
    private final CurrentUserResolver currentUserResolver;
    private final VipAccessService vipAccessService;
    private final NovelMapper novelMapper;
    private final CategoryMapper categoryMapper;
    private final VipPublicationProgress publicationProgress;

    public VipController(VipPlanMapper vipPlanMapper, CurrentUserResolver currentUserResolver, VipAccessService vipAccessService,
                         NovelMapper novelMapper, CategoryMapper categoryMapper, VipPublicationProgress publicationProgress) {
        this.vipPlanMapper = vipPlanMapper;
        this.currentUserResolver = currentUserResolver;
        this.vipAccessService = vipAccessService;
        this.novelMapper = novelMapper;
        this.categoryMapper = categoryMapper;
        this.publicationProgress = publicationProgress;
    }

    @GetMapping("/books")
    public Result<VipBookPageVo> books(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(value = "category", defaultValue = CATEGORY_ALL) String category) {
        long safePage = Math.max(1, page);
        long safePageSize = Math.max(1, Math.min(100, pageSize));
        Map<Long, Category> categories = validCategories();
        LambdaQueryWrapper<Novel> query = vipBooksQuery(category, categories.keySet())
                .orderByDesc(Novel::getUpdatedAt)
                .orderByDesc(Novel::getId);
        Page<Novel> result = novelMapper.selectPage(new Page<>(safePage, safePageSize), query);
        result.getRecords().forEach(novel -> {
            normalizeCategory(novel, categories);
            publicationProgress.enrich(novel);
        });
        return Result.ok(VipBookPageVo.from(result));
    }

    @GetMapping("/categories")
    public Result<List<VipBookCategoryVo>> categories() {
        Map<Long, Category> categories = validCategories();
        long total = novelMapper.selectCount(vipBooksQuery(CATEGORY_ALL, categories.keySet()));
        List<VipBookCategoryVo> result = new java.util.ArrayList<>();
        result.add(new VipBookCategoryVo(CATEGORY_ALL, null, "全部", total));
        categories.values().forEach(category -> {
            long count = novelMapper.selectCount(vipBooksQuery(String.valueOf(category.getId()), categories.keySet()));
            if (count > 0) {
                result.add(new VipBookCategoryVo(String.valueOf(category.getId()), category.getId(), category.getName().trim(), count));
            }
        });
        long otherCount = novelMapper.selectCount(vipBooksQuery(CATEGORY_OTHER, categories.keySet()));
        if (otherCount > 0) {
            result.add(new VipBookCategoryVo(CATEGORY_OTHER, null, CATEGORY_OTHER_NAME, otherCount));
        }
        return Result.ok(result);
    }

    LambdaQueryWrapper<Novel> vipBooksQuery(String category, Set<Long> validCategoryIds) {
        LambdaQueryWrapper<Novel> query = new LambdaQueryWrapper<Novel>()
                .ne(Novel::getStatus, 0)
                .eq(Novel::getVipRequired, true)
                .exists("""
                        SELECT 1
                        FROM novel_source_mapping vip_mapping
                        JOIN mini_novel_crawler.crawl_source vip_source
                          ON vip_source.source_code = vip_mapping.source_code
                         AND vip_source.source_type = 'AUTHORIZED_VIP'
                        WHERE vip_mapping.novel_id = novel.id
                          AND vip_mapping.content_status = 'CONTENT_READY'
                        """)
                .exists("SELECT 1 FROM chapter vip_chapter WHERE vip_chapter.novel_id = novel.id");
        String key = StringUtils.hasText(category) ? category.trim().toLowerCase(Locale.ROOT) : CATEGORY_ALL;
        if (CATEGORY_ALL.equals(key) || "全部".equals(key)) {
            return query;
        }
        if (CATEGORY_OTHER.equals(key) || CATEGORY_OTHER_NAME.equals(key)) {
            return query.and(nested -> {
                nested.isNull(Novel::getCategoryId);
                if (!validCategoryIds.isEmpty()) {
                    nested.or().notIn(Novel::getCategoryId, validCategoryIds);
                }
            });
        }
        try {
            long categoryId = Long.parseLong(key);
            if (categoryId > 0 && validCategoryIds.contains(categoryId)) {
                return query.eq(Novel::getCategoryId, categoryId);
            }
        } catch (NumberFormatException ignored) {
            // Unknown category keys intentionally produce an empty page.
        }
        return query.apply("1 = 0");
    }

    private Map<Long, Category> validCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSort)
                        .orderByAsc(Category::getId))
                .stream()
                .filter(category -> category.getId() != null && StringUtils.hasText(category.getName()))
                .collect(Collectors.toMap(Category::getId, category -> category, (left, right) -> left, LinkedHashMap::new));
    }

    private void normalizeCategory(Novel novel, Map<Long, Category> categories) {
        Category category = categories.get(novel.getCategoryId());
        if (category == null) {
            novel.setCategoryId(null);
            novel.setCategoryName(CATEGORY_OTHER_NAME);
            return;
        }
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
