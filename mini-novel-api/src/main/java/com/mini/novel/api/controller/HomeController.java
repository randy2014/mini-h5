package com.mini.novel.api.controller;

import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.service.BookReadService;
import com.mini.novel.common.result.Result;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @GetMapping("/sections")
    public Result<Map<String, List<Novel>>> sections() {
        Map<String, List<Novel>> data = new LinkedHashMap<>();
        data.put("hot", bookReadService.rankNovels("HOT", 12));
        data.put("completed", bookReadService.rankNovels("COMPLETED", 8));
        data.put("latest", bookReadService.rankNovels("LATEST", 12));
        data.put("long", bookReadService.rankNovels("LONG", 8));
        return Result.ok(data);
    }
}
