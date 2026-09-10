package com.mini.novel.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.media.entity.MediaAsset;
import com.mini.novel.media.entity.MediaPost;
import com.mini.novel.media.entity.MediaPostAsset;
import com.mini.novel.media.mapper.MediaAssetMapper;
import com.mini.novel.media.mapper.MediaPostAssetMapper;
import com.mini.novel.media.mapper.MediaPostMapper;
import com.mini.novel.media.support.MediaFileStorage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 内容帖领域服务实现。规则见 docs/media-pool-design.md D3-D5：
 * - 编辑/删除仅 DRAFT；PUBLISHED 先下架；
 * - 发布校验：频道(调用方校验) + 标题 + ≥1 素材 + 素材 READY + 视频 ≤1；
 * - 删除帖解引用，素材无任何引用时物理删除。
 */
@Service
public class MediaPostServiceImpl implements MediaPostService {

    private final MediaPostMapper postMapper;
    private final MediaPostAssetMapper postAssetMapper;
    private final MediaAssetMapper assetMapper;
    private final MediaFileStorage storage;

    public MediaPostServiceImpl(MediaPostMapper postMapper,
                                MediaPostAssetMapper postAssetMapper,
                                MediaAssetMapper assetMapper,
                                MediaFileStorage storage) {
        this.postMapper = postMapper;
        this.postAssetMapper = postAssetMapper;
        this.assetMapper = assetMapper;
        this.storage = storage;
    }

    @Override
    @Transactional
    public MediaPost createDraft(String title, List<Long> assetIds, Long operatorId) {
        requireTitle(title);
        MediaPost post = new MediaPost();
        post.setTitle(title.trim());
        post.setType(MediaPost.TYPE_IMAGE);
        post.setStatus(MediaPost.STATUS_DRAFT);
        post.setOperatorId(operatorId);
        post.setCreatedAt(LocalDateTime.now());
        postMapper.insert(post);
        replaceAssets(post.getId(), assetIds);
        // 自动判定类型与封面
        syncTypeAndCover(post.getId());
        return postMapper.selectById(post.getId());
    }

    @Override
    @Transactional
    public MediaPost updateDraft(Long postId, String title, List<Long> assetIds, Long operatorId) {
        MediaPost post = require(postId);
        requireDraft(post);
        requireTitle(title);
        post.setTitle(title.trim());
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(post);
        replaceAssets(postId, assetIds);
        syncTypeAndCover(postId);
        return postMapper.selectById(postId);
    }

    @Override
    @Transactional
    public MediaPost publish(Long postId, Long channelId, Long operatorId) {
        MediaPost post = require(postId);
        requireDraft(post);
        if (channelId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "发布必须选择挂载频道");
        }
        if (!StringUtils.hasText(post.getTitle())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "标题必填");
        }
        List<MediaAsset> assets = orderedAssets(postId);
        if (assets.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "内容至少需要一个图片或视频素材");
        }
        long videoCount = assets.stream().filter(a -> MediaAsset.TYPE_VIDEO.equals(a.getFileType())).count();
        if (videoCount > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "每条内容最多 1 个视频");
        }
        boolean allReady = assets.stream().allMatch(a -> MediaAsset.STATUS_READY.equals(a.getStatus()));
        if (!allReady) {
            MediaAsset pending = assets.stream()
                    .filter(a -> !MediaAsset.STATUS_READY.equals(a.getStatus())).findFirst().orElse(null);
            String why = pending == null ? "" : "（" + pending.getOriginalName() + " 转码中或失败）";
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "素材未就绪，暂不能发布" + why);
        }
        post.setChannelId(channelId);
        post.setStatus(MediaPost.STATUS_PUBLISHED);
        post.setPublishedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(post);
        syncTypeAndCover(postId);
        return postMapper.selectById(postId);
    }

    @Override
    @Transactional
    public MediaPost unpublish(Long postId) {
        MediaPost post = require(postId);
        if (!MediaPost.STATUS_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅已发布内容可下架");
        }
        post.setStatus(MediaPost.STATUS_DRAFT);
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(post);
        return post;
    }

    @Override
    @Transactional
    public void deleteDraft(Long postId) {
        MediaPost post = require(postId);
        requireDraft(post);
        List<MediaPostAsset> links = postAssetMapper.selectList(
                new LambdaQueryWrapper<MediaPostAsset>().eq(MediaPostAsset::getPostId, postId));
        postAssetMapper.delete(new LambdaQueryWrapper<MediaPostAsset>().eq(MediaPostAsset::getPostId, postId));
        postMapper.deleteById(postId);
        for (MediaPostAsset link : links) {
            releaseAssetIfOrphan(link.getAssetId());
        }
    }

    @Override
    public Page<MediaPost> pageDrafts(long page, long pageSize) {
        return postMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<MediaPost>()
                        .eq(MediaPost::getStatus, MediaPost.STATUS_DRAFT)
                        .orderByDesc(MediaPost::getId));
    }

    @Override
    public Page<MediaPost> pagePublished(Long channelId, long page, long pageSize) {
        LambdaQueryWrapper<MediaPost> w = new LambdaQueryWrapper<MediaPost>()
                .eq(MediaPost::getStatus, MediaPost.STATUS_PUBLISHED);
        if (channelId != null) {
            w.eq(MediaPost::getChannelId, channelId);
        }
        w.orderByDesc(MediaPost::getPublishedAt).orderByDesc(MediaPost::getId);
        return postMapper.selectPage(new Page<>(page, pageSize), w);
    }

    @Override
    public MediaPostDetail getDetail(Long postId) {
        MediaPost post = require(postId);
        List<MediaAsset> assets = orderedAssets(postId);
        return new MediaPostDetail(post, assets, resolveCover(post, assets));
    }

    @Override
    public Page<MediaPostDetail> pageChannelPublished(Long channelId, long page, long pageSize) {
        Page<MediaPost> p = postMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<MediaPost>()
                        .eq(MediaPost::getChannelId, channelId)
                        .eq(MediaPost::getStatus, MediaPost.STATUS_PUBLISHED)
                        .orderByDesc(MediaPost::getPublishedAt).orderByDesc(MediaPost::getId));
        List<MediaPostDetail> details = new ArrayList<>();
        for (MediaPost post : p.getRecords()) {
            List<MediaAsset> assets = orderedAssets(post.getId());
            details.add(new MediaPostDetail(post, assets, resolveCover(post, assets)));
        }
        Page<MediaPostDetail> out = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        out.setRecords(details);
        return out;
    }

    @Override
    @Transactional
    public void deleteAsset(Long assetId) {
        MediaAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            return;
        }
        Long refs = postAssetMapper.selectCount(
                new LambdaQueryWrapper<MediaPostAsset>().eq(MediaPostAsset::getAssetId, assetId));
        if (refs != null && refs > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "素材正被内容引用，不能删除");
        }
        assetMapper.deleteById(assetId);
        storage.delete(asset.getMainPath());
        storage.delete(asset.getThumbPath());
        storage.delete(asset.getPosterPath());
    }

    @Override
    public Page<MediaAsset> pageAssets(String type, String status, String keyword, long page, long pageSize) {
        LambdaQueryWrapper<MediaAsset> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(type)) {
            w.eq(MediaAsset::getFileType, type);
        }
        if (StringUtils.hasText(status)) {
            w.eq(MediaAsset::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            w.like(MediaAsset::getOriginalName, keyword.trim());
        }
        w.orderByDesc(MediaAsset::getId);
        return assetMapper.selectPage(new Page<>(page, pageSize), w);
    }

    @Override
    public MediaAsset requireAsset(Long assetId) {
        MediaAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材不存在: " + assetId);
        }
        return asset;
    }

    @Override
    public boolean isAssetInPublishedPostOfChannel(Long assetId, Long channelId) {
        List<MediaPostAsset> links = postAssetMapper.selectList(new LambdaQueryWrapper<MediaPostAsset>()
                .eq(MediaPostAsset::getAssetId, assetId));
        if (links.isEmpty()) {
            return false;
        }
        java.util.Set<Long> postIds = links.stream().map(MediaPostAsset::getPostId).collect(java.util.stream.Collectors.toSet());
        Long count = postMapper.selectCount(new LambdaQueryWrapper<MediaPost>()
                .in(MediaPost::getId, postIds)
                .eq(MediaPost::getChannelId, channelId)
                .eq(MediaPost::getStatus, MediaPost.STATUS_PUBLISHED));
        return count != null && count > 0;
    }

    // ---------- 内部 ----------

    private void replaceAssets(Long postId, List<Long> assetIds) {
        // 记录旧关联，替换后释放不再被引用的素材（记录 + 磁盘文件同步删除）
        List<MediaPostAsset> previous = postAssetMapper.selectList(
                new LambdaQueryWrapper<MediaPostAsset>().eq(MediaPostAsset::getPostId, postId));
        postAssetMapper.delete(new LambdaQueryWrapper<MediaPostAsset>().eq(MediaPostAsset::getPostId, postId));

        java.util.Set<Long> kept = new java.util.HashSet<>();
        if (assetIds != null) {
            for (Long id : assetIds) {
                if (id != null) {
                    kept.add(id);
                }
            }
        }
        if (assetIds == null || assetIds.isEmpty()) {
            releaseRemovedAssets(previous, kept);
            return;
        }
        int seq = 0;
        for (Long assetId : assetIds) {
            if (assetId == null) {
                continue;
            }
            MediaAsset asset = assetMapper.selectById(assetId);
            if (asset == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "素材不存在: " + assetId);
            }
            if (!MediaAsset.STATUS_READY.equals(asset.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "素材未就绪不能加入内容（" + asset.getOriginalName() + " 转码中或失败）");
            }
            MediaPostAsset link = new MediaPostAsset();
            link.setPostId(postId);
            link.setAssetId(assetId);
            link.setSeq(seq++);
            postAssetMapper.insert(link);
        }
        releaseRemovedAssets(previous, kept);
    }

    /** 释放本次编辑中被移除的素材：无任何帖引用时删除记录与磁盘文件。 */
    private void releaseRemovedAssets(List<MediaPostAsset> previous, java.util.Set<Long> kept) {
        for (MediaPostAsset link : previous) {
            if (!kept.contains(link.getAssetId())) {
                releaseAssetIfOrphan(link.getAssetId());
            }
        }
    }

    private void syncTypeAndCover(Long postId) {
        List<MediaAsset> assets = orderedAssets(postId);
        boolean hasImage = assets.stream().anyMatch(a -> MediaAsset.TYPE_IMAGE.equals(a.getFileType()));
        boolean hasVideo = assets.stream().anyMatch(a -> MediaAsset.TYPE_VIDEO.equals(a.getFileType()));
        String type = MediaPost.TYPE_IMAGE;
        if (hasVideo && hasImage) {
            type = MediaPost.TYPE_MIXED;
        } else if (hasVideo) {
            type = MediaPost.TYPE_VIDEO;
        }
        Long coverAssetId = null;
        if (hasVideo) {
            coverAssetId = assets.stream()
                    .filter(a -> MediaAsset.TYPE_VIDEO.equals(a.getFileType())).findFirst().map(MediaAsset::getId).orElse(null);
        } else if (!assets.isEmpty()) {
            coverAssetId = assets.get(0).getId();
        }
        MediaPost post = postMapper.selectById(postId);
        if (post != null && (!type.equals(post.getType())
                || !Objects.equals(coverAssetId, post.getCoverAssetId()))) {
            post.setType(type);
            post.setCoverAssetId(coverAssetId);
            postMapper.updateById(post);
        }
    }

    private MediaAsset resolveCover(MediaPost post, List<MediaAsset> assets) {
        if (post == null || post.getCoverAssetId() == null) {
            return assets.isEmpty() ? null : assets.get(0);
        }
        return assets.stream().filter(a -> a.getId().equals(post.getCoverAssetId())).findFirst()
                .orElse(assets.isEmpty() ? null : assets.get(0));
    }

    private List<MediaAsset> orderedAssets(Long postId) {
        List<MediaPostAsset> links = postAssetMapper.selectList(
                new LambdaQueryWrapper<MediaPostAsset>()
                        .eq(MediaPostAsset::getPostId, postId)
                        .orderByAsc(MediaPostAsset::getSeq));
        if (links.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> ids = links.stream().map(MediaPostAsset::getAssetId).collect(Collectors.toSet());
        Map<Long, MediaAsset> byId = assetMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MediaAsset::getId, Function.identity()));
        List<MediaAsset> out = new ArrayList<>();
        for (MediaPostAsset link : links) {
            MediaAsset asset = byId.get(link.getAssetId());
            if (asset != null) {
                out.add(asset);
            }
        }
        return out;
    }

    /** 删除帖关联后：若素材不再被任何帖引用 → 物理删除文件与记录。 */
    private void releaseAssetIfOrphan(Long assetId) {
        Long refs = postAssetMapper.selectCount(
                new LambdaQueryWrapper<MediaPostAsset>().eq(MediaPostAsset::getAssetId, assetId));
        if (refs == null || refs == 0) {
            MediaAsset asset = assetMapper.selectById(assetId);
            if (asset != null) {
                assetMapper.deleteById(assetId);
                storage.delete(asset.getMainPath());
                storage.delete(asset.getThumbPath());
                storage.delete(asset.getPosterPath());
            }
        }
    }

    private MediaPost require(Long id) {
        MediaPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "内容不存在");
        }
        return post;
    }

    private void requireDraft(MediaPost post) {
        if (!MediaPost.STATUS_DRAFT.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "仅草稿可操作：已发布内容请先下架回到草稿");
        }
    }

    private void requireTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "标题必填");
        }
        if (title.trim().length() > 120) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "标题不能超过 120 字");
        }
    }
}
