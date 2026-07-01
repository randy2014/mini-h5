package com.mini.novel.api.controller;

import com.mini.novel.book.entity.Category;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.service.BookReadService;
import com.mini.novel.common.result.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final BookReadService bookReadService;

    public CategoryController(BookReadService bookReadService) {
        this.bookReadService = bookReadService;
    }

    @GetMapping
    public Result<List<Category>> categories() {
        return Result.ok(bookReadService.listCategories());
    }

    @GetMapping("/{categoryId}/novels")
    public Result<List<Novel>> novels(@PathVariable("categoryId") Long categoryId,
                                      @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return Result.ok(bookReadService.novelsByCategory(categoryId, limit));
    }
}
