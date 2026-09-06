package com.mini.novel.api.service;

import com.mini.novel.api.model.ChannelNovelsVo;
import com.mini.novel.api.model.SubscribeChannelVo;
import com.mini.novel.user.entity.UserReadHistory;
import com.mini.novel.vip.entity.UserSubscribe;
import java.util.List;
import java.util.Set;

public interface SubscribeService {
    List<SubscribeChannelVo> channels(Long userId);

    ChannelNovelsVo channelNovels(Long userId, Long channelId, long page, long pageSize);

    UserSubscribe subscribe(Long userId, Long channelId, String periodType);

    List<UserSubscribe> subscribeAll(Long userId, String periodType);

    List<UserSubscribe> mySubscribes(Long userId);

    List<UserReadHistory> history(Long userId);

    boolean isTrialActive(Long userId);

    boolean isAccessible(Long userId, Long channelId);

    Set<Long> subscribedNovelIds(Long userId);

    int expireSweep();
}
