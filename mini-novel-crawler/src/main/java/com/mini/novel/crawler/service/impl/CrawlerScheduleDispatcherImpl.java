package com.mini.novel.crawler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mini.novel.crawler.entity.CrawlMergeTask;
import com.mini.novel.crawler.entity.CrawlRankSource;
import com.mini.novel.crawler.entity.CrawlSchedule;
import com.mini.novel.crawler.entity.CrawlTaskRecord;
import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import com.mini.novel.crawler.mapper.CrawlMergeTaskMapper;
import com.mini.novel.crawler.mapper.CrawlRankSourceMapper;
import com.mini.novel.crawler.mapper.CrawlScheduleMapper;
import com.mini.novel.crawler.mapper.CrawlTaskRecordMapper;
import com.mini.novel.crawler.mapper.CrawlerSourceConfigMapper;
import com.mini.novel.crawler.service.CrawlerExecutionService;
import com.mini.novel.crawler.service.CrawlerMergeService;
import com.mini.novel.crawler.service.CrawlerScheduleDispatcher;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CrawlerScheduleDispatcherImpl implements CrawlerScheduleDispatcher {
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final CrawlScheduleMapper scheduleMapper;
    private final CrawlTaskRecordMapper taskRecordMapper;
    private final CrawlMergeTaskMapper mergeTaskMapper;
    private final CrawlRankSourceMapper rankSourceMapper;
    private final CrawlerSourceConfigMapper sourceMapper;
    private final CrawlerExecutionService executionService;
    private final CrawlerMergeService mergeService;
    private final JdbcTemplate jdbcTemplate;

    public CrawlerScheduleDispatcherImpl(CrawlScheduleMapper scheduleMapper,
                                         CrawlTaskRecordMapper taskRecordMapper,
                                         CrawlMergeTaskMapper mergeTaskMapper,
                                         CrawlRankSourceMapper rankSourceMapper,
                                         CrawlerSourceConfigMapper sourceMapper,
                                         CrawlerExecutionService executionService,
                                         CrawlerMergeService mergeService,
                                         JdbcTemplate jdbcTemplate) {
        this.scheduleMapper = scheduleMapper;
        this.taskRecordMapper = taskRecordMapper;
        this.mergeTaskMapper = mergeTaskMapper;
        this.rankSourceMapper = rankSourceMapper;
        this.sourceMapper = sourceMapper;
        this.executionService = executionService;
        this.mergeService = mergeService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${crawler.schedule.fixed-delay-ms:60000}", initialDelayString = "${crawler.schedule.initial-delay-ms:20000}")
    public void scheduledDispatch() {
        dispatchDueSchedules();
        runPendingTasks();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasksOnStartup() {
        LocalDateTime now = LocalDateTime.now();
        List<CrawlTaskRecord> runningTasks = taskRecordMapper.selectList(new QueryWrapper<CrawlTaskRecord>()
                .in("status", List.of("RUNNING"))
                .last("LIMIT 200"));
        for (CrawlTaskRecord task : runningTasks) {
            if ("AUTHORIZED_BOOK_CONTENT".equals(task.taskType)) {
                task.status = "PENDING";
                task.finishedAt = null;
            } else {
                task.status = "FAILED";
                task.finishedAt = now;
            }
            task.updatedAt = now;
            task.message = appendMessage(task.message, "Crawler service restarted; "
                    + ("AUTHORIZED_BOOK_CONTENT".equals(task.taskType)
                    ? "recoverable authorized-content task was returned to PENDING."
                    : "interrupted running task was closed."));
            taskRecordMapper.updateById(task);
        }

        List<CrawlMergeTask> mergingTasks = mergeTaskMapper.selectList(new QueryWrapper<CrawlMergeTask>()
                .in("status", List.of("MERGING"))
                .last("LIMIT 200"));
        for (CrawlMergeTask task : mergingTasks) {
            task.status = "FAILED";
            task.finishedAt = now;
            task.updatedAt = now;
            task.message = appendMessage(task.message, "Crawler service restarted; interrupted merge task was closed.");
            mergeTaskMapper.updateById(task);
        }
    }

    @Override
    public void dispatchDueSchedules() {
        List<CrawlSchedule> schedules = scheduleMapper.selectList(new QueryWrapper<CrawlSchedule>()
                .eq("enabled", true)
                .last("LIMIT 200"));
        for (CrawlSchedule schedule : schedules) {
            DueWindow dueWindow = dueWindow(schedule);
            if (dueWindow == null || !isRunnableSchedule(schedule)) {
                continue;
            }

            String lockName = scheduleLockName(schedule, dueWindow.windowStart());
            if (!tryAcquireScheduleLock(lockName)) {
                continue;
            }
            try {
                int createdCount = 0;
                for (CrawlRankSource rank : loadEnabledRanks(schedule.sourceId)) {
                    if (hasActiveRankTask(schedule.sourceId, rank.id, null)) {
                        continue;
                    }
                    createTask(schedule, rank, "SCHEDULE");
                    createdCount++;
                }
                if (createdCount == 0) {
                    continue;
                }

                schedule.lastRunAt = dueWindow.windowStart();
                schedule.nextRunAt = nextRunAt(schedule, dueWindow.windowStart().plusMinutes(1), dueWindow.zoneId());
                schedule.updatedAt = LocalDateTime.now();
                scheduleMapper.updateById(schedule);
            } finally {
                releaseScheduleLock(lockName);
            }
        }
    }

    @Override
    public void runPendingTasks() {
        List<CrawlTaskRecord> tasks = taskRecordMapper.selectList(new QueryWrapper<CrawlTaskRecord>()
                .eq("status", "PENDING")
                .orderByAsc("id")
                .last("LIMIT 10"));
        Set<Long> dispatchedSourceIds = new HashSet<>();
        for (CrawlTaskRecord task : tasks) {
            if (!isRunnablePendingTask(task)
                    || dispatchedSourceIds.contains(task.sourceId)
                    || hasRunningSourceTask(task.sourceId, task.id)) {
                continue;
            }
            dispatchedSourceIds.add(task.sourceId);
            executionService.executeAsync(task.id);
        }
        mergeService.mergePending();
    }

    private boolean isRunnablePendingTask(CrawlTaskRecord task) {
        if (task == null) {
            return false;
        }
        if ("AUTHORIZED_BOOK_CONTENT".equals(task.taskType)) {
            return true;
        }
        if (task.sourceId == null || task.rankSourceId == null) {
            return false;
        }
        CrawlerSourceConfig source = sourceMapper.selectById(task.sourceId);
        if (!isEnabledSource(source)) {
            return false;
        }
        CrawlRankSource rank = rankSourceMapper.selectById(task.rankSourceId);
        return rank != null
                && task.sourceId.equals(rank.sourceId)
                && Boolean.TRUE.equals(rank.enabled);
    }

    private DueWindow dueWindow(CrawlSchedule schedule) {
        if (!StringUtils.hasText(schedule.scheduleTimes)) {
            return null;
        }
        ZoneId zoneId = ZoneId.of(StringUtils.hasText(schedule.timezone) ? schedule.timezone : "Asia/Shanghai");
        LocalDateTime now = LocalDateTime.now(zoneId).truncatedTo(ChronoUnit.MINUTES);
        if (schedule.nextRunAt != null && schedule.nextRunAt.truncatedTo(ChronoUnit.MINUTES).isAfter(now)) {
            return null;
        }
        String current = now.format(MINUTE_FORMAT);
        boolean timeMatched = Arrays.stream(schedule.scheduleTimes.split(","))
                .map(String::trim)
                .anyMatch(current::equals);
        if (!timeMatched) {
            return null;
        }
        if (schedule.lastRunAt != null && schedule.lastRunAt.truncatedTo(ChronoUnit.MINUTES).equals(now)) {
            return null;
        }
        Long existing = taskRecordMapper.selectCount(new QueryWrapper<CrawlTaskRecord>()
                .eq("schedule_id", schedule.id)
                .eq("trigger_type", "SCHEDULE")
                .ge("created_at", now)
                .lt("created_at", now.plusMinutes(1)));
        return existing == null || existing == 0 ? new DueWindow(now, zoneId) : null;
    }

    private boolean isRunnableSchedule(CrawlSchedule schedule) {
        if (schedule == null || schedule.sourceId == null) {
            return false;
        }
        CrawlerSourceConfig source = sourceMapper.selectById(schedule.sourceId);
        if (!isEnabledSource(source)) {
            return false;
        }
        List<CrawlRankSource> ranks = loadEnabledRanks(schedule.sourceId);
        return !ranks.isEmpty();
    }

    private boolean isEnabledSource(CrawlerSourceConfig source) {
        if (source == null || !Boolean.TRUE.equals(source.enabled)) {
            return false;
        }
        return "PUBLIC".equalsIgnoreCase(source.sourceType)
                || "AUTHORIZED_VIP".equalsIgnoreCase(source.sourceType)
                || !StringUtils.hasText(source.sourceType);
    }

    private List<CrawlRankSource> loadEnabledRanks(Long sourceId) {
        return rankSourceMapper.selectList(new QueryWrapper<CrawlRankSource>()
                .eq("source_id", sourceId)
                .eq("enabled", true)
                .orderByAsc("id")
                .last("LIMIT 100"));
    }

    private boolean hasActiveRankTask(Long sourceId, Long rankSourceId, Long excludedTaskId) {
        if (sourceId == null) {
            return false;
        }
        QueryWrapper<CrawlTaskRecord> wrapper = new QueryWrapper<CrawlTaskRecord>()
                .eq("source_id", sourceId)
                .in("status", List.of("PENDING", "RUNNING"));
        if (rankSourceId == null) {
            wrapper.isNull("rank_source_id");
        } else {
            wrapper.eq("rank_source_id", rankSourceId);
        }
        if (excludedTaskId != null) {
            wrapper.ne("id", excludedTaskId);
        }
        Long count = taskRecordMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private boolean hasRunningSourceTask(Long sourceId, Long excludedTaskId) {
        if (sourceId == null) {
            return false;
        }
        QueryWrapper<CrawlTaskRecord> wrapper = new QueryWrapper<CrawlTaskRecord>()
                .eq("source_id", sourceId)
                .eq("status", "RUNNING");
        if (excludedTaskId != null) {
            wrapper.ne("id", excludedTaskId);
        }
        Long count = taskRecordMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private CrawlTaskRecord createTask(CrawlSchedule schedule, CrawlRankSource rank, String triggerType) {
        LocalDateTime now = LocalDateTime.now();
        CrawlTaskRecord task = new CrawlTaskRecord();
        task.scheduleId = schedule.id;
        task.sourceId = schedule.sourceId;
        task.rankSourceId = rank.id;
        task.credentialId = schedule.credentialId;
        task.taskType = taskType(schedule);
        task.triggerType = triggerType;
        task.status = "PENDING";
        task.targetUrl = rank.rankUrl;
        task.totalCount = 0;
        task.successCount = 0;
        task.failCount = 0;
        task.message = "Rank task queued: " + rankLabel(rank) + ".";
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
            mergeTask.message = "Merge will run when this rank task has ready books.";
            mergeTask.createdAt = now;
            mergeTask.updatedAt = now;
            mergeTaskMapper.insert(mergeTask);
        }
        return task;
    }

    private String taskType(CrawlSchedule schedule) {
        CrawlerSourceConfig source = schedule == null || schedule.sourceId == null ? null : sourceMapper.selectById(schedule.sourceId);
        if (source != null && "AUTHORIZED_VIP".equalsIgnoreCase(source.sourceType)) {
            return "AUTHORIZED_VIP";
        }
        return schedule != null && Boolean.TRUE.equals(schedule.crawlVip) ? "VIP_AND_PUBLIC" : "PUBLIC";
    }

    private String scheduleLockName(CrawlSchedule schedule, LocalDateTime windowStart) {
        return "crawler_schedule:" + schedule.id + ":" + windowStart.truncatedTo(ChronoUnit.MINUTES);
    }

    private boolean tryAcquireScheduleLock(String lockName) {
        Integer locked = jdbcTemplate.queryForObject("SELECT GET_LOCK(?, 0)", Integer.class, lockName);
        return locked != null && locked == 1;
    }

    private void releaseScheduleLock(String lockName) {
        jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName);
    }

    private LocalDateTime nextRunAt(CrawlSchedule schedule, LocalDateTime after, ZoneId zoneId) {
        LocalDateTime cursor = after.truncatedTo(ChronoUnit.MINUTES);
        for (int dayOffset = 0; dayOffset <= 1; dayOffset++) {
            LocalDateTime day = cursor.plusDays(dayOffset).toLocalDate().atStartOfDay();
            LocalDateTime next = Arrays.stream(schedule.scheduleTimes.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(time -> LocalDateTime.parse(day.toLocalDate() + "T" + time + ":00"))
                    .filter(candidate -> !candidate.isBefore(cursor))
                    .sorted()
                    .findFirst()
                    .orElse(null);
            if (next != null) {
                return next;
            }
        }
        return cursor.plusDays(1);
    }

    private String rankLabel(CrawlRankSource rank) {
        if (rank == null) {
            return "unknown rank";
        }
        String type = StringUtils.hasText(rank.rankType) ? rank.rankType : "rank-" + rank.id;
        String name = StringUtils.hasText(rank.rankName) ? rank.rankName : "";
        return name.isEmpty() ? type : type + "/" + name;
    }

    private String appendMessage(String original, String suffix) {
        String value = StringUtils.hasText(original) ? original : "";
        String appended = value + (value.isEmpty() ? "" : " | ") + suffix;
        return appended.length() <= 1000 ? appended : appended.substring(appended.length() - 1000);
    }

    private record DueWindow(LocalDateTime windowStart, ZoneId zoneId) {
    }
}
