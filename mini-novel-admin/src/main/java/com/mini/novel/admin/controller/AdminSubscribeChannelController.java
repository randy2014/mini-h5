package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.book.entity.SubscribeChannel;
import com.mini.novel.book.entity.SubscribeChannelNovel;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.book.mapper.SubscribeChannelMapper;
import com.mini.novel.book.mapper.SubscribeChannelNovelMapper;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.vip.entity.UserSubscribe;
import com.mini.novel.vip.mapper.UserSubscribeMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/subscribe-channels")
public class AdminSubscribeChannelController {
    private final SubscribeChannelMapper channelMapper;
    private final SubscribeChannelNovelMapper channelNovelMapper;
    private final UserSubscribeMapper subscribeMapper;
    private final NovelMapper novelMapper;

    public AdminSubscribeChannelController(SubscribeChannelMapper channelMapper,
                                           SubscribeChannelNovelMapper channelNovelMapper,
                                           UserSubscribeMapper subscribeMapper,
                                           NovelMapper novelMapper) {
        this.channelMapper = channelMapper;
        this.channelNovelMapper = channelNovelMapper;
        this.subscribeMapper = subscribeMapper;
        this.novelMapper = novelMapper;
    }

    @GetMapping
    public Result<List<SubscribeChannel>> list() {
        return Result.ok(channelMapper.selectList(new LambdaQueryWrapper<SubscribeChannel>()
                .orderByAsc(SubscribeChannel::getSort)
                .orderByAsc(SubscribeChannel::getId)));
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

    @PostMapping("/{channelId}/novels")
    public Result<SubscribeChannelNovel> addNovel(@PathVariable Long channelId,
                                                  @RequestBody AddNovelRequest request) {
        require(channelId);
        if (novelMapper.selectById(request.novelId()) == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "小说不存在");
        }
        SubscribeChannelNovel link = new SubscribeChannelNovel();
        link.setChannelId(channelId);
        link.setNovelId(request.novelId());
        link.setOperatorId(request.operatorId());
        link.setCreatedAt(LocalDateTime.now());
        channelNovelMapper.insert(link);
        return Result.ok(link);
    }

    @DeleteMapping("/{channelId}/novels/{novelId}")
    public Result<Boolean> removeNovel(@PathVariable Long channelId, @PathVariable Long novelId) {
        channelNovelMapper.delete(new LambdaQueryWrapper<SubscribeChannelNovel>()
                .eq(SubscribeChannelNovel::getChannelId, channelId)
                .eq(SubscribeChannelNovel::getNovelId, novelId));
        return Result.ok(true);
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
}
