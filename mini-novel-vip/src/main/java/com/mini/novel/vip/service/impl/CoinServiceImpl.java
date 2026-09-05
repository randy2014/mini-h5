package com.mini.novel.vip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.vip.entity.UserCoinBalance;
import com.mini.novel.vip.entity.UserCoinLog;
import com.mini.novel.vip.mapper.UserCoinBalanceMapper;
import com.mini.novel.vip.mapper.UserCoinLogMapper;
import com.mini.novel.vip.service.CoinService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoinServiceImpl implements CoinService {
    private final UserCoinBalanceMapper balanceMapper;
    private final UserCoinLogMapper logMapper;

    public CoinServiceImpl(UserCoinBalanceMapper balanceMapper, UserCoinLogMapper logMapper) {
        this.balanceMapper = balanceMapper;
        this.logMapper = logMapper;
    }

    @Override
    public long balance(Long userId) {
        UserCoinBalance balance = balanceMapper.selectById(userId);
        return balance == null || balance.getBalance() == null ? 0L : balance.getBalance();
    }

    @Override
    @Transactional
    public void recharge(Long userId, long amount, Long operatorId, String remark) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "充值数量必须大于 0");
        }
        UserCoinBalance balance = lockBalance(userId);
        long after = (balance.getBalance() == null ? 0L : balance.getBalance()) + amount;
        balance.setBalance(after);
        balanceMapper.updateById(balance);
        insertLog(userId, amount, after, UserCoinLog.BIZ_RECHARGE, null, operatorId, remark);
    }

    @Override
    @Transactional
    public void deduct(Long userId, long amount, String bizType, String bizId) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "扣费数量必须大于 0");
        }
        UserCoinBalance balance = lockBalance(userId);
        long current = balance.getBalance() == null ? 0L : balance.getBalance();
        if (current < amount) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "快乐币余额不足");
        }
        long after = current - amount;
        balance.setBalance(after);
        balanceMapper.updateById(balance);
        insertLog(userId, -amount, after, bizType, bizId, null, null);
    }

    @Override
    public List<UserCoinLog> logs(Long userId) {
        return logMapper.selectList(new LambdaQueryWrapper<UserCoinLog>()
                .eq(UserCoinLog::getUserId, userId)
                .orderByDesc(UserCoinLog::getId));
    }

    private UserCoinBalance lockBalance(Long userId) {
        UserCoinBalance balance = balanceMapper.selectByUserIdForUpdate(userId);
        if (balance == null) {
            balance = new UserCoinBalance();
            balance.setUserId(userId);
            balance.setBalance(0L);
            balanceMapper.insert(balance);
            balance = balanceMapper.selectByUserIdForUpdate(userId);
        }
        return balance;
    }

    private void insertLog(Long userId, long change, long after, String bizType,
                           String bizId, Long operatorId, String remark) {
        UserCoinLog log = new UserCoinLog();
        log.setUserId(userId);
        log.setChangeAmount(change);
        log.setBalanceAfter(after);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        logMapper.insert(log);
    }
}
