package com.mini.novel.api.controller;

import com.mini.novel.api.model.ChannelFeedVo;
import com.mini.novel.api.model.ChannelNovelsVo;
import com.mini.novel.api.model.SubscribeChannelVo;
import com.mini.novel.api.service.ChannelFeedService;
import com.mini.novel.api.service.SubscribeService;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.common.result.Result;
import com.mini.novel.vip.entity.UserSubscribe;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscribe")
public class SubscribeController {
    private final SubscribeService subscribeService;
    private final ChannelFeedService channelFeedService;
    private final CurrentUserResolver currentUserResolver;

    public SubscribeController(SubscribeService subscribeService,
                               ChannelFeedService channelFeedService,
                               CurrentUserResolver currentUserResolver) {
        this.subscribeService = subscribeService;
        this.channelFeedService = channelFeedService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/channels")
    public Result<List<SubscribeChannelVo>> channels() {
        return Result.ok(subscribeService.channels(currentUserId()));
    }

    /** 频道统一内容流（小说 + 图文/视频 混排，时间倒序）—— 频道详情网格/列表数据源。 */
    @GetMapping("/channels/{channelId}/feed")
    public Result<ChannelFeedVo> feed(@PathVariable Long channelId,
                                      @RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(channelFeedService.feed(currentUserId(), channelId, page, pageSize));
    }

    @GetMapping("/channels/{channelId}/novels")
    public Result<ChannelNovelsVo> novels(@PathVariable Long channelId,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(subscribeService.channelNovels(currentUserId(), channelId, page, pageSize));
    }

    @PostMapping("/channels/{channelId}")
    public Result<UserSubscribe> subscribe(@PathVariable Long channelId,
                                           @RequestParam(defaultValue = "MONTH") String periodType) {
        return Result.ok(subscribeService.subscribe(currentUserId(), channelId, periodType));
    }

    @PostMapping("/subscribe-all")
    public Result<List<UserSubscribe>> subscribeAll(@RequestParam(defaultValue = "MONTH") String periodType) {
        return Result.ok(subscribeService.subscribeAll(currentUserId(), periodType));
    }

    @GetMapping("/my")
    public Result<List<UserSubscribe>> my() {
        return Result.ok(subscribeService.mySubscribes(currentUserId()));
    }

    private Long currentUserId() {
        return currentUserResolver.requireUser(null).getId();
    }
}
