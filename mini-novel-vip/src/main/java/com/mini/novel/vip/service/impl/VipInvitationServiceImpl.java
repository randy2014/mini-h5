package com.mini.novel.vip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.vip.entity.UserVip;
import com.mini.novel.vip.entity.VipAdjustLog;
import com.mini.novel.vip.entity.VipInvitationCode;
import com.mini.novel.vip.entity.VipInvitationRecord;
import com.mini.novel.vip.entity.VipOperationAudit;
import com.mini.novel.vip.mapper.UserVipMapper;
import com.mini.novel.vip.mapper.VipAdjustLogMapper;
import com.mini.novel.vip.mapper.VipInvitationCodeMapper;
import com.mini.novel.vip.mapper.VipInvitationRecordMapper;
import com.mini.novel.vip.mapper.VipOperationAuditMapper;
import com.mini.novel.vip.service.VipInvitationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VipInvitationServiceImpl implements VipInvitationService {
    private static final int DEFAULT_INVITE_QUOTA = 3;
    private static final LocalDateTime PERMANENT_EXPIRE_AT = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    private final AppUserMapper appUserMapper;
    private final UserVipMapper userVipMapper;
    private final VipAdjustLogMapper vipAdjustLogMapper;
    private final VipInvitationCodeMapper invitationCodeMapper;
    private final VipInvitationRecordMapper invitationRecordMapper;
    private final VipOperationAuditMapper operationAuditMapper;

    public VipInvitationServiceImpl(AppUserMapper appUserMapper, UserVipMapper userVipMapper,
                                    VipAdjustLogMapper vipAdjustLogMapper,
                                    VipInvitationCodeMapper invitationCodeMapper,
                                    VipInvitationRecordMapper invitationRecordMapper,
                                    VipOperationAuditMapper operationAuditMapper) {
        this.appUserMapper = appUserMapper;
        this.userVipMapper = userVipMapper;
        this.vipAdjustLogMapper = vipAdjustLogMapper;
        this.invitationCodeMapper = invitationCodeMapper;
        this.invitationRecordMapper = invitationRecordMapper;
        this.operationAuditMapper = operationAuditMapper;
    }

    @Override
    @Transactional
    public LoginResult loginOrCreate(String mobile, String invitationCode, boolean confirmCreateNormal) {
        LocalDateTime now = LocalDateTime.now();
        String code = normalizeCode(invitationCode);
        AppUser existing = appUserMapper.selectByMobileForUpdate(mobile);
        if (existing != null) {
            LoginResult result = result(existing, false);
            if (StringUtils.hasText(code) && !isVip(existing)) {
                result.setLoginErrorCode("INVITE_ONLY_ON_CREATE");
                result.setMessage("邀请码仅首次创建账号时生效，请联系客服升级 VIP");
            }
            return result;
        }
        if (!StringUtils.hasText(code) && !confirmCreateNormal) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新账号未填邀请码将创建普通账号");
        }

        VipInvitationCode inviterCode = null;
        if (StringUtils.hasText(code)) {
            inviterCode = invitationCodeMapper.selectByCodeForUpdate(code);
            if (!isUsable(inviterCode)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "邀请码不可用，可重新填写或不使用邀请码创建普通账号");
            }
        }

        AppUser user = createUser(mobile, now);
        LoginResult result = result(user, true);
        if (inviterCode != null) {
            applyInvitation(user, inviterCode, now);
            result.setInviteCodeApplied(true);
            result.setInviteQuotaLeft(inviterCode.getRemainingQuota());
            result.setExclusiveInviteCode(ensureCurrentCode(user.getId(), 0L, "邀请注册自动生成", now).getCode());
            result.setUser(appUserMapper.selectById(user.getId()));
        }
        return result;
    }

    @Override
    @Transactional
    public VipAdminResult adjustVip(Long userId, String action, Integer days, String expireAt, Long operatorId,
                                    String reason, String requestId) {
        if (StringUtils.hasText(requestId) && operationAuditMapper.selectByRequestId(requestId) != null) {
            VipAdminResult existing = new VipAdminResult();
            existing.setUser(appUserMapper.selectById(userId));
            existing.setInvitationCode(invitationCodeMapper.selectCurrentByOwner(userId));
            return existing;
        }
        AppUser before = appUserMapper.selectByIdForUpdate(userId);
        if (before == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        String normalizedAction = action == null ? "UPGRADE" : action;
        LocalDateTime beforeExpire = before.getVipExpireTime();
        LocalDateTime afterExpire = switch (normalizedAction) {
            case "DOWNGRADE", "SUSPEND", "CANCEL" -> null;
            case "RESTORE" -> beforeExpire != null && beforeExpire.isAfter(now) ? beforeExpire : PERMANENT_EXPIRE_AT;
            case "SET" -> parseExpireAt(expireAt);
            default -> {
                LocalDateTime base = beforeExpire != null && beforeExpire.isAfter(now) ? beforeExpire : now;
                yield days == null ? PERMANENT_EXPIRE_AT : base.plusDays(days);
            }
        };

        AppUser update = new AppUser();
        update.setId(userId);
        update.setVipExpireTime(afterExpire);
        update.setVipStatus(afterExpire == null ? 0 : (afterExpire.getYear() >= 2099 ? 2 : 1));
        update.setVipSource(afterExpire == null ? null : "ADMIN");
        update.setVipActivatedAt(afterExpire == null ? before.getVipActivatedAt() : now);
        update.setVipDisabledAt(afterExpire == null ? now : null);
        update.setUpdatedAt(now);
        appUserMapper.updateById(update);

        VipInvitationCode code = invitationCodeMapper.selectCurrentByOwnerForUpdate(userId);
        if (afterExpire == null) {
            disableCurrentCode(code, operatorId, reason, now);
        } else {
            code = ensureCurrentCode(userId, operatorId, reason, now);
        }
        insertUserVip(userId, afterExpire, "ADMIN", null, operatorId, reason, now);
        insertVipAdjustLog(before, appUserMapper.selectById(userId), normalizedAction, days, reason, operatorId, now);
        audit(normalizedAction, userId, code == null ? null : code.getId(), null,
                userJson(before), userJson(appUserMapper.selectById(userId)), operatorId, reason, requestId, now);

        VipAdminResult result = new VipAdminResult();
        result.setUser(appUserMapper.selectById(userId));
        result.setInvitationCode(invitationCodeMapper.selectCurrentByOwner(userId));
        return result;
    }

    @Override
    @Transactional
    public VipInvitationCode enableCode(Long codeId, Long operatorId, String reason, String requestId) {
        if (hasRequest(requestId)) {
            return invitationCodeMapper.selectById(codeId);
        }
        VipInvitationCode code = invitationCodeMapper.selectById(codeId);
        AppUser owner = code == null ? null : appUserMapper.selectByIdForUpdate(code.getOwnerUserId());
        if (code == null || owner == null || !isVip(owner)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅有效 VIP 的邀请码可启用");
        }
        code.setStatus("ENABLED");
        code.setEnabledAt(LocalDateTime.now());
        code.setDisabledAt(null);
        code.setOperatorId(operatorId);
        code.setRemark(reason);
        code.setUpdatedAt(LocalDateTime.now());
        invitationCodeMapper.updateById(code);
        audit("ENABLE_CODE", code.getOwnerUserId(), code.getId(), null, null, code.getStatus(), operatorId, reason, requestId, LocalDateTime.now());
        return code;
    }

    @Override
    @Transactional
    public VipInvitationCode disableCode(Long codeId, Long operatorId, String reason, String requestId) {
        if (hasRequest(requestId)) {
            return invitationCodeMapper.selectById(codeId);
        }
        VipInvitationCode code = invitationCodeMapper.selectById(codeId);
        if (code == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "邀请码不存在");
        }
        disableCurrentCode(code, operatorId, reason, LocalDateTime.now());
        audit("DISABLE_CODE", code.getOwnerUserId(), code.getId(), null, null, code.getStatus(), operatorId, reason, requestId, LocalDateTime.now());
        return code;
    }

    @Override
    @Transactional
    public VipInvitationCode reissueCode(Long userId, Long operatorId, String reason, String requestId) {
        if (hasRequest(requestId)) {
            return invitationCodeMapper.selectCurrentByOwner(userId);
        }
        LocalDateTime now = LocalDateTime.now();
        AppUser user = appUserMapper.selectByIdForUpdate(userId);
        if (!isVip(user)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅 VIP 用户可重发邀请码");
        }
        VipInvitationCode old = invitationCodeMapper.selectCurrentByOwnerForUpdate(userId);
        if (old != null) {
            old.setCurrent(false);
            old.setStatus("REVOKED");
            old.setRevokedAt(now);
            old.setOperatorId(operatorId);
            old.setRemark(reason);
            old.setUpdatedAt(now);
            invitationCodeMapper.updateById(old);
        }
        VipInvitationCode code = createCode(userId, operatorId, reason, now);
        if (old != null) {
            old.setReplacedByCodeId(code.getId());
            invitationCodeMapper.updateById(old);
        }
        audit("REISSUE_CODE", userId, code.getId(), null, old == null ? null : old.getCode(), code.getCode(), operatorId, reason, requestId, now);
        return code;
    }

    @Override
    @Transactional
    public VipInvitationCode updateQuota(Long codeId, Integer totalQuota, Long operatorId, String reason, String requestId) {
        if (hasRequest(requestId)) {
            return invitationCodeMapper.selectById(codeId);
        }
        VipInvitationCode code = invitationCodeMapper.selectById(codeId);
        if (code == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "邀请码不存在");
        }
        int used = safe(code.getUsedQuota());
        int total = Math.max(used, totalQuota == null ? DEFAULT_INVITE_QUOTA : totalQuota);
        String before = quotaJson(code);
        code.setTotalQuota(total);
        code.setRemainingQuota(total - used);
        code.setOperatorId(operatorId);
        code.setRemark(reason);
        code.setUpdatedAt(LocalDateTime.now());
        invitationCodeMapper.updateById(code);
        audit("UPDATE_QUOTA", code.getOwnerUserId(), code.getId(), null, before, quotaJson(code), operatorId, reason, requestId, LocalDateTime.now());
        return code;
    }

    @Override
    public VipInvitationCode currentCode(Long userId) {
        return invitationCodeMapper.selectCurrentByOwner(userId);
    }

    @Override
    public List<VipInvitationRecord> records(Long userId) {
        return invitationRecordMapper.selectList(new LambdaQueryWrapper<VipInvitationRecord>()
                .eq(userId != null, VipInvitationRecord::getInviterUserId, userId)
                .or(userId != null)
                .eq(userId != null, VipInvitationRecord::getInviteeUserId, userId)
                .orderByDesc(VipInvitationRecord::getCreatedAt)
                .last("LIMIT 200"));
    }

    @Override
    public List<VipOperationAudit> audits(Long userId) {
        return operationAuditMapper.selectList(new LambdaQueryWrapper<VipOperationAudit>()
                .eq(userId != null, VipOperationAudit::getTargetUserId, userId)
                .orderByDesc(VipOperationAudit::getCreatedAt)
                .last("LIMIT 200"));
    }

    private AppUser createUser(String mobile, LocalDateTime now) {
        AppUser user = new AppUser();
        user.setMobile(mobile);
        user.setNickname("读者" + mobile.substring(mobile.length() - 4));
        user.setStatus(1);
        user.setVipStatus(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        appUserMapper.insert(user);
        return user;
    }

    private void applyInvitation(AppUser user, VipInvitationCode inviterCode, LocalDateTime now) {
        inviterCode.setUsedQuota(safe(inviterCode.getUsedQuota()) + 1);
        inviterCode.setRemainingQuota(safe(inviterCode.getRemainingQuota()) - 1);
        inviterCode.setLastUsedAt(now);
        inviterCode.setUpdatedAt(now);
        invitationCodeMapper.updateById(inviterCode);

        AppUser vip = new AppUser();
        vip.setId(user.getId());
        vip.setVipStatus(2);
        vip.setVipExpireTime(PERMANENT_EXPIRE_AT);
        vip.setVipSource("INVITATION");
        vip.setVipActivatedAt(now);
        vip.setUpdatedAt(now);
        appUserMapper.updateById(vip);

        VipInvitationRecord record = new VipInvitationRecord();
        record.setInvitationCodeId(inviterCode.getId());
        record.setCodeSnapshot(inviterCode.getCode());
        record.setInviterUserId(inviterCode.getOwnerUserId());
        record.setInviteeUserId(user.getId());
        record.setStatus("ACTIVATED");
        record.setActivatedAt(now);
        record.setRemark("邀请注册激活 VIP");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        invitationRecordMapper.insert(record);
        insertUserVip(user.getId(), PERMANENT_EXPIRE_AT, "INVITATION", record.getId(), inviterCode.getOwnerUserId(), "邀请注册激活", now);
        audit("INVITE_ACTIVATE", user.getId(), inviterCode.getId(), record.getId(), null, inviterCode.getCode(), inviterCode.getOwnerUserId(), "邀请注册激活", null, now);
    }

    private VipInvitationCode ensureCurrentCode(Long userId, Long operatorId, String reason, LocalDateTime now) {
        VipInvitationCode code = invitationCodeMapper.selectCurrentByOwner(userId);
        if (code != null) {
            if (!"ENABLED".equals(code.getStatus())) {
                code.setStatus("ENABLED");
                code.setEnabledAt(now);
                code.setDisabledAt(null);
                code.setUpdatedAt(now);
                invitationCodeMapper.updateById(code);
            }
            return code;
        }
        return createCode(userId, operatorId, reason, now);
    }

    private VipInvitationCode createCode(Long userId, Long operatorId, String reason, LocalDateTime now) {
        VipInvitationCode code = new VipInvitationCode();
        code.setOwnerUserId(userId);
        code.setCode(generateCode());
        code.setStatus("ENABLED");
        code.setTotalQuota(DEFAULT_INVITE_QUOTA);
        code.setUsedQuota(0);
        code.setRemainingQuota(DEFAULT_INVITE_QUOTA);
        code.setCurrent(true);
        code.setGeneratedAt(now);
        code.setEnabledAt(now);
        code.setOperatorId(operatorId);
        code.setRemark(reason);
        code.setCreatedAt(now);
        code.setUpdatedAt(now);
        invitationCodeMapper.insert(code);
        return code;
    }

    private void disableCurrentCode(VipInvitationCode code, Long operatorId, String reason, LocalDateTime now) {
        if (code == null) {
            return;
        }
        code.setStatus("DISABLED");
        code.setDisabledAt(now);
        code.setOperatorId(operatorId);
        code.setRemark(reason);
        code.setUpdatedAt(now);
        invitationCodeMapper.updateById(code);
    }

    private void insertUserVip(Long userId, LocalDateTime expireAt, String sourceType, Long sourceRefId, Long operatorId, String remark, LocalDateTime now) {
        if (expireAt == null) {
            return;
        }
        UserVip userVip = new UserVip();
        userVip.setUserId(userId);
        userVip.setStartTime(now);
        userVip.setEndTime(expireAt);
        userVip.setStatus(1);
        userVip.setSourceType(sourceType);
        userVip.setSourceRefId(sourceRefId);
        userVip.setOperatorId(operatorId);
        userVip.setRemark(remark);
        userVip.setCreatedAt(now);
        userVip.setUpdatedAt(now);
        userVipMapper.insert(userVip);
    }

    private void insertVipAdjustLog(AppUser before, AppUser after, String action, Integer days, String reason, Long operatorId, LocalDateTime now) {
        VipAdjustLog log = new VipAdjustLog();
        log.setUserId(after.getId());
        log.setAction(action);
        log.setBeforeExpireTime(before.getVipExpireTime());
        log.setAfterExpireTime(after.getVipExpireTime());
        log.setBeforeStatus(String.valueOf(before.getVipStatus()));
        log.setAfterStatus(String.valueOf(after.getVipStatus()));
        log.setDays(days);
        log.setReason(reason);
        log.setOperatorId(operatorId == null ? 1L : operatorId);
        log.setCreatedAt(now);
        vipAdjustLogMapper.insert(log);
    }

    private void audit(String action, Long userId, Long codeId, Long recordId, String beforeJson, String afterJson,
                       Long operatorId, String reason, String requestId, LocalDateTime now) {
        if (StringUtils.hasText(requestId) && operationAuditMapper.selectByRequestId(requestId) != null) {
            return;
        }
        VipOperationAudit audit = new VipOperationAudit();
        audit.setAction(action);
        audit.setTargetUserId(userId);
        audit.setTargetCodeId(codeId);
        audit.setTargetInvitationRecordId(recordId);
        audit.setBeforeJson(toJsonValue(beforeJson));
        audit.setAfterJson(toJsonValue(afterJson));
        audit.setOperatorId(operatorId);
        audit.setReason(reason);
        audit.setRequestId(StringUtils.hasText(requestId) ? requestId : null);
        audit.setCreatedAt(now);
        operationAuditMapper.insert(audit);
    }

    private LoginResult result(AppUser user, boolean newAccount) {
        LoginResult result = new LoginResult();
        result.setUser(user);
        result.setNewAccount(newAccount);
        result.setInviteCodeApplied(false);
        VipInvitationCode code = invitationCodeMapper.selectCurrentByOwner(user.getId());
        if (code != null) {
            result.setExclusiveInviteCode(code.getCode());
            result.setInviteQuotaLeft(code.getRemainingQuota());
        }
        return result;
    }

    private boolean isUsable(VipInvitationCode code) {
        return code != null && "ENABLED".equals(code.getStatus()) && safe(code.getRemainingQuota()) > 0;
    }

    private boolean isVip(AppUser user) {
        return user != null && user.getVipExpireTime() != null && user.getVipExpireTime().isAfter(LocalDateTime.now());
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private String generateCode() {
        return "VIP" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private LocalDateTime parseExpireAt(String value) {
        if (!StringUtils.hasText(value)) {
            return PERMANENT_EXPIRE_AT;
        }
        return LocalDateTime.parse(value);
    }

    private String userJson(AppUser user) {
        if (user == null) {
            return null;
        }
        return "{\"id\":" + user.getId() + ",\"vipStatus\":" + user.getVipStatus()
                + ",\"vipExpireTime\":\"" + user.getVipExpireTime() + "\"}";
    }

    private String quotaJson(VipInvitationCode code) {
        return "{\"totalQuota\":" + code.getTotalQuota() + ",\"usedQuota\":" + code.getUsedQuota()
                + ",\"remainingQuota\":" + code.getRemainingQuota() + "}";
    }

    private String toJsonValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || "null".equals(trimmed)) {
            return trimmed;
        }
        return "\"" + trimmed.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private boolean hasRequest(String requestId) {
        return StringUtils.hasText(requestId) && operationAuditMapper.selectByRequestId(requestId) != null;
    }
}
