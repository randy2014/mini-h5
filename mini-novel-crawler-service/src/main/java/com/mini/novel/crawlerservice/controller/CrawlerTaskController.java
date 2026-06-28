package com.mini.novel.crawlerservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.crawler.entity.CrawlTask;
import com.mini.novel.crawler.mapper.CrawlTaskMapper;
import com.mini.novel.crawler.model.CrawlSubmitRequest;
import com.mini.novel.crawler.service.CrawlerTaskService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/crawler/tasks")
public class CrawlerTaskController {
    private final CrawlTaskMapper crawlTaskMapper;
    private final CrawlerTaskService crawlerTaskService;

    public CrawlerTaskController(CrawlTaskMapper crawlTaskMapper, CrawlerTaskService crawlerTaskService) {
        this.crawlTaskMapper = crawlTaskMapper;
        this.crawlerTaskService = crawlerTaskService;
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

    @PostMapping
    public Result<CrawlTask> run(@Valid @RequestBody CrawlSubmitRequest request) {
        return Result.ok(crawlerTaskService.submit(request));
    }

    @PostMapping("/{id}/retry")
    public Result<CrawlTask> retry(@PathVariable("id") Long id) {
        CrawlTask task = crawlTaskMapper.selectById(id);
        CrawlSubmitRequest request = new CrawlSubmitRequest();
        request.setSourceId(task.getSourceId());
        request.setNovelId(task.getNovelId());
        request.setSeedUrl(null);
        return Result.ok(crawlerTaskService.submit(request));
    }
}
