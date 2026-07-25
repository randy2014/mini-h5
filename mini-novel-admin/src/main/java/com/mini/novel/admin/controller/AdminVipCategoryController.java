package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mini.novel.book.entity.NovelVipCategoryMapping;
import com.mini.novel.book.entity.VipCategory;
import com.mini.novel.book.entity.VipSourceCategoryMapping;
import com.mini.novel.book.mapper.NovelVipCategoryMappingMapper;
import com.mini.novel.book.mapper.VipCategoryMapper;
import com.mini.novel.book.mapper.VipSourceCategoryMappingMapper;
import com.mini.novel.common.result.Result;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/vip-categories")
public class AdminVipCategoryController {
    private final VipCategoryMapper categoryMapper;
    private final VipSourceCategoryMappingMapper sourceMappingMapper;
    private final NovelVipCategoryMappingMapper novelCategoryMappingMapper;

    public AdminVipCategoryController(VipCategoryMapper categoryMapper,
                                      VipSourceCategoryMappingMapper sourceMappingMapper,
                                      NovelVipCategoryMappingMapper novelCategoryMappingMapper) {
        this.categoryMapper = categoryMapper;
        this.sourceMappingMapper = sourceMappingMapper;
        this.novelCategoryMappingMapper = novelCategoryMappingMapper;
    }

    @GetMapping
    public Result<List<VipCategory>> list() {
        return Result.ok(categoryMapper.selectList(new LambdaQueryWrapper<VipCategory>()
                .orderByAsc(VipCategory::getSort)
                .orderByAsc(VipCategory::getId)));
    }

    @PostMapping
    public Result<VipCategory> create(@RequestBody VipCategory category) {
        prepare(category);
        if (Boolean.TRUE.equals(category.getIsDefault())) {
            clearDefault(null);
        }
        categoryMapper.insert(category);
        return Result.ok(category);
    }

    @PutMapping("/{id}")
    public Result<VipCategory> update(@PathVariable Long id, @RequestBody VipCategory category) {
        VipCategory existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("VIP category does not exist.");
        }
        category.setId(id);
        prepare(category);
        protectDefaultChange(existing, category);
        if (Boolean.TRUE.equals(category.getIsDefault())) {
            clearDefault(id);
        }
        categoryMapper.updateById(category);
        return Result.ok(categoryMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        VipCategory existing = categoryMapper.selectById(id);
        if (existing == null) {
            return Result.ok(false);
        }
        if (Boolean.TRUE.equals(existing.getIsDefault())) {
            throw new IllegalArgumentException("Default VIP category cannot be deleted.");
        }
        Long novels = novelCategoryMappingMapper.selectCount(new QueryWrapper<NovelVipCategoryMapping>()
                .eq("vip_category_id", id));
        if (novels != null && novels > 0) {
            throw new IllegalArgumentException("VIP category is used by published novels and cannot be deleted.");
        }
        Long sourceMappings = sourceMappingMapper.selectCount(new QueryWrapper<VipSourceCategoryMapping>()
                .eq("vip_category_id", id));
        if (sourceMappings != null && sourceMappings > 0) {
            throw new IllegalArgumentException("VIP category is used by source mappings and cannot be deleted.");
        }
        return Result.ok(categoryMapper.deleteById(id) > 0);
    }

    @GetMapping("/source-mappings")
    public Result<List<VipSourceCategoryMapping>> sourceMappings(@RequestParam(required = false) String sourceCode) {
        LambdaQueryWrapper<VipSourceCategoryMapping> query = new LambdaQueryWrapper<VipSourceCategoryMapping>()
                .orderByAsc(VipSourceCategoryMapping::getSourceCode)
                .orderByAsc(VipSourceCategoryMapping::getSourceCategoryName);
        if (StringUtils.hasText(sourceCode)) {
            query.eq(VipSourceCategoryMapping::getSourceCode, sourceCode);
        }
        return Result.ok(sourceMappingMapper.selectList(query));
    }

    @PostMapping("/source-mappings")
    public Result<VipSourceCategoryMapping> saveSourceMapping(@RequestBody VipSourceCategoryMapping mapping) {
        mapping.setSourceCategoryName(StringUtils.hasText(mapping.getSourceCategoryName())
                ? mapping.getSourceCategoryName().trim() : "");
        mapping.setNormalizedName(normalize(mapping.getSourceCategoryName()));
        mapping.setEnabled(mapping.getEnabled() == null || mapping.getEnabled());
        VipCategory target = categoryMapper.selectById(mapping.getVipCategoryId());
        if (target == null || !Boolean.TRUE.equals(target.getEnabled())) {
            throw new IllegalArgumentException("Source mapping must point to an enabled VIP category.");
        }
        if (mapping.getId() == null) {
            VipSourceCategoryMapping existing = sourceMappingMapper.selectOne(new QueryWrapper<VipSourceCategoryMapping>()
                    .eq("source_code", mapping.getSourceCode())
                    .eq("normalized_name", mapping.getNormalizedName())
                    .last("LIMIT 1"));
            if (existing == null) {
                sourceMappingMapper.insert(mapping);
            } else {
                mapping.setId(existing.getId());
                sourceMappingMapper.updateById(mapping);
            }
        } else {
            sourceMappingMapper.updateById(mapping);
        }
        return Result.ok(mapping);
    }

    private void prepare(VipCategory category) {
        if (!StringUtils.hasText(category.getName())) {
            throw new IllegalArgumentException("VIP category name is required.");
        }
        category.setName(category.getName().trim());
        category.setNormalizedName(normalize(category.getName()));
        category.setSort(category.getSort() == null ? 100 : category.getSort());
        category.setEnabled(category.getEnabled() == null || category.getEnabled());
        category.setIsDefault(category.getIsDefault() != null && category.getIsDefault());
    }

    private void protectDefaultChange(VipCategory existing, VipCategory incoming) {
        if (!Boolean.TRUE.equals(existing.getIsDefault())) {
            return;
        }
        if (!Boolean.TRUE.equals(incoming.getEnabled())) {
            throw new IllegalArgumentException("Default VIP category cannot be disabled.");
        }
        if (!Boolean.TRUE.equals(incoming.getIsDefault())) {
            throw new IllegalArgumentException("Default VIP category cannot be unset before another default is chosen.");
        }
    }

    private void clearDefault(Long keepId) {
        List<VipCategory> defaults = categoryMapper.selectList(new LambdaQueryWrapper<VipCategory>()
                .eq(VipCategory::getIsDefault, true));
        LocalDateTime now = LocalDateTime.now();
        for (VipCategory category : defaults) {
            if (keepId != null && Objects.equals(keepId, category.getId())) {
                continue;
            }
            category.setIsDefault(false);
            category.setUpdatedAt(now);
            categoryMapper.updateById(category);
        }
    }

    private String normalize(String value) {
        String name = StringUtils.hasText(value) ? value.trim() : "";
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "");
        return StringUtils.hasText(normalized) ? normalized : "";
    }
}
