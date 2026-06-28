package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.book.entity.Category;
import com.mini.novel.book.mapper.CategoryMapper;
import com.mini.novel.common.result.Result;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/categories")
public class AdminCategoryController {
    private final CategoryMapper categoryMapper;

    public AdminCategoryController(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @GetMapping
    public Result<List<Category>> list() {
        return Result.ok(categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId)));
    }

    @PostMapping
    public Result<Category> create(@RequestBody Category category) {
        category.setSort(category.getSort() == null ? 0 : category.getSort());
        categoryMapper.insert(category);
        return Result.ok(category);
    }

    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable("id") Long id, @RequestBody Category category) {
        category.setId(id);
        categoryMapper.updateById(category);
        return Result.ok(categoryMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        return Result.ok(categoryMapper.deleteById(id) > 0);
    }
}
