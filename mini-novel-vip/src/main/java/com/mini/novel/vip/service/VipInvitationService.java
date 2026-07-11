package com.mini.novel.vip.service;

import com.mini.novel.user.entity.AppUser;
import com.mini.novel.vip.entity.VipInvitationCode;
import com.mini.novel.vip.entity.VipInvitationRecord;
import com.mini.novel.vip.entity.VipOperationAudit;
import java.util.List;

public interface VipInvitationService {
    LoginResult loginOrCreate(String mobile, String invitationCode, boolean confirmCreateNormal);

    VipAdminResult adjustVip(Long userId, String action, Integer days, String expireAt, Long operatorId,
                             String reason, String requestId);

    VipInvitationCode enableCode(Long codeId, Long operatorId, String reason, String requestId);

    VipInvitationCode disableCode(Long codeId, Long operatorId, String reason, String requestId);

    VipInvitationCode reissueCode(Long userId, Long operatorId, String reason, String requestId);

    VipInvitationCode updateQuota(Long codeId, Integer totalQuota, Long operatorId, String reason, String requestId);

    VipInvitationCode currentCode(Long userId);

    List<VipInvitationRecord> records(Long userId);

    List<VipOperationAudit> audits(Long userId);

    class LoginResult {
        private AppUser user;
        private boolean newAccount;
        private boolean inviteCodeApplied;
        private Integer inviteQuotaLeft;
        private String exclusiveInviteCode;
        private String loginErrorCode;
        private String message;

        public AppUser getUser() { return user; }
        public void setUser(AppUser user) { this.user = user; }
        public boolean isNewAccount() { return newAccount; }
        public void setNewAccount(boolean newAccount) { this.newAccount = newAccount; }
        public boolean isInviteCodeApplied() { return inviteCodeApplied; }
        public void setInviteCodeApplied(boolean inviteCodeApplied) { this.inviteCodeApplied = inviteCodeApplied; }
        public Integer getInviteQuotaLeft() { return inviteQuotaLeft; }
        public void setInviteQuotaLeft(Integer inviteQuotaLeft) { this.inviteQuotaLeft = inviteQuotaLeft; }
        public String getExclusiveInviteCode() { return exclusiveInviteCode; }
        public void setExclusiveInviteCode(String exclusiveInviteCode) { this.exclusiveInviteCode = exclusiveInviteCode; }
        public String getLoginErrorCode() { return loginErrorCode; }
        public void setLoginErrorCode(String loginErrorCode) { this.loginErrorCode = loginErrorCode; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    class VipAdminResult {
        private AppUser user;
        private VipInvitationCode invitationCode;

        public AppUser getUser() { return user; }
        public void setUser(AppUser user) { this.user = user; }
        public VipInvitationCode getInvitationCode() { return invitationCode; }
        public void setInvitationCode(VipInvitationCode invitationCode) { this.invitationCode = invitationCode; }
    }
}
