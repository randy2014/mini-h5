package com.mini.novel.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.api.model.ChannelNovelsVo;
import com.mini.novel.api.model.SubscribeChannelVo;
import com.mini.novel.api.service.SubscribeService;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.entity.SubscribeChannel;
import com.mini.novel.book.entity.SubscribeChannelNovel;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.book.mapper.SubscribeChannelMapper;
import com.mini.novel.book.mapper.SubscribeChannelNovelMapper;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.entity.UserReadHistory;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.user.mapper.UserReadHistoryMapper;
import com.mini.novel.vip.entity.UserCoinLog;
import com.mini.novel.vip.entity.UserSubscribe;
import com.mini.novel.vip.mapper.UserSubscribeMapper;
import com.mini.novel.vip.service.CoinService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SubscribeServiceImpl implements SubscribeService {
    private static final int TRIAL_DAYS = 7;

    private final SubscribeChannelMapper channelMapper;
    private final SubscribeChannelNovelMapper channelNovelMapper;
    private final UserSubscribeMapper subscribeMapper;
    private final NovelMapper novelMapper;
    private final AppUserMapper appUserMapper;
    private final UserReadHistoryMapper readHistoryMapper;
    private final CoinService coinService;

    @Value("#{${app.subscribe.prices:{WEEK:100, MONTH:300, QUARTER:800, YEAR:3000}}}")
    private Map<String, Long> prices;

    public SubscribeServiceImpl(SubscribeChannelMapper channelMapper,
                                SubscribeChannelNovelMapper channelNovelMapper,
                                UserSubscribeMapper subscribeMapper,
                                NovelMapper novelMapper,
                                AppUserMapper appUserMapper,
                                UserReadHistoryMapper readHistoryMapper,
                                CoinService coinService) {
        this.channelMapper = channelMapper;
        this.channelNovelMapper = channelNovelMapper;
        this.subscribeMapper = subscribeMapper;
        this.novelMapper = novelMapper;
        this.appUserMapper = appUserMapper;
        this.readHistoryMapper = readHistoryMapper;
        this.coinService = coinService;
    }

    @Override
    public List<SubscribeChannelVo> channels(Long userId) {
        List<SubscribeChannel> channels = channelMapper.selectList(new LambdaQueryWrapper<SubscribeChannel>()
                .eq(SubscribeChannel::getStatus, SubscribeChannel.STATUS_PUBLISHED)
                .orderByAsc(SubscribeChannel::getSort)
                .orderByAsc(SubscribeChannel::getId));
        Map<Long, UserSubscribe> activeByChannel = activeSubscribes(userId).stream()
                .collect(Collectors.toMap(UserSubscribe::getChannelId, Function.identity(), (l, r) -> l));
        boolean trial = isTrialActive(userId);
        List<SubscribeChannelVo> result = new ArrayList<>();
        for (SubscribeChannel channel : channels) {
            SubscribeChannelVo vo = new SubscribeChannelVo();
            vo.setId(channel.getId());
            vo.setName(channel.getName());
            vo.setCover(channel.getCover());
            vo.setDescription(channel.getDescription());
            vo.setSort(channel.getSort());
            vo.setTrial(trial);
            UserSubscribe active = activeByChannel.get(channel.getId());
            vo.setSubscribed(active != null || trial);
            if (active != null) {
                vo.setPeriodType(active.getPeriodType());
                vo.setEndTime(active.getEndTime());
                vo.setDaysLeft(daysLeft(active.getEndTime()));
            }
            vo.setNovelCount(channelNovelMapper.selectCount(new QueryWrapper<SubscribeChannelNovel>()
                    .eq("channel_id", channel.getId())));
            result.add(vo);
        }
        return result;
    }

    @Override
    public ChannelNovelsVo channelNovels(Long userId, Long channelId, long page, long pageSize) {
        SubscribeChannel channel = channelMapper.selectById(channelId);
        if (channel == null || !SubscribeChannel.STATUS_PUBLISHED.equals(channel.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "频道不存在或未发布");
        }
        List<SubscribeChannelNovel> links = channelNovelMapper.selectList(new QueryWrapper<SubscribeChannelNovel>()
                .eq("channel_id", channelId));
        Set<Long> channelNovelIds = links.stream().map(SubscribeChannelNovel::getNovelId).collect(Collectors.toSet());
        Set<Long> readNovelIds = readHistoryMapper.selectList(new LambdaQueryWrapper<UserReadHistory>()
                        .eq(UserReadHistory::getUserId, userId)
                        .select(UserReadHistory::getNovelId))
                .stream().map(UserReadHistory::getNovelId).collect(Collectors.toSet());
        Set<Long> unreadIds = channelNovelIds.stream()
                .filter(id -> !readNovelIds.contains(id))
                .collect(Collectors.toSet());

        boolean accessible = isAccessible(userId, channelId);
        long safePageSize = Math.max(1, Math.min(100, pageSize));
        ChannelNovelsVo vo = new ChannelNovelsVo();
        if (unreadIds.isEmpty()) {
            vo.setRecords(new ArrayList<>());
            vo.setTotal(0);
            vo.setRestricted(!accessible);
            return vo;
        }
        QueryWrapper<Novel> query = new QueryWrapper<Novel>()
                .ne("status", 0)
                .in("id", unreadIds)
                .orderByDesc("updated_at");
        long safePage = Math.max(1, page);
        if (!accessible) {
            safePage = 1;
        }
        Page<Novel> result = novelMapper.selectPage(new Page<>(safePage, safePageSize), query);
        vo.setRecords(result.getRecords());
        vo.setTotal(result.getTotal());
        vo.setRestricted(!accessible);
        return vo;
    }

    @Override
    @Transactional
    public UserSubscribe subscribe(Long userId, Long channelId, String periodType) {
        SubscribeChannel channel = requirePublished(channelId);
        String normalizedPeriod = normalizePeriod(periodType);
        long price = priceFor(normalizedPeriod);
        coinService.deduct(userId, price, UserCoinLog.BIZ_SUBSCRIBE, "channel:" + channelId);

        UserSubscribe existing = subscribeMapper.selectActive(userId, channelId);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setEndTime(existing.getEndTime().plusDays(daysFor(normalizedPeriod)));
            existing.setPeriodType(normalizedPeriod);
            existing.setCostCoins((existing.getCostCoins() == null ? 0 : existing.getCostCoins()) + price);
            existing.setUpdatedAt(now);
            subscribeMapper.updateById(existing);
            return existing;
        }
        UserSubscribe sub = new UserSubscribe();
        sub.setUserId(userId);
        sub.setChannelId(channelId);
        sub.setPeriodType(normalizedPeriod);
        sub.setStartTime(now);
        sub.setEndTime(now.plusDays(daysFor(normalizedPeriod)));
        sub.setStatus(UserSubscribe.STATUS_ACTIVE);
        sub.setCostCoins(price);
        sub.setCreatedAt(now);
        sub.setUpdatedAt(now);
        subscribeMapper.insert(sub);
        return sub;
    }

    @Override
    @Transactional
    public List<UserSubscribe> subscribeAll(Long userId, String periodType) {
        String normalizedPeriod = normalizePeriod(periodType);
        List<SubscribeChannel> published = channelMapper.selectList(new LambdaQueryWrapper<SubscribeChannel>()
                .eq(SubscribeChannel::getStatus, SubscribeChannel.STATUS_PUBLISHED));
        Map<Long, UserSubscribe> activeByChannel = activeSubscribes(userId).stream()
                .collect(Collectors.toMap(UserSubscribe::getChannelId, Function.identity(), (l, r) -> l));
        List<SubscribeChannel> targets = published.stream()
                .filter(c -> !activeByChannel.containsKey(c.getId()))
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            return new ArrayList<>();
        }
        long totalPrice = targets.stream().mapToLong(c -> priceFor(normalizedPeriod)).sum();
        if (coinService.balance(userId) < totalPrice) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "快乐币余额不足，无法一键订阅");
        }
        List<UserSubscribe> created = new ArrayList<>();
        for (SubscribeChannel channel : targets) {
            created.add(subscribe(userId, channel.getId(), normalizedPeriod));
        }
        return created;
    }

    @Override
    public List<UserSubscribe> mySubscribes(Long userId) {
        return activeSubscribes(userId);
    }

    @Override
    public List<UserReadHistory> history(Long userId) {
        return readHistoryMapper.selectList(new LambdaQueryWrapper<UserReadHistory>()
                .eq(UserReadHistory::getUserId, userId)
                .orderByDesc(UserReadHistory::getReadAt));
    }

    @Override
    public boolean isTrialActive(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null || user.getVipActivatedAt() == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(user.getVipActivatedAt().plusDays(TRIAL_DAYS));
    }

    @Override
    public boolean isAccessible(Long userId, Long channelId) {
        if (isTrialActive(userId)) {
            return true;
        }
        return subscribeMapper.selectActive(userId, channelId) != null;
    }

    @Override
    @Transactional
    public int expireSweep() {
        List<UserSubscribe> expired = subscribeMapper.selectList(new LambdaQueryWrapper<UserSubscribe>()
                .eq(UserSubscribe::getStatus, UserSubscribe.STATUS_ACTIVE)
                .lt(UserSubscribe::getEndTime, LocalDateTime.now()));
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (UserSubscribe sub : expired) {
            sub.setStatus(UserSubscribe.STATUS_EXPIRED);
            sub.setUpdatedAt(now);
            subscribeMapper.updateById(sub);
            count++;
        }
        return count;
    }

    private List<UserSubscribe> activeSubscribes(Long userId) {
        return subscribeMapper.selectList(new LambdaQueryWrapper<UserSubscribe>()
                .eq(UserSubscribe::getUserId, userId)
                .eq(UserSubscribe::getStatus, UserSubscribe.STATUS_ACTIVE)
                .gt(UserSubscribe::getEndTime, LocalDateTime.now()));
    }

    private SubscribeChannel requirePublished(Long channelId) {
        SubscribeChannel channel = channelMapper.selectById(channelId);
        if (channel == null || !SubscribeChannel.STATUS_PUBLISHED.equals(channel.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "频道不存在或未发布");
        }
        return channel;
    }

    private long daysLeft(LocalDateTime endTime) {
        if (endTime == null) {
            return 0;
        }
        return Math.max(0, Duration.between(LocalDateTime.now(), endTime).toDays());
    }

    static String normalizePeriod(String periodType) {
        if (!StringUtils.hasText(periodType)) {
            return UserSubscribe.PERIOD_MONTH;
        }
        return periodType.trim().toUpperCase();
    }

    static long daysFor(String periodType) {
        return switch (periodType) {
            case UserSubscribe.PERIOD_WEEK -> 7;
            case UserSubscribe.PERIOD_QUARTER -> 90;
            case UserSubscribe.PERIOD_YEAR -> 365;
            default -> 30;
        };
    }

    long priceFor(String periodType) {
        Long price = prices.get(periodType);
        return price == null ? 300L : price;
    }
}
