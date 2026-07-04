package com.mini.novel.crawlerservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.crawler.entity.CrawlMergeTask;
import com.mini.novel.crawler.entity.CrawlMergeItem;
import com.mini.novel.crawler.entity.CrawlBookRaw;
import com.mini.novel.crawler.entity.CrawlChapterRaw;
import com.mini.novel.crawler.entity.CrawlContentRaw;
import com.mini.novel.crawler.entity.CrawlRankSource;
import com.mini.novel.crawler.entity.CrawlSchedule;
import com.mini.novel.crawler.entity.CrawlSourceCredential;
import com.mini.novel.crawler.entity.CrawlTaskRecord;
import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import com.mini.novel.crawler.mapper.CrawlMergeTaskMapper;
import com.mini.novel.crawler.mapper.CrawlMergeItemMapper;
import com.mini.novel.crawler.mapper.CrawlBookRawMapper;
import com.mini.novel.crawler.mapper.CrawlChapterRawMapper;
import com.mini.novel.crawler.mapper.CrawlContentRawMapper;
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
    private static final String PRIMARY_SOURCE_CODE = "23qb_public";

    private final CrawlerSourceConfigMapper sourceMapper;
    private final CrawlRankSourceMapper rankSourceMapper;
    private final CrawlScheduleMapper scheduleMapper;
    private final CrawlSourceCredentialMapper credentialMapper;
    private final CrawlTaskRecordMapper taskRecordMapper;
    private final CrawlMergeTaskMapper mergeTaskMapper;
    private final CrawlMergeItemMapper mergeItemMapper;
    private final CrawlBookRawMapper bookRawMapper;
    private final CrawlChapterRawMapper chapterRawMapper;
    private final CrawlContentRawMapper contentRawMapper;
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
                                   CrawlChapterRawMapper chapterRawMapper,
                                   CrawlContentRawMapper contentRawMapper,
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
        this.chapterRawMapper = chapterRawMapper;
        this.contentRawMapper = contentRawMapper;
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
            return new Result<>(404, "Crawler credential does not exist.", null);
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
    public Result<CrawlTaskRecord> runNow(@PathVariable Long id,
                                          @RequestParam(required = false) Long rankSourceId,
                                          @RequestParam(required = false) String rankType,
                                          @RequestParam(required = false) Integer maxBooks) {
        CrawlSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            return new Result<>(404, "Crawler schedule does not exist.", null);
        }
        CrawlerSourceConfig source = sourceMapper.selectById(schedule.sourceId);
        if (!isPrimarySource(source)) {
            return new Result<>(400, "Only 23qb_public is enabled as the stable crawler source.", null);
        }
        List<CrawlTaskRecord> createdTasks = createRankTasks(schedule, rankSourceId, rankType, maxBooks, "MANUAL");
        if (createdTasks.isEmpty()) {
            return new Result<>(409, "No rank task was created; matched ranks may already be pending or running.", null);
        }
        if (!hasRunningTaskForSource(schedule.sourceId)) {
            crawlerExecutionService.executeAsync(createdTasks.get(0).id);
        }
        return Result.ok(createdTasks.get(0));
    }


    @PostMapping("/tasks/{id}/run")
    public Result<Void> runTask(@PathVariable Long id) {
        CrawlTaskRecord task = taskRecordMapper.selectById(id);
        if (task == null) {
            return new Result<>(404, "Crawler task does not exist.", null);
        }
        crawlerExecutionService.executeAsync(id);
        return Result.ok();
    }

    @PostMapping("/tasks/{id}/interrupt")
    public Result<CrawlTaskRecord> interruptTask(@PathVariable Long id) {
        CrawlTaskRecord task = taskRecordMapper.selectById(id);
        if (task == null) {
            return new Result<>(404, "Crawler task does not exist.", null);
        }
        if (!List.of("PENDING", "RUNNING").contains(task.status)) {
            return Result.ok(task);
        }
        LocalDateTime now = LocalDateTime.now();
        task.status = "FAILED";
        task.finishedAt = now;
        task.updatedAt = now;
        task.message = (task.message == null ? "" : task.message)
                + " | Marked interrupted from admin console at " + now + ".";
        taskRecordMapper.updateById(task);
        return Result.ok(task);
    }

    @GetMapping("/tasks")
    public Result<List<CrawlTaskRecord>> tasks() {
        return Result.ok(taskRecordMapper.selectList(new QueryWrapper<CrawlTaskRecord>().orderByDesc("id").last("LIMIT 100")));
    }

    @GetMapping("/tasks/{id}/books")
    public Result<List<Map<String, Object>>> taskBooks(@PathVariable Long id) {
        List<CrawlBookRaw> books = bookRawMapper.selectList(new QueryWrapper<CrawlBookRaw>()
                .eq("crawl_task_id", id)
                .orderByDesc("id")
                .last("LIMIT 500"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CrawlBookRaw book : books) {
            Long chapterCount = chapterRawMapper.selectCount(new QueryWrapper<CrawlChapterRaw>()
                    .eq("book_raw_id", book.id));
            Long readyChapterCount = chapterRawMapper.selectCount(new QueryWrapper<CrawlChapterRaw>()
                    .eq("book_raw_id", book.id)
                    .eq("content_status", "CONTENT_READY"));
            Long contentCount = contentRawMapper.selectCount(new QueryWrapper<CrawlContentRaw>()
                    .inSql("chapter_raw_id", "SELECT id FROM mini_novel_crawler.crawl_chapter_raw WHERE book_raw_id = " + book.id)
                    .gt("content_length", 0));
            Integer minNo = chapterRawMapper.selectList(new QueryWrapper<CrawlChapterRaw>()
                            .select("chapter_no")
                            .eq("book_raw_id", book.id)
                            .orderByAsc("chapter_no")
                            .last("LIMIT 1"))
                    .stream()
                    .findFirst()
                    .map(row -> row.chapterNo)
                    .orElse(0);
            Integer maxNo = chapterRawMapper.selectList(new QueryWrapper<CrawlChapterRaw>()
                            .select("chapter_no")
                            .eq("book_raw_id", book.id)
                            .orderByDesc("chapter_no")
                            .last("LIMIT 1"))
                    .stream()
                    .findFirst()
                    .map(row -> row.chapterNo)
                    .orElse(0);
            long gapCount = Math.max(0L, (long) maxNo - minNo + 1L - (chapterCount == null ? 0L : chapterCount));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", book.id);
            row.put("title", book.title);
            row.put("author", book.author);
            row.put("bookStatus", book.bookStatus);
            row.put("contentStatus", book.contentStatus);
            row.put("chapterCount", chapterCount == null ? 0L : chapterCount);
            row.put("readyChapterCount", readyChapterCount == null ? 0L : readyChapterCount);
            row.put("contentCount", contentCount == null ? 0L : contentCount);
            row.put("minChapterNo", minNo);
            row.put("maxChapterNo", maxNo);
            row.put("gapCount", gapCount);
            row.put("sourceUrl", book.sourceUrl);
            row.put("crawledAt", book.crawledAt);
            rows.add(row);
        }
        return Result.ok(rows);
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

    private boolean isPrimarySource(CrawlerSourceConfig source) {
        return source != null
                && Boolean.TRUE.equals(source.enabled)
                && PRIMARY_SOURCE_CODE.equals(source.sourceCode);
    }

    private List<CrawlTaskRecord> createRankTasks(CrawlSchedule schedule, Long rankSourceId, String rankType,
                                                  Integer maxBooks, String triggerType) {
        List<CrawlRankSource> ranks = runnableRanks(schedule.sourceId, rankSourceId, rankType);
        LocalDateTime now = LocalDateTime.now();
        List<CrawlTaskRecord> createdTasks = new ArrayList<>();
        for (CrawlRankSource rank : ranks) {
            if (activeTaskForRank(schedule.sourceId, rank.id) != null) {
                continue;
            }
            CrawlTaskRecord task = new CrawlTaskRecord();
            task.scheduleId = schedule.id;
            task.sourceId = schedule.sourceId;
            task.rankSourceId = rank.id;
            task.credentialId = schedule.credentialId;
            task.taskType = schedule.crawlVip != null && schedule.crawlVip ? "VIP_AND_PUBLIC" : "PUBLIC";
            task.triggerType = triggerType;
            task.status = "PENDING";
            task.targetUrl = taskTargetUrl(rank, maxBooks);
            task.totalCount = 0;
            task.successCount = 0;
            task.failCount = 0;
            task.message = "Rank task queued: " + rankLabel(rank) + ".";
            task.createdAt = now;
            task.updatedAt = now;
            taskRecordMapper.insert(task);
            createdTasks.add(task);

            if (schedule.autoMerge == null || schedule.autoMerge) {
                CrawlMergeTask mergeTask = new CrawlMergeTask();
                mergeTask.crawlTaskId = task.id;
                mergeTask.status = "PENDING";
                mergeTask.totalCount = 0;
                mergeTask.mergedCount = 0;
                mergeTask.pendingReviewCount = 0;
                mergeTask.failedCount = 0;
                mergeTask.message = "Merge will run when this rank task has ready books.";
                mergeTask.createdAt = now;
                mergeTask.updatedAt = now;
                mergeTaskMapper.insert(mergeTask);
            }
        }
        if (!createdTasks.isEmpty()) {
            schedule.lastRunAt = now;
            schedule.updatedAt = now;
            scheduleMapper.updateById(schedule);
        }
        return createdTasks;
    }

    private String taskTargetUrl(CrawlRankSource rank, Integer maxBooks) {
        if (rank == null || !StringUtils.hasText(rank.rankUrl)) {
            return null;
        }
        Integer scopedMaxBooks = scopedMaxBooks(maxBooks);
        return scopedMaxBooks == null ? rank.rankUrl : rank.rankUrl + "#maxBooks=" + scopedMaxBooks;
    }

    private Integer scopedMaxBooks(Integer maxBooks) {
        if (maxBooks == null) {
            return null;
        }
        return Math.max(1, Math.min(maxBooks, 100));
    }

    private List<CrawlRankSource> runnableRanks(Long sourceId, Long rankSourceId, String rankType) {
        QueryWrapper<CrawlRankSource> wrapper = new QueryWrapper<CrawlRankSource>()
                .eq("source_id", sourceId)
                .eq("enabled", true)
                .orderByAsc("id");
        if (rankSourceId != null) {
            wrapper.eq("id", rankSourceId);
        }
        if (StringUtils.hasText(rankType)) {
            wrapper.eq("rank_type", rankType.trim());
        }
        wrapper.last("LIMIT 100");
        return rankSourceMapper.selectList(wrapper);
    }

    private CrawlTaskRecord activeTaskForRank(Long sourceId, Long rankSourceId) {
        if (sourceId == null || rankSourceId == null) {
            return null;
        }
        return taskRecordMapper.selectOne(new QueryWrapper<CrawlTaskRecord>()
                .eq("source_id", sourceId)
                .eq("rank_source_id", rankSourceId)
                .in("status", List.of("PENDING", "RUNNING"))
                .orderByDesc("id")
                .last("LIMIT 1"));
    }

    private boolean hasRunningTaskForSource(Long sourceId) {
        if (sourceId == null) {
            return false;
        }
        Long count = taskRecordMapper.selectCount(new QueryWrapper<CrawlTaskRecord>()
                .eq("source_id", sourceId)
                .eq("status", "RUNNING"));
        return count != null && count > 0;
    }

    private String rankLabel(CrawlRankSource rank) {
        if (rank == null) {
            return "unknown rank";
        }
        String type = StringUtils.hasText(rank.rankType) ? rank.rankType : "rank-" + rank.id;
        String name = StringUtils.hasText(rank.rankName) ? rank.rankName : "";
        return name.isEmpty() ? type : type + "/" + name;
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
            credential.name = "Crawler credential";
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
