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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlerScheduleDispatcherImplTest {

    private final CrawlScheduleMapper scheduleMapper = mock(CrawlScheduleMapper.class);
    private final CrawlTaskRecordMapper taskRecordMapper = mock(CrawlTaskRecordMapper.class);
    private final CrawlMergeTaskMapper mergeTaskMapper = mock(CrawlMergeTaskMapper.class);
    private final CrawlRankSourceMapper rankSourceMapper = mock(CrawlRankSourceMapper.class);
    private final CrawlerSourceConfigMapper sourceMapper = mock(CrawlerSourceConfigMapper.class);
    private final CrawlerExecutionService executionService = mock(CrawlerExecutionService.class);
    private final CrawlerMergeService mergeService = mock(CrawlerMergeService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private final CrawlerScheduleDispatcherImpl dispatcher = new CrawlerScheduleDispatcherImpl(
            scheduleMapper, taskRecordMapper, mergeTaskMapper, rankSourceMapper,
            sourceMapper, executionService, mergeService, jdbcTemplate);

    @Test
    void dispatchesDueSchedulesForMultipleEnabledSources() {
        String currentMinute = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        CrawlSchedule h528 = schedule(21L, 101L, currentMinute);
        CrawlSchedule novel69h = schedule(22L, 102L, currentMinute);
        CrawlerSourceConfig h528Source = source(101L, "h528_authorized", "AUTHORIZED_VIP", true);
        CrawlerSourceConfig novel69hSource = source(102L, "novel69h_authorized", "AUTHORIZED_VIP", true);
        CrawlRankSource h528Rank = rank(201L, 101L, "H528_AUTHORIZED_POSTS");
        CrawlRankSource novel69hRank = rank(202L, 102L, "CATEGORY_AUTHORIZED");

        when(scheduleMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(h528, novel69h));
        when(sourceMapper.selectById(101L)).thenReturn(h528Source);
        when(sourceMapper.selectById(102L)).thenReturn(novel69hSource);
        when(rankSourceMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(h528Rank), List.of(novel69hRank));
        when(taskRecordMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, 0)"), eq(Integer.class), any())).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT RELEASE_LOCK(?)"), eq(Integer.class), any())).thenReturn(1);

        dispatcher.dispatchDueSchedules();

        ArgumentCaptor<CrawlTaskRecord> taskCaptor = ArgumentCaptor.forClass(CrawlTaskRecord.class);
        verify(taskRecordMapper, org.mockito.Mockito.times(2)).insert(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues())
                .extracting(task -> task.sourceId)
                .containsExactly(101L, 102L);
        assertThat(taskCaptor.getAllValues())
                .extracting(task -> task.taskType)
                .containsExactly("AUTHORIZED_VIP", "AUTHORIZED_VIP");
        verify(mergeTaskMapper, never()).insert(any(CrawlMergeTask.class));
        verify(scheduleMapper, org.mockito.Mockito.times(2)).updateById(any(CrawlSchedule.class));
    }

    @Test
    void skipsScheduleWhenSourceIsDisabled() {
        String currentMinute = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        CrawlSchedule schedule = schedule(21L, 101L, currentMinute);

        when(scheduleMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(schedule));
        when(taskRecordMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(sourceMapper.selectById(101L)).thenReturn(source(101L, "h528_authorized", "AUTHORIZED_VIP", false));

        dispatcher.dispatchDueSchedules();

        verify(taskRecordMapper, never()).insert(any(CrawlTaskRecord.class));
        verify(jdbcTemplate, never()).queryForObject(eq("SELECT GET_LOCK(?, 0)"), eq(Integer.class), any());
    }

    @Test
    void runPendingTasksDispatchesDifferentEnabledSources() {
        CrawlTaskRecord h528Task = pendingTask(301L, 101L, 201L);
        CrawlTaskRecord novel69hTask = pendingTask(302L, 102L, 202L);

        when(taskRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>(List.of(h528Task, novel69hTask)));
        when(sourceMapper.selectById(101L)).thenReturn(source(101L, "h528_authorized", "AUTHORIZED_VIP", true));
        when(sourceMapper.selectById(102L)).thenReturn(source(102L, "novel69h_authorized", "AUTHORIZED_VIP", true));
        when(rankSourceMapper.selectById(201L)).thenReturn(rank(201L, 101L, "H528_AUTHORIZED_POSTS"));
        when(rankSourceMapper.selectById(202L)).thenReturn(rank(202L, 102L, "CATEGORY_AUTHORIZED"));
        when(taskRecordMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        dispatcher.runPendingTasks();

        verify(executionService).executeAsync(301L);
        verify(executionService).executeAsync(302L);
    }

    private CrawlSchedule schedule(Long id, Long sourceId, String scheduleTimes) {
        CrawlSchedule schedule = new CrawlSchedule();
        schedule.id = id;
        schedule.sourceId = sourceId;
        schedule.scheduleTimes = scheduleTimes;
        schedule.timezone = "Asia/Shanghai";
        schedule.crawlVip = true;
        schedule.autoMerge = false;
        schedule.enabled = true;
        return schedule;
    }

    private CrawlerSourceConfig source(Long id, String code, String type, boolean enabled) {
        CrawlerSourceConfig source = new CrawlerSourceConfig();
        source.id = id;
        source.sourceCode = code;
        source.sourceType = type;
        source.enabled = enabled;
        return source;
    }

    private CrawlRankSource rank(Long id, Long sourceId, String rankType) {
        CrawlRankSource rank = new CrawlRankSource();
        rank.id = id;
        rank.sourceId = sourceId;
        rank.rankType = rankType;
        rank.rankName = rankType;
        rank.rankUrl = "https://example.test/";
        rank.enabled = true;
        rank.maxBooks = 10;
        return rank;
    }

    private CrawlTaskRecord pendingTask(Long id, Long sourceId, Long rankSourceId) {
        CrawlTaskRecord task = new CrawlTaskRecord();
        task.id = id;
        task.sourceId = sourceId;
        task.rankSourceId = rankSourceId;
        task.taskType = "AUTHORIZED_VIP";
        task.status = "PENDING";
        return task;
    }
}
