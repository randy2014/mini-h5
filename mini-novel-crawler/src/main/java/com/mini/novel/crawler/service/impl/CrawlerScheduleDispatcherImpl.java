package com.mini.novel.crawler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mini.novel.crawler.entity.CrawlMergeTask;
import com.mini.novel.crawler.entity.CrawlSchedule;
import com.mini.novel.crawler.entity.CrawlTaskRecord;
import com.mini.novel.crawler.mapper.CrawlMergeTaskMapper;
import com.mini.novel.crawler.mapper.CrawlScheduleMapper;
import com.mini.novel.crawler.mapper.CrawlTaskRecordMapper;
import com.mini.novel.crawler.service.CrawlerExecutionService;
import com.mini.novel.crawler.service.CrawlerMergeService;
import com.mini.novel.crawler.service.CrawlerScheduleDispatcher;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CrawlerScheduleDispatcherImpl implements CrawlerScheduleDispatcher {
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final CrawlScheduleMapper scheduleMapper;
    private final CrawlTaskRecordMapper taskRecordMapper;
    private final CrawlMergeTaskMapper mergeTaskMapper;
    private final CrawlerExecutionService executionService;
    private final CrawlerMergeService mergeService;

    public CrawlerScheduleDispatcherImpl(CrawlScheduleMapper scheduleMapper,
                                         CrawlTaskRecordMapper taskRecordMapper,
                                         CrawlMergeTaskMapper mergeTaskMapper,
                                         CrawlerExecutionService executionService,
                                         CrawlerMergeService mergeService) {
        this.scheduleMapper = scheduleMapper;
        this.taskRecordMapper = taskRecordMapper;
        this.mergeTaskMapper = mergeTaskMapper;
        this.executionService = executionService;
        this.mergeService = mergeService;
    }

    @Scheduled(fixedDelayString = "${crawler.schedule.fixed-delay-ms:60000}", initialDelayString = "${crawler.schedule.initial-delay-ms:20000}")
    public void scheduledDispatch() {
        dispatchDueSchedules();
        runPendingTasks();
    }

    @Override
    public void dispatchDueSchedules() {
        List<CrawlSchedule> schedules = scheduleMapper.selectList(new QueryWrapper<CrawlSchedule>()
                .eq("enabled", true)
                .last("LIMIT 200"));
        for (CrawlSchedule schedule : schedules) {
            if (!isDue(schedule)) {
                continue;
            }
            CrawlTaskRecord task = createTask(schedule, "SCHEDULE");
            schedule.lastRunAt = LocalDateTime.now();
            schedule.updatedAt = schedule.lastRunAt;
            scheduleMapper.updateById(schedule);
            executionService.executeAsync(task.id);
        }
    }

    @Override
    public void runPendingTasks() {
        List<CrawlTaskRecord> tasks = taskRecordMapper.selectList(new QueryWrapper<CrawlTaskRecord>()
                .eq("status", "PENDING")
                .orderByAsc("id")
                .last("LIMIT 10"));
        for (CrawlTaskRecord task : tasks) {
            executionService.executeAsync(task.id);
        }
        mergeService.mergePending();
    }

    private boolean isDue(CrawlSchedule schedule) {
        if (!StringUtils.hasText(schedule.scheduleTimes)) {
            return false;
        }
        ZoneId zoneId = ZoneId.of(StringUtils.hasText(schedule.timezone) ? schedule.timezone : "Asia/Shanghai");
        LocalDateTime now = LocalDateTime.now(zoneId).truncatedTo(ChronoUnit.MINUTES);
        String current = now.format(MINUTE_FORMAT);
        boolean timeMatched = Arrays.stream(schedule.scheduleTimes.split(","))
                .map(String::trim)
                .anyMatch(current::equals);
        if (!timeMatched) {
            return false;
        }
        if (schedule.lastRunAt != null && schedule.lastRunAt.truncatedTo(ChronoUnit.MINUTES).equals(now)) {
            return false;
        }
        Long existing = taskRecordMapper.selectCount(new QueryWrapper<CrawlTaskRecord>()
                .eq("schedule_id", schedule.id)
                .eq("trigger_type", "SCHEDULE")
                .ge("created_at", now)
                .lt("created_at", now.plusMinutes(1)));
        return existing == null || existing == 0;
    }

    private CrawlTaskRecord createTask(CrawlSchedule schedule, String triggerType) {
        LocalDateTime now = LocalDateTime.now();
        CrawlTaskRecord task = new CrawlTaskRecord();
        task.scheduleId = schedule.id;
        task.sourceId = schedule.sourceId;
        task.credentialId = schedule.credentialId;
        task.taskType = schedule.crawlVip != null && schedule.crawlVip ? "VIP_AND_PUBLIC" : "PUBLIC";
        task.triggerType = triggerType;
        task.status = "PENDING";
        task.totalCount = 0;
        task.successCount = 0;
        task.failCount = 0;
        task.message = "定时调度已创建采集任务，等待执行器处理。";
        task.createdAt = now;
        task.updatedAt = now;
        taskRecordMapper.insert(task);

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
        return task;
    }
}
