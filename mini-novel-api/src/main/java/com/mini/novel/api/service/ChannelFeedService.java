package com.mini.novel.api.service;

import com.mini.novel.api.model.ChannelFeedVo;

public interface ChannelFeedService {

    /**
     * 频道统一内容流（小说 + 图文/视频帖，按加入/发布时间倒序混排）。
     *
     * @param userId  当前用户
     * @param channelId 频道 id
     * @param page 从 1 开始
     * @param pageSize 每页条数
     * @return restricted=true 表示未订阅（媒体内容不出现在结果中，前端隐藏媒体区块并引导订阅）
     */
    ChannelFeedVo feed(Long userId, Long channelId, long page, long pageSize);
}
