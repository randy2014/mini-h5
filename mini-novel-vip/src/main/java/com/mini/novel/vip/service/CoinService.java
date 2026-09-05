package com.mini.novel.vip.service;

import com.mini.novel.vip.entity.UserCoinLog;
import java.util.List;

public interface CoinService {
    long balance(Long userId);

    void recharge(Long userId, long amount, Long operatorId, String remark);

    void deduct(Long userId, long amount, String bizType, String bizId);

    List<UserCoinLog> logs(Long userId);
}
