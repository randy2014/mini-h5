package com.mini.novel.admin.controller;

import com.mini.novel.common.result.Result;
import com.mini.novel.crawler.entity.CrawlTask;
import com.mini.novel.crawler.mapper.CrawlTaskMapper;
import com.mini.novel.crawler.model.CrawlSubmitRequest;
import com.mini.novel.crawler.service.CrawlerTaskService;
import jakarta.validation.Valid;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/crawl-tasks")
public class AdminCrawlerController {
    private final CrawlerTaskService crawlerTaskService;
    private final CrawlTaskMapper crawlTaskMapper;

    public AdminCrawlerController(CrawlerTaskService crawlerTaskService, CrawlTaskMapper crawlTaskMapper) {
        this.crawlerTaskService = crawlerTaskService;
        this.crawlTaskMapper = crawlTaskMapper;
    }

    @GetMapping
    public Result<List<CrawlTask>> list() {
        return Result.ok(crawlTaskMapper.selectList(new LambdaQueryWrapper<CrawlTask>()
                .orderByDesc(CrawlTask::getCreatedAt)
                .last("LIMIT 200")));
    }

    @GetMapping("/{id}")
    public Result<CrawlTask> detail(@PathVariable("id") Long id) {
        return Result.ok(crawlTaskMapper.selectById(id));
    }

    @PostMapping("/run")
    public Result<CrawlTask> run(@Valid @RequestBody CrawlSubmitRequest request) {
        return Result.ok(crawlerTaskService.submit(request));
    }
}
