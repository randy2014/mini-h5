package com.mini.novel.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.api.model.ChannelFeedVo;
import com.mini.novel.api.service.ChannelFeedService;
import com.mini.novel.api.service.SubscribeService;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.entity.SubscribeChannelNovel;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.book.mapper.SubscribeChannelNovelMapper;
import com.mini.novel.media.entity.MediaAsset;
import com.mini.novel.media.entity.MediaPost;
import com.mini.novel.media.entity.MediaPostAsset;
import com.mini.novel.media.mapper.MediaAssetMapper;
import com.mini.novel.media.mapper.MediaPostAssetMapper;
import com.mini.novel.media.mapper.MediaPostMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 频道统一内容流实现：合并「小说（加入频道）」与「多媒体帖（已发布）」为单一时间倒序流。
 * 未订阅：restricted=true，媒体内容不返回（无未订阅预览），小说沿用受限第一页语义。
 */
@Service
public class ChannelFeedServiceImpl implements ChannelFeedService {

    private final SubscribeService subscribeService;
    private final SubscribeChannelNovelMapper channelNovelMapper;
    private final NovelMapper novelMapper;
    private final MediaPostMapper mediaPostMapper;
    private final MediaPostAssetMapper postAssetMapper;
    private final MediaAssetMapper assetMapper;

    public ChannelFeedServiceImpl(SubscribeService subscribeService,
                                  SubscribeChannelNovelMapper channelNovelMapper,
                                  NovelMapper novelMapper,
                                  MediaPostMapper mediaPostMapper,
                                  MediaPostAssetMapper postAssetMapper,
                                  MediaAssetMapper assetMapper) {
        this.subscribeService = subscribeService;
        this.channelNovelMapper = channelNovelMapper;
        this.novelMapper = novelMapper;
        this.mediaPostMapper = mediaPostMapper;
        this.postAssetMapper = postAssetMapper;
        this.assetMapper = assetMapper;
    }

    @Override
    public ChannelFeedVo feed(Long userId, Long channelId, long page, long pageSize) {
        boolean accessible = subscribeService.isAccessible(userId, channelId);
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, pageSize));
        ChannelFeedVo vo = new ChannelFeedVo();
        vo.setRestricted(!accessible);

        List<ChannelFeedVo.FeedItem> all = new ArrayList<>();
        if (accessible) {
            all.addAll(collectMedia(channelId));
        }
        all.addAll(collectNovels(channelId));
        // 统一时间倒序
        all.sort(Comparator.comparing(ChannelFeedVo.FeedItem::getSortTime,
                Comparator.nullsFirst(Comparator.reverseOrder())));
        vo.setTotal(all.size());
        int from = (int) Math.min(all.size(), (safePage - 1) * safeSize);
        int to = (int) Math.min(all.size(), from + safeSize);
        vo.setRecords(all.subList(from, to));
        return vo;
    }

    /** 小说：subscribe_channel_novel 关联 + novel(status!=0)，时间=加入频道时间。 */
    private List<ChannelFeedVo.FeedItem> collectNovels(Long channelId) {
        List<SubscribeChannelNovel> links = channelNovelMapper.selectList(new LambdaQueryWrapper<SubscribeChannelNovel>()
                .eq(SubscribeChannelNovel::getChannelId, channelId));
        if (links.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> ids = links.stream().map(SubscribeChannelNovel::getNovelId).collect(Collectors.toSet());
        Map<Long, Novel> novels = novelMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Novel::getId, Function.identity(), (l, r) -> l));
        List<ChannelFeedVo.FeedItem> out = new ArrayList<>();
        for (SubscribeChannelNovel link : links) {
            Novel novel = novels.get(link.getNovelId());
            if (novel == null || (novel.getStatus() != null && novel.getStatus() == 0)) {
                continue;
            }
            ChannelFeedVo.FeedItem item = new ChannelFeedVo.FeedItem();
            item.setKind("NOVEL");
            item.setId(novel.getId());
            item.setTitle(novel.getTitle());
            item.setAuthor(novel.getAuthor());
            item.setCoverKind("novel");
            item.setSortTime(link.getCreatedAt() == null ? LocalDateTime.now() : link.getCreatedAt());
            out.add(item);
        }
        return out;
    }

    /** 媒体：频道已发布帖（published_at 倒序）+ 有序素材统计与封面。 */
    private List<ChannelFeedVo.FeedItem> collectMedia(Long channelId) {
        List<MediaPost> posts = mediaPostMapper.selectList(new LambdaQueryWrapper<MediaPost>()
                .eq(MediaPost::getChannelId, channelId)
                .eq(MediaPost::getStatus, MediaPost.STATUS_PUBLISHED)
                .orderByDesc(MediaPost::getPublishedAt));
        if (posts.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> postIds = posts.stream().map(MediaPost::getId).collect(Collectors.toSet());
        List<MediaPostAsset> allLinks = postIds.isEmpty() ? new ArrayList<>()
                : postAssetMapper.selectList(new LambdaQueryWrapper<MediaPostAsset>()
                        .in(MediaPostAsset::getPostId, postIds));
        Map<Long, List<MediaPostAsset>> linksByPost = allLinks.stream()
                .collect(Collectors.groupingBy(MediaPostAsset::getPostId));
        Set<Long> assetIds = allLinks.stream().map(MediaPostAsset::getAssetId).collect(Collectors.toSet());
        Map<Long, MediaAsset> assets = assetIds.isEmpty() ? Map.of()
                : assetMapper.selectBatchIds(assetIds).stream()
                        .collect(Collectors.toMap(MediaAsset::getId, Function.identity(), (l, r) -> l));

        List<ChannelFeedVo.FeedItem> out = new ArrayList<>();
        for (MediaPost post : posts) {
            List<MediaPostAsset> postLinks = linksByPost.getOrDefault(post.getId(), List.of())
                    .stream().sorted(Comparator.comparing(MediaPostAsset::getSeq)).toList();
            MediaAsset cover = null;
            if (post.getCoverAssetId() != null) {
                cover = assets.get(post.getCoverAssetId());
            }
            if (cover == null && !postLinks.isEmpty()) {
                cover = assets.get(postLinks.get(0).getAssetId());
            }
            ChannelFeedVo.FeedItem item = new ChannelFeedVo.FeedItem();
            item.setKind(post.getType()); // IMAGE/VIDEO/MIXED
            item.setId(post.getId());
            item.setTitle(post.getTitle());
            item.setCoverKind(cover != null && MediaAsset.TYPE_VIDEO.equals(cover.getFileType()) ? "poster" : "thumb");
            item.setCoverAssetId(cover == null ? null : cover.getId());
            item.setVideoDurationMs(cover != null && MediaAsset.TYPE_VIDEO.equals(cover.getFileType())
                    ? cover.getDurationMs() : null);
            long imageCount = postLinks.stream()
                    .filter(l -> { MediaAsset a = assets.get(l.getAssetId()); return a != null && MediaAsset.TYPE_IMAGE.equals(a.getFileType()); })
                    .count();
            long videoCount = postLinks.stream()
                    .filter(l -> { MediaAsset a = assets.get(l.getAssetId()); return a != null && MediaAsset.TYPE_VIDEO.equals(a.getFileType()); })
                    .count();
            item.setImageCount((int) imageCount);
            item.setVideoCount((int) videoCount);
            item.setSortTime(post.getPublishedAt() == null ? post.getUpdatedAt() : post.getPublishedAt());
            out.add(item);
        }
        return out;
    }
}
