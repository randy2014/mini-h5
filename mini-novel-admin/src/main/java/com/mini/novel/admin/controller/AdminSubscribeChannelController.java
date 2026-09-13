package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.entity.SubscribeChannel;
import com.mini.novel.book.entity.SubscribeChannelNovel;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.book.mapper.SubscribeChannelMapper;
import com.mini.novel.book.mapper.SubscribeChannelNovelMapper;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.media.entity.MediaPost;
import com.mini.novel.media.mapper.MediaPostMapper;
import com.mini.novel.media.service.MediaPostService;
import com.mini.novel.vip.entity.UserSubscribe;
import com.mini.novel.vip.mapper.UserSubscribeMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/subscribe-channels")
public class AdminSubscribeChannelController {
    /** 单次批量加入上限（文章管理列表接口本身 LIMIT 200）。 */
    private static final int MAX_BATCH_SIZE = 200;

    private final SubscribeChannelMapper channelMapper;
    private final SubscribeChannelNovelMapper channelNovelMapper;
    private final UserSubscribeMapper subscribeMapper;
    private final NovelMapper novelMapper;
    private final MediaPostMapper mediaPostMapper;
    private final MediaPostService mediaPostService;

    public AdminSubscribeChannelController(SubscribeChannelMapper channelMapper,
                                           SubscribeChannelNovelMapper channelNovelMapper,
                                           UserSubscribeMapper subscribeMapper,
                                           NovelMapper novelMapper,
                                           MediaPostMapper mediaPostMapper,
                                           MediaPostService mediaPostService) {
        this.channelMapper = channelMapper;
        this.channelNovelMapper = channelNovelMapper;
        this.subscribeMapper = subscribeMapper;
        this.novelMapper = novelMapper;
        this.mediaPostMapper = mediaPostMapper;
        this.mediaPostService = mediaPostService;
    }

    /** 频道列表（含内容统计：小说数 + 已发布多媒体帖数）。 */
    @GetMapping
    public Result<List<SubscribeChannel>> list() {
        return Result.ok(withCounts(channelMapper.selectList(new LambdaQueryWrapper<SubscribeChannel>()
                .orderByAsc(SubscribeChannel::getSort)
                .orderByAsc(SubscribeChannel::getId))));
    }

    /** 频道详情（基本信息 + 内容统计），供「查看频道详情」页头部使用。 */
    @GetMapping("/{id}/detail")
    public Result<SubscribeChannel> detail(@PathVariable Long id) {
        return Result.ok(withCounts(List.of(require(id))).get(0));
    }

    /**
     * 频道内小说（后台视角：含已下架小说，前端用状态标签区分）。
     * 关系表按加入时间倒序取出后组装：一次关系查询 + 一次小说批量查询，避免 join 手写 SQL。
     */
    @GetMapping("/{id}/novels")
    public Result<ChannelNovelPage> novels(@PathVariable Long id,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long pageSize) {
        require(id);
        List<SubscribeChannelNovel> links = channelNovelMapper.selectList(
                new LambdaQueryWrapper<SubscribeChannelNovel>()
                        .eq(SubscribeChannelNovel::getChannelId, id)
                        .orderByDesc(SubscribeChannelNovel::getId));
        if (links.isEmpty()) {
            return Result.ok(new ChannelNovelPage(0, Math.max(1, page), Math.min(100, Math.max(1, pageSize)),
                    new ArrayList<>()));
        }
        Set<Long> novelIds = links.stream().map(SubscribeChannelNovel::getNovelId).collect(Collectors.toSet());
        Map<Long, Novel> novelMap = novelMapper.selectBatchIds(novelIds).stream()
                .collect(Collectors.toMap(Novel::getId, n -> n, (l, r) -> l));

        // 加入顺序倒序；小说已被物理删除的脏关系直接跳过
        Map<Long, LocalDateTime> joinedAt = new LinkedHashMap<>();
        links.forEach(link -> joinedAt.putIfAbsent(link.getNovelId(), link.getCreatedAt()));

        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<ChannelNovelItem> items = new ArrayList<>();
        for (Map.Entry<Long, LocalDateTime> entry : joinedAt.entrySet()) {
            Novel novel = novelMap.get(entry.getKey());
            if (novel == null) {
                continue;
            }
            if (kw != null && !contains(novel.getTitle(), kw) && !contains(novel.getAuthor(), kw)) {
                continue;
            }
            items.add(new ChannelNovelItem(novel.getId(), novel.getTitle(), novel.getAuthor(), novel.getCoverUrl(),
                    novel.getCategoryId(), novel.getStatus(), novel.getVipRequired(), novel.getWordCount(),
                    novel.getLatestChapterTitle(), entry.getValue(), novel.getUpdatedAt()));
        }
        long safeSize = Math.min(100, Math.max(1, pageSize));
        long safePage = Math.max(1, page);
        int from = (int) Math.min(items.size(), (safePage - 1) * safeSize);
        int to = (int) Math.min(items.size(), from + safeSize);
        return Result.ok(new ChannelNovelPage(items.size(), safePage, safeSize, items.subList(from, to)));
    }

    /** 频道内已发布多媒体帖（含封面与素材，按发布时间倒序）。 */
    @GetMapping("/{id}/posts")
    public Result<Page<MediaPostService.MediaPostDetail>> posts(@PathVariable Long id,
                                                               @RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "20") long pageSize) {
        require(id);
        return Result.ok(mediaPostService.pageChannelPublished(id, Math.max(1, page),
                Math.min(100, Math.max(1, pageSize))));
    }

    @PostMapping
    public Result<SubscribeChannel> create(@RequestBody SubscribeChannel channel) {
        prepare(channel);
        channelMapper.insert(channel);
        return Result.ok(channel);
    }

    @PutMapping("/{id}")
    public Result<SubscribeChannel> update(@PathVariable Long id, @RequestBody SubscribeChannel channel) {
        require(id);
        channel.setId(id);
        prepare(channel);
        channelMapper.updateById(channel);
        return Result.ok(channelMapper.selectById(id));
    }

    @PutMapping("/{id}/publish")
    public Result<SubscribeChannel> publish(@PathVariable Long id) {
        SubscribeChannel existing = require(id);
        existing.setStatus(SubscribeChannel.STATUS_PUBLISHED);
        existing.setUpdatedAt(LocalDateTime.now());
        channelMapper.updateById(existing);
        return Result.ok(existing);
    }

    @PutMapping("/{id}/offline")
    public Result<SubscribeChannel> offline(@PathVariable Long id) {
        SubscribeChannel existing = require(id);
        Long active = subscribeMapper.selectCount(new LambdaQueryWrapper<UserSubscribe>()
                .eq(UserSubscribe::getChannelId, id)
                .eq(UserSubscribe::getStatus, UserSubscribe.STATUS_ACTIVE)
                .gt(UserSubscribe::getEndTime, LocalDateTime.now()));
        if (active != null && active > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该频道还有未时效的订阅，不能下架");
        }
        existing.setStatus(SubscribeChannel.STATUS_OFFLINE);
        existing.setUpdatedAt(LocalDateTime.now());
        channelMapper.updateById(existing);
        return Result.ok(existing);
    }

    /**
     * 小说已加入的频道 id 列表（后台弹窗用于把「已加入」的频道置为已加入态，避免重复提交）。
     */
    @GetMapping("/novels/{novelId}")
    public Result<List<Long>> channelsOfNovel(@PathVariable Long novelId) {
        return Result.ok(channelNovelMapper.selectList(new LambdaQueryWrapper<SubscribeChannelNovel>()
                        .eq(SubscribeChannelNovel::getNovelId, novelId))
                .stream()
                .map(SubscribeChannelNovel::getChannelId)
                .distinct()
                .toList());
    }

    /**
     * 小说加入频道：幂等语义——重复加入直接返回既有关系，不再让唯一键 uk_channel_novel 抛 500。
     */
    @PostMapping("/{channelId}/novels")
    public Result<SubscribeChannelNovel> addNovel(@PathVariable Long channelId,
                                                  @RequestBody AddNovelRequest request) {
        require(channelId);
        if (request.novelId() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "小说 id 必填");
        }
        if (novelMapper.selectById(request.novelId()) == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "小说不存在");
        }
        SubscribeChannelNovel existing = channelNovelMapper.selectOne(new LambdaQueryWrapper<SubscribeChannelNovel>()
                .eq(SubscribeChannelNovel::getChannelId, channelId)
                .eq(SubscribeChannelNovel::getNovelId, request.novelId())
                .last("limit 1"));
        if (existing != null) {
            return Result.ok(existing);
        }
        SubscribeChannelNovel link = new SubscribeChannelNovel();
        link.setChannelId(channelId);
        link.setNovelId(request.novelId());
        link.setOperatorId(request.operatorId());
        link.setCreatedAt(LocalDateTime.now());
        channelNovelMapper.insert(link);
        return Result.ok(link);
    }

    /**
     * 批量加入频道（文章管理多选后一次提交）：整批幂等——
     * 已在该频道的小说计入 skipped，不存在的小说计入 notFound，其余插入。
     */
    @PostMapping("/{channelId}/novels/batch")
    @Transactional
    public Result<BatchJoinResult> addNovelsBatch(@PathVariable Long channelId,
                                                  @RequestBody BatchAddNovelRequest request) {
        require(channelId);
        List<Long> ids = request.novelIds() == null ? List.of()
                : request.novelIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请选择要加入的小说");
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "单次最多加入 " + MAX_BATCH_SIZE + " 本小说");
        }
        Set<Long> existingNovelIds = novelMapper.selectBatchIds(ids).stream()
                .map(Novel::getId)
                .collect(Collectors.toSet());
        int notFound = (int) ids.stream().filter(id -> !existingNovelIds.contains(id)).count();

        Set<Long> linkedNovelIds = existingNovelIds.isEmpty() ? Set.of()
                : channelNovelMapper.selectList(new LambdaQueryWrapper<SubscribeChannelNovel>()
                        .eq(SubscribeChannelNovel::getChannelId, channelId)
                        .in(SubscribeChannelNovel::getNovelId, existingNovelIds))
                .stream()
                .map(SubscribeChannelNovel::getNovelId)
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        int added = 0;
        for (Long novelId : ids) {
            if (!existingNovelIds.contains(novelId) || linkedNovelIds.contains(novelId)) {
                continue;
            }
            SubscribeChannelNovel link = new SubscribeChannelNovel();
            link.setChannelId(channelId);
            link.setNovelId(novelId);
            link.setOperatorId(request.operatorId());
            link.setCreatedAt(now);
            channelNovelMapper.insert(link);
            added++;
        }
        int skipped = ids.size() - added - notFound;
        return Result.ok(new BatchJoinResult(ids.size(), added, skipped, notFound));
    }

    @DeleteMapping("/{channelId}/novels/{novelId}")
    public Result<Boolean> removeNovel(@PathVariable Long channelId, @PathVariable Long novelId) {
        channelNovelMapper.delete(new LambdaQueryWrapper<SubscribeChannelNovel>()
                .eq(SubscribeChannelNovel::getChannelId, channelId)
                .eq(SubscribeChannelNovel::getNovelId, novelId));
        return Result.ok(true);
    }

    /** 补内容统计（小说数 + 已发布多媒体帖数）；一次 IN 查询取回，避免 N+1。 */
    private List<SubscribeChannel> withCounts(List<SubscribeChannel> channels) {
        if (channels.isEmpty()) {
            return channels;
        }
        List<Long> ids = channels.stream().map(SubscribeChannel::getId).toList();
        Map<Long, Long> novelCounts = new LinkedHashMap<>();
        channelNovelMapper.selectList(new LambdaQueryWrapper<SubscribeChannelNovel>()
                        .in(SubscribeChannelNovel::getChannelId, ids))
                .forEach(link -> novelCounts.merge(link.getChannelId(), 1L, Long::sum));
        Map<Long, Long> mediaCounts = new LinkedHashMap<>();
        mediaPostMapper.selectList(new LambdaQueryWrapper<MediaPost>()
                        .in(MediaPost::getChannelId, ids)
                        .eq(MediaPost::getStatus, MediaPost.STATUS_PUBLISHED))
                .forEach(post -> mediaCounts.merge(post.getChannelId(), 1L, Long::sum));
        for (SubscribeChannel channel : channels) {
            channel.setNovelCount(novelCounts.getOrDefault(channel.getId(), 0L));
            channel.setMediaCount(mediaCounts.getOrDefault(channel.getId(), 0L));
        }
        return channels;
    }

    private static boolean contains(String source, String keyword) {
        return source != null && source.contains(keyword);
    }

    private void prepare(SubscribeChannel channel) {
        if (!StringUtils.hasText(channel.getName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "频道名称必填");
        }
        channel.setName(channel.getName().trim());
        channel.setSort(channel.getSort() == null ? 100 : channel.getSort());
        if (!StringUtils.hasText(channel.getStatus())) {
            channel.setStatus(SubscribeChannel.STATUS_OFFLINE);
        }
    }

    private SubscribeChannel require(Long id) {
        SubscribeChannel existing = channelMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "频道不存在");
        }
        return existing;
    }

    public record AddNovelRequest(Long novelId, Long operatorId) {
    }

    public record BatchAddNovelRequest(List<Long> novelIds, Long operatorId) {
    }

    /** requested=提交总数，added=新增，skipped=已在该频道，notFound=小说不存在。 */
    public record BatchJoinResult(int requested, int added, int skipped, int notFound) {
    }

    /** 频道内小说条目（含加入频道时间）。 */
    public record ChannelNovelItem(Long id, String title, String author, String coverUrl, Long categoryId,
                                   Integer status, Boolean vipRequired, Long wordCount, String latestChapterTitle,
                                   LocalDateTime joinedAt, LocalDateTime updatedAt) {
    }

    public record ChannelNovelPage(long total, long page, long pageSize, List<ChannelNovelItem> records) {
    }
}
