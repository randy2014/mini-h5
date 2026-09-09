package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.book.entity.SubscribeChannel;
import com.mini.novel.book.mapper.SubscribeChannelMapper;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.media.entity.MediaPost;
import com.mini.novel.media.service.MediaPostService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台：多媒体内容帖（草稿 / 已发布）管理。
 * 规则（见 docs/media-pool-design.md）：草稿可增改删发；已发布仅下架；发布挂载订阅频道管理中的 PUBLISHED 频道。
 */
@RestController
@RequestMapping("/admin/media/posts")
public class AdminMediaPostController {

    private final MediaPostService postService;
    private final SubscribeChannelMapper channelMapper;

    public AdminMediaPostController(MediaPostService postService, SubscribeChannelMapper channelMapper) {
        this.postService = postService;
        this.channelMapper = channelMapper;
    }

    /** 可挂载频道下拉（订阅频道管理中 PUBLISHED 的频道）。 */
    @GetMapping("/channels")
    public Result<List<SubscribeChannel>> channels() {
        return Result.ok(channelMapper.selectList(new LambdaQueryWrapper<SubscribeChannel>()
                .eq(SubscribeChannel::getStatus, SubscribeChannel.STATUS_PUBLISHED)
                .orderByAsc(SubscribeChannel::getSort).orderByAsc(SubscribeChannel::getId)));
    }

    @PostMapping
    public Result<MediaPost> create(@RequestBody SavePostRequest request) {
        return Result.ok(postService.createDraft(request.title(), request.assetIds(), request.operatorId()));
    }

    @GetMapping("/drafts")
    public Result<Page<MediaPost>> drafts(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(postService.pageDrafts(Math.max(1, page), Math.min(100, pageSize)));
    }

    @GetMapping("/published")
    public Result<Page<MediaPost>> published(@RequestParam(required = false) Long channelId,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(postService.pagePublished(channelId, Math.max(1, page), Math.min(100, pageSize)));
    }

    @GetMapping("/{id}")
    public Result<MediaPostService.MediaPostDetail> detail(@PathVariable Long id) {
        return Result.ok(postService.getDetail(id));
    }

    @PutMapping("/{id}")
    public Result<MediaPost> update(@PathVariable Long id, @RequestBody SavePostRequest request) {
        return Result.ok(postService.updateDraft(id, request.title(), request.assetIds(), request.operatorId()));
    }

    @PostMapping("/{id}/publish")
    public Result<MediaPost> publish(@PathVariable Long id, @RequestBody PublishRequest request) {
        requireChannelPublished(request.channelId());
        return Result.ok(postService.publish(id, request.channelId(), request.operatorId()));
    }

    @PostMapping("/{id}/unpublish")
    public Result<MediaPost> unpublish(@PathVariable Long id) {
        return Result.ok(postService.unpublish(id));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        postService.deleteDraft(id);
        return Result.ok(true);
    }

    private void requireChannelPublished(Long channelId) {
        if (channelId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "发布必须选择挂载频道");
        }
        SubscribeChannel channel = channelMapper.selectById(channelId);
        if (channel == null || !SubscribeChannel.STATUS_PUBLISHED.equals(channel.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "挂载频道不存在或未发布（仅订阅频道管理中已发布频道可挂载）");
        }
    }

    public record SavePostRequest(String title, List<Long> assetIds, Long operatorId) {
    }

    public record PublishRequest(Long channelId, Long operatorId) {
    }
}
