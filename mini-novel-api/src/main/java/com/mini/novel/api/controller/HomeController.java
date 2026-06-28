package com.mini.novel.api.controller;

import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.service.BookReadService;
import com.mini.novel.common.result.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeController {
    private final BookReadService bookReadService;

    public HomeController(BookReadService bookReadService) {
        this.bookReadService = bookReadService;
    }

    @GetMapping
    public Result<List<Novel>> home() {
        return Result.ok(bookReadService.latestNovels(20));
    }
}
