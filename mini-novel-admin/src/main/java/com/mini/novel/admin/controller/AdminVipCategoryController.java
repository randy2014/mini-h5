package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mini.novel.book.entity.VipCategory;
import com.mini.novel.book.entity.VipSourceCategoryMapping;
import com.mini.novel.book.mapper.VipCategoryMapper;
import com.mini.novel.book.mapper.VipSourceCategoryMappingMapper;
import com.mini.novel.common.result.Result;
import java.util.List;
import java.util.Locale;
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

    public AdminVipCategoryController(VipCategoryMapper categoryMapper,
                                      VipSourceCategoryMappingMapper sourceMappingMapper) {
        this.categoryMapper = categoryMapper;
        this.sourceMappingMapper = sourceMappingMapper;
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
        categoryMapper.insert(category);
        return Result.ok(category);
    }

    @PutMapping("/{id}")
    public Result<VipCategory> update(@PathVariable Long id, @RequestBody VipCategory category) {
        category.setId(id);
        prepare(category);
        categoryMapper.updateById(category);
        return Result.ok(categoryMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
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
        mapping.setNormalizedName(normalize(mapping.getSourceCategoryName()));
        mapping.setEnabled(mapping.getEnabled() == null || mapping.getEnabled());
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
    }

    private String normalize(String value) {
        String name = StringUtils.hasText(value) ? value.trim() : "\u5176\u4ed6";
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "");
        return StringUtils.hasText(normalized) ? normalized : "other";
    }
}
