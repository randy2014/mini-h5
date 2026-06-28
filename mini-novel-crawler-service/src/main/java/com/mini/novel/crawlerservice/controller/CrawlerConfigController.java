package com.mini.novel.crawlerservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.crawler.entity.CrawlMergeTask;
import com.mini.novel.crawler.entity.CrawlMergeItem;
import com.mini.novel.crawler.entity.CrawlBookRaw;
import com.mini.novel.crawler.entity.CrawlRankSource;
import com.mini.novel.crawler.entity.CrawlSchedule;
import com.mini.novel.crawler.entity.CrawlSourceCredential;
import com.mini.novel.crawler.entity.CrawlTaskRecord;
import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import com.mini.novel.crawler.mapper.CrawlMergeTaskMapper;
import com.mini.novel.crawler.mapper.CrawlMergeItemMapper;
import com.mini.novel.crawler.mapper.CrawlBookRawMapper;
import com.mini.novel.crawler.mapper.CrawlRankSourceMapper;
import com.mini.novel.crawler.mapper.CrawlScheduleMapper;
import com.mini.novel.crawler.mapper.CrawlSourceCredentialMapper;
import com.mini.novel.crawler.mapper.CrawlTaskRecordMapper;
import com.mini.novel.crawler.mapper.CrawlerSourceConfigMapper;
import com.mini.novel.crawler.service.CrawlerExecutionService;
import com.mini.novel.crawler.service.CrawlerMergeService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/crawler/config")
public class CrawlerConfigController {
    private final CrawlerSourceConfigMapper sourceMapper;
    private final CrawlRankSourceMapper rankSourceMapper;
    private final CrawlScheduleMapper scheduleMapper;
    private final CrawlSourceCredentialMapper credentialMapper;
    private final CrawlTaskRecordMapper taskRecordMapper;
    private final CrawlMergeTaskMapper mergeTaskMapper;
    private final CrawlMergeItemMapper mergeItemMapper;
    private final CrawlBookRawMapper bookRawMapper;
    private final CrawlerExecutionService crawlerExecutionService;
    private final CrawlerMergeService crawlerMergeService;

    public CrawlerConfigController(CrawlerSourceConfigMapper sourceMapper,
                                   CrawlRankSourceMapper rankSourceMapper,
                                   CrawlScheduleMapper scheduleMapper,
                                   CrawlSourceCredentialMapper credentialMapper,
                                   CrawlTaskRecordMapper taskRecordMapper,
                                   CrawlMergeTaskMapper mergeTaskMapper,
                                   CrawlMergeItemMapper mergeItemMapper,
                                   CrawlBookRawMapper bookRawMapper,
                                   CrawlerExecutionService crawlerExecutionService,
                                   CrawlerMergeService crawlerMergeService) {
        this.sourceMapper = sourceMapper;
        this.rankSourceMapper = rankSourceMapper;
        this.scheduleMapper = scheduleMapper;
        this.credentialMapper = credentialMapper;
        this.taskRecordMapper = taskRecordMapper;
        this.mergeTaskMapper = mergeTaskMapper;
        this.mergeItemMapper = mergeItemMapper;
        this.bookRawMapper = bookRawMapper;
        this.crawlerExecutionService = crawlerExecutionService;
        this.crawlerMergeService = crawlerMergeService;
    }

    @GetMapping("/credentials")
    public Result<List<CrawlSourceCredential>> credentials() {
        List<CrawlSourceCredential> rows = credentialMapper.selectList(new QueryWrapper<CrawlSourceCredential>()
                .orderByDesc("id")
                .last("LIMIT 200"));
        rows.forEach(this::maskCredential);
        return Result.ok(rows);
    }

    @PostMapping("/credentials")
    public Result<CrawlSourceCredential> createCredential(@RequestBody CrawlSourceCredential credential) {
        normalizeCredential(credential, null);
        credential.createdAt = LocalDateTime.now();
        credential.updatedAt = credential.createdAt;
        credentialMapper.insert(credential);
        maskCredential(credential);
        return Result.ok(credential);
    }

    @PutMapping("/credentials/{id}")
    public Result<CrawlSourceCredential> updateCredential(@PathVariable Long id, @RequestBody CrawlSourceCredential credential) {
        CrawlSourceCredential existing = credentialMapper.selectById(id);
        if (existing == null) {
            return new Result<>(404, "采集账号不存在", null);
        }
        credential.id = id;
        normalizeCredential(credential, existing);
        credential.updatedAt = LocalDateTime.now();
        credentialMapper.updateById(credential);
        CrawlSourceCredential saved = credentialMapper.selectById(id);
        maskCredential(saved);
        return Result.ok(saved);
    }

    @GetMapping("/sources")
    public Result<List<CrawlerSourceConfig>> sources() {
        return Result.ok(sourceMapper.selectList(new QueryWrapper<CrawlerSourceConfig>().orderByAsc("priority", "id")));
    }

    @PostMapping("/sources")
    public Result<CrawlerSourceConfig> createSource(@RequestBody CrawlerSourceConfig source) {
        normalizeSource(source);
        source.createdAt = LocalDateTime.now();
        source.updatedAt = source.createdAt;
        sourceMapper.insert(source);
        return Result.ok(source);
    }

    @PutMapping("/sources/{id}")
    public Result<CrawlerSourceConfig> updateSource(@PathVariable Long id, @RequestBody CrawlerSourceConfig source) {
        source.id = id;
        normalizeSource(source);
        source.updatedAt = LocalDateTime.now();
        sourceMapper.updateById(source);
        return Result.ok(sourceMapper.selectById(id));
    }

    @GetMapping("/rank-sources")
    public Result<List<CrawlRankSource>> rankSources() {
        return Result.ok(rankSourceMapper.selectList(new QueryWrapper<CrawlRankSource>().orderByDesc("id").last("LIMIT 200")));
    }

    @PostMapping("/rank-sources")
    public Result<CrawlRankSource> createRankSource(@RequestBody CrawlRankSource rankSource) {
        normalizeRankSource(rankSource);
        rankSource.createdAt = LocalDateTime.now();
        rankSource.updatedAt = rankSource.createdAt;
        rankSourceMapper.insert(rankSource);
        return Result.ok(rankSource);
    }

    @PutMapping("/rank-sources/{id}")
    public Result<CrawlRankSource> updateRankSource(@PathVariable Long id, @RequestBody CrawlRankSource rankSource) {
        rankSource.id = id;
        normalizeRankSource(rankSource);
        rankSource.updatedAt = LocalDateTime.now();
        rankSourceMapper.updateById(rankSource);
        return Result.ok(rankSourceMapper.selectById(id));
    }

    @GetMapping("/schedules")
    public Result<List<CrawlSchedule>> schedules() {
        return Result.ok(scheduleMapper.selectList(new QueryWrapper<CrawlSchedule>().orderByDesc("id").last("LIMIT 100")));
    }

    @PostMapping("/schedules")
    public Result<CrawlSchedule> createSchedule(@RequestBody CrawlSchedule schedule) {
        normalizeSchedule(schedule);
        schedule.createdAt = LocalDateTime.now();
        schedule.updatedAt = schedule.createdAt;
        scheduleMapper.insert(schedule);
        return Result.ok(schedule);
    }

    @PutMapping("/schedules/{id}")
    public Result<CrawlSchedule> updateSchedule(@PathVariable Long id, @RequestBody CrawlSchedule schedule) {
        schedule.id = id;
        normalizeSchedule(schedule);
        schedule.updatedAt = LocalDateTime.now();
        scheduleMapper.updateById(schedule);
        return Result.ok(scheduleMapper.selectById(id));
    }

    @PostMapping("/schedules/{id}/run-now")
    public Result<CrawlTaskRecord> runNow(@PathVariable Long id) {
        CrawlSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            return new Result<>(404, "采集计划不存在", null);
        }

        LocalDateTime now = LocalDateTime.now();
        CrawlTaskRecord task = new CrawlTaskRecord();
        task.scheduleId = schedule.id;
        task.sourceId = schedule.sourceId;
        task.credentialId = schedule.credentialId;
        task.taskType = schedule.crawlVip != null && schedule.crawlVip ? "VIP_AND_PUBLIC" : "PUBLIC";
        task.triggerType = "MANUAL";
        task.status = "PENDING";
        task.totalCount = 0;
        task.successCount = 0;
        task.failCount = 0;
        task.message = "任务已创建，采集执行器即将拉取榜单、章节目录和公开正文。";
        task.createdAt = now;
        task.updatedAt = now;
        taskRecordMapper.insert(task);

        schedule.lastRunAt = now;
        schedule.updatedAt = now;
        scheduleMapper.updateById(schedule);

        if (schedule.autoMerge == null || schedule.autoMerge) {
            CrawlMergeTask mergeTask = new CrawlMergeTask();
            mergeTask.crawlTaskId = task.id;
            mergeTask.status = "PENDING";
            mergeTask.totalCount = 0;
            mergeTask.mergedCount = 0;
            mergeTask.pendingReviewCount = 0;
            mergeTask.failedCount = 0;
            mergeTask.message = "采集完成后自动进入清洗入库队列。";
            mergeTask.createdAt = now;
            mergeTask.updatedAt = now;
            mergeTaskMapper.insert(mergeTask);
        }

        crawlerExecutionService.executeAsync(task.id);
        return Result.ok(task);
    }

    @PostMapping("/tasks/{id}/run")
    public Result<Void> runTask(@PathVariable Long id) {
        CrawlTaskRecord task = taskRecordMapper.selectById(id);
        if (task == null) {
            return new Result<>(404, "采集任务不存在", null);
        }
        crawlerExecutionService.executeAsync(id);
        return Result.ok();
    }

    @GetMapping("/tasks")
    public Result<List<CrawlTaskRecord>> tasks() {
        return Result.ok(taskRecordMapper.selectList(new QueryWrapper<CrawlTaskRecord>().orderByDesc("id").last("LIMIT 100")));
    }

    @GetMapping("/merge-tasks")
    public Result<List<CrawlMergeTask>> mergeTasks() {
        return Result.ok(mergeTaskMapper.selectList(new QueryWrapper<CrawlMergeTask>().orderByDesc("id").last("LIMIT 100")));
    }

    @GetMapping("/merge-items")
    public Result<List<Map<String, Object>>> mergeItems(@RequestParam(required = false) String status) {
        QueryWrapper<CrawlMergeItem> wrapper = new QueryWrapper<CrawlMergeItem>().orderByDesc("id").last("LIMIT 300");
        if (StringUtils.hasText(status)) {
            wrapper.eq("match_status", status);
        }
        List<CrawlMergeItem> items = mergeItemMapper.selectList(wrapper);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CrawlMergeItem item : items) {
            CrawlBookRaw book = item.bookRawId == null ? null : bookRawMapper.selectById(item.bookRawId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.id);
            row.put("mergeTaskId", item.mergeTaskId);
            row.put("bookRawId", item.bookRawId);
            row.put("identityId", item.identityId);
            row.put("novelId", item.novelId);
            row.put("matchStatus", item.matchStatus);
            row.put("confidenceScore", item.confidenceScore);
            row.put("message", item.message);
            row.put("sourceCode", book == null ? null : book.sourceCode);
            row.put("sourceBookId", book == null ? null : book.sourceBookId);
            row.put("title", book == null ? null : book.title);
            row.put("author", book == null ? null : book.author);
            row.put("sourceUrl", book == null ? null : book.sourceUrl);
            row.put("contentStatus", book == null ? null : book.contentStatus);
            row.put("updatedAt", item.updatedAt);
            rows.add(row);
        }
        return Result.ok(rows);
    }

    @PostMapping("/merge-tasks/run-pending")
    public Result<Void> runPendingMergeTasks() {
        crawlerMergeService.mergePending();
        return Result.ok();
    }

    @PostMapping("/merge-items/{id}/retry")
    public Result<Void> retryMergeItem(@PathVariable Long id) {
        crawlerMergeService.retryMergeItem(id);
        return Result.ok();
    }

    @PostMapping("/merge-items/{id}/ignore")
    public Result<Void> ignoreMergeItem(@PathVariable Long id, @RequestBody(required = false) Map<String, String> payload) {
        String reason = payload == null ? null : payload.get("reason");
        crawlerMergeService.ignoreMergeItem(id, reason);
        return Result.ok();
    }

    private void normalizeSource(CrawlerSourceConfig source) {
        if (!StringUtils.hasText(source.sourceCode)) {
            source.sourceCode = "custom_" + System.currentTimeMillis();
        }
        if (!StringUtils.hasText(source.sourceType)) {
            source.sourceType = "PUBLIC";
        }
        if (!StringUtils.hasText(source.authMode)) {
            source.authMode = "NONE";
        }
        if (source.enabled == null) {
            source.enabled = true;
        }
        if (source.priority == null) {
            source.priority = 100;
        }
    }

    private void normalizeRankSource(CrawlRankSource rankSource) {
        if (!StringUtils.hasText(rankSource.rankType)) {
            rankSource.rankType = "HOT";
        }
        if (rankSource.preferCompleted == null) {
            rankSource.preferCompleted = true;
        }
        if (rankSource.maxBooks == null || rankSource.maxBooks <= 0) {
            rankSource.maxBooks = 50;
        }
        if (rankSource.enabled == null) {
            rankSource.enabled = true;
        }
    }

    private void normalizeSchedule(CrawlSchedule schedule) {
        if (!StringUtils.hasText(schedule.scheduleTimes)) {
            schedule.scheduleTimes = "00:00,08:00,14:00";
        }
        if (!StringUtils.hasText(schedule.timezone)) {
            schedule.timezone = "Asia/Shanghai";
        }
        if (schedule.crawlPublic == null) {
            schedule.crawlPublic = true;
        }
        if (schedule.crawlVip == null) {
            schedule.crawlVip = false;
        }
        if (schedule.autoMerge == null) {
            schedule.autoMerge = true;
        }
        if (schedule.enabled == null) {
            schedule.enabled = true;
        }
    }

    private void normalizeCredential(CrawlSourceCredential credential, CrawlSourceCredential existing) {
        if (!StringUtils.hasText(credential.name)) {
            credential.name = "采集账号";
        }
        if (!StringUtils.hasText(credential.authMode)) {
            credential.authMode = "PASSWORD";
        }
        if (!StringUtils.hasText(credential.status)) {
            credential.status = "UNVERIFIED";
        }
        if (credential.enabled == null) {
            credential.enabled = true;
        }
        if ("__KEEP__".equals(credential.passwordCipher) && existing != null) {
            credential.passwordCipher = existing.passwordCipher;
        }
        if ("__KEEP__".equals(credential.cookieText) && existing != null) {
            credential.cookieText = existing.cookieText;
        }
        if (!StringUtils.hasText(credential.passwordCipher) && existing != null) {
            credential.passwordCipher = existing.passwordCipher;
        }
        if (!StringUtils.hasText(credential.cookieText) && existing != null) {
            credential.cookieText = existing.cookieText;
        }
    }

    private void maskCredential(CrawlSourceCredential credential) {
        if (credential == null) {
            return;
        }
        if (StringUtils.hasText(credential.passwordCipher)) {
            credential.passwordCipher = "__KEEP__";
        }
        if (StringUtils.hasText(credential.cookieText)) {
            credential.cookieText = "__KEEP__";
        }
    }
}
