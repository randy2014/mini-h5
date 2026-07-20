package com.mini.novel.vip.service;

import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.vip.entity.VipInvitationCode;
import com.mini.novel.vip.entity.VipInvitationRecord;
import com.mini.novel.vip.mapper.UserVipMapper;
import com.mini.novel.vip.mapper.VipAdjustLogMapper;
import com.mini.novel.vip.mapper.VipInvitationCodeMapper;
import com.mini.novel.vip.mapper.VipInvitationRecordMapper;
import com.mini.novel.vip.mapper.VipOperationAuditMapper;
import com.mini.novel.vip.service.impl.VipInvitationServiceImpl;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VipInvitationLoginServiceTest {
    private static final String PASSWORD = "LocalTestPassword!1";
    private AppUserMapper appUserMapper;
    private UserVipMapper userVipMapper;
    private VipInvitationCodeMapper codeMapper;
    private VipInvitationRecordMapper recordMapper;
    private VipOperationAuditMapper auditMapper;
    private VipInvitationService service;

    @BeforeEach
    void setUp() {
        appUserMapper = mock(AppUserMapper.class);
        userVipMapper = mock(UserVipMapper.class);
        codeMapper = mock(VipInvitationCodeMapper.class);
        recordMapper = mock(VipInvitationRecordMapper.class);
        auditMapper = mock(VipOperationAuditMapper.class);
        service = new VipInvitationServiceImpl(
                appUserMapper,
                userVipMapper,
                mock(VipAdjustLogMapper.class),
                codeMapper,
                recordMapper,
                auditMapper);
    }

    @Test
    void blankInvitationCreatesOrdinaryUser() {
        stubNewUser(11L);

        VipInvitationService.LoginResult result = service.loginOrCreate("13800000000", PASSWORD, " ");

        assertFalse(result.getUser().getVipStatus() != null && result.getUser().getVipStatus() > 0);
        assertNull(result.getLoginErrorCode());
        assertTrue(result.getUser().getPasswordHash().startsWith("$2"));
        assertFalse(result.getUser().getPasswordHash().contains(PASSWORD));
        verify(codeMapper, never()).selectByCodeForUpdate(any());
    }

    @ParameterizedTest
    @MethodSource("invalidCodes")
    void unusableInvitationDoesNotBlockLogin(VipInvitationCode code) {
        stubNewUser(12L);
        when(recordMapper.selectByInviteeForUpdate(12L)).thenReturn(null);
        when(codeMapper.selectByCodeForUpdate("PROMO")).thenReturn(code);

        VipInvitationService.LoginResult result = service.loginOrCreate("13800000001", PASSWORD, "PROMO");

        assertFalse(result.getUser().getVipStatus() != null && result.getUser().getVipStatus() > 0);
        assertEquals("INVITE_INVALID", result.getLoginErrorCode());
        assertEquals("邀请码无效，已按普通用户登录", result.getMessage());
        verify(recordMapper, never()).insert(any(VipInvitationRecord.class));
    }

    static VipInvitationCode[] invalidCodes() {
        return new VipInvitationCode[]{
                null,
                code("DISABLED", 1, LocalDateTime.now().plusDays(1)),
                code("ENABLED", 1, LocalDateTime.now().minusSeconds(1)),
                code("ENABLED", 0, LocalDateTime.now().plusDays(1))
        };
    }

    @Test
    void validInvitationGrantsVipOnceAndAuditsFingerprint() {
        stubNewUser(21L);
        VipInvitationCode code = code("ENABLED", 2, LocalDateTime.now().plusDays(1));
        code.setId(31L);
        code.setOwnerUserId(99L);
        code.setCode("SECRET-CODE");
        when(recordMapper.selectByInviteeForUpdate(21L))
                .thenReturn((VipInvitationRecord) null)
                .thenReturn(null);
        when(codeMapper.selectByCodeForUpdate("SECRET-CODE")).thenReturn(code);
        when(codeMapper.selectCurrentByOwner(21L)).thenReturn(null, code("ENABLED", 3, null));
        when(appUserMapper.selectById(21L)).thenAnswer(ignored -> vipUser(21L));

        VipInvitationService.LoginResult result = service.loginOrCreate("13800000002", PASSWORD, "SECRET-CODE");

        assertTrue(result.getUser().getVipStatus() > 0);
        assertTrue(result.isInviteCodeApplied());
        assertEquals(1, code.getRemainingQuota());
        ArgumentCaptor<VipInvitationRecord> record = ArgumentCaptor.forClass(VipInvitationRecord.class);
        verify(recordMapper).insert(record.capture());
        assertTrue(record.getValue().getCodeSnapshot().startsWith("sha256:"));
        assertFalse(record.getValue().getCodeSnapshot().contains("SECRET-CODE"));
        ArgumentCaptor<com.mini.novel.vip.entity.VipOperationAudit> audit =
                ArgumentCaptor.forClass(com.mini.novel.vip.entity.VipOperationAudit.class);
        verify(auditMapper).insert(audit.capture());
        assertFalse(audit.getValue().getAfterJson().contains("SECRET-CODE"));
    }

    @Test
    void existingRedemptionIsIdempotentAndDoesNotConsumeAgain() {
        stubNewUser(22L);
        VipInvitationRecord existing = new VipInvitationRecord();
        existing.setInviteeUserId(22L);
        when(recordMapper.selectByInviteeForUpdate(22L)).thenReturn(existing);
        when(codeMapper.selectCurrentByOwner(22L)).thenReturn(code("ENABLED", 3, null));
        when(appUserMapper.selectById(22L)).thenAnswer(ignored -> vipUser(22L));

        VipInvitationService.LoginResult result = service.loginOrCreate("13800000003", PASSWORD, "PROMO");

        assertTrue(result.getUser().getVipStatus() > 0);
        assertTrue(result.isInviteCodeApplied());
        verify(codeMapper, never()).selectByCodeForUpdate(any());
        verify(recordMapper, never()).insert(any(VipInvitationRecord.class));
    }

    @Test
    void concurrentRetryIsRecheckedAfterCodeLockAndDoesNotConsumeAgain() {
        stubNewUser(24L);
        VipInvitationRecord committedByFirstRequest = new VipInvitationRecord();
        committedByFirstRequest.setInviteeUserId(24L);
        when(recordMapper.selectByInviteeForUpdate(24L)).thenReturn(null, committedByFirstRequest);
        when(codeMapper.selectByCodeForUpdate("PROMO")).thenReturn(code("ENABLED", 1, null));
        when(codeMapper.selectCurrentByOwner(24L)).thenReturn(null, code("ENABLED", 3, null));
        when(appUserMapper.selectById(24L)).thenAnswer(ignored -> vipUser(24L));

        VipInvitationService.LoginResult result = service.loginOrCreate("13800000005", PASSWORD, "PROMO");

        assertTrue(result.isInviteCodeApplied());
        verify(recordMapper, never()).insert(any(VipInvitationRecord.class));
        verify(codeMapper, never()).updateById(any(VipInvitationCode.class));
    }

    @Test
    void existingVipIsNeverDowngradedOrCharged() {
        AppUser existing = vipUser(23L);
        when(appUserMapper.selectByMobileForUpdate("13800000004")).thenReturn(existing);
        when(codeMapper.selectCurrentByOwner(23L)).thenReturn(code("ENABLED", 3, null));
        when(codeMapper.selectByCodeForUpdate("INVALID")).thenReturn(null);

        VipInvitationService.LoginResult result = service.loginOrCreate("13800000004", PASSWORD, "INVALID");

        assertTrue(result.getUser().getVipStatus() > 0);
        assertEquals("INVITE_INVALID", result.getLoginErrorCode());
        verify(appUserMapper, never()).updateById(any(AppUser.class));
    }

    @Test
    void redemptionMappersDeclareDatabaseRowLocks() throws Exception {
        Select codeLock = VipInvitationCodeMapper.class
                .getMethod("selectByCodeForUpdate", String.class).getAnnotation(Select.class);
        Select inviteeLock = VipInvitationRecordMapper.class
                .getMethod("selectByInviteeForUpdate", Long.class).getAnnotation(Select.class);

        assertNotNull(codeLock);
        assertNotNull(inviteeLock);
        assertTrue(String.join(" ", codeLock.value()).toUpperCase().contains("FOR UPDATE"));
        assertTrue(String.join(" ", inviteeLock.value()).toUpperCase().contains("FOR UPDATE"));
    }

    @Test
    void wrongPasswordIsRejectedBeforeInvitationProcessing() {
        AppUser existing = ordinaryUser(25L);
        existing.setPasswordHash(new BCryptPasswordEncoder().encode(PASSWORD));
        when(appUserMapper.selectByMobileForUpdate("13800000006")).thenReturn(existing);

        com.mini.novel.common.exception.BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                com.mini.novel.common.exception.BusinessException.class,
                () -> service.loginOrCreate("13800000006", "WrongPassword!1", "PROMO"));

        assertEquals(com.mini.novel.common.exception.ErrorCode.UNAUTHORIZED, error.getCode());
        verify(codeMapper, never()).selectByCodeForUpdate(any());
    }

    private void stubNewUser(long id) {
        when(appUserMapper.selectByMobileForUpdate(any())).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(id);
            return 1;
        }).when(appUserMapper).insert(any(AppUser.class));
        when(codeMapper.selectCurrentByOwner(anyLong())).thenReturn(null);
    }

    private static VipInvitationCode code(String status, int remaining, LocalDateTime expiresAt) {
        VipInvitationCode code = new VipInvitationCode();
        code.setStatus(status);
        code.setRemainingQuota(remaining);
        code.setUsedQuota(0);
        code.setTotalQuota(remaining);
        code.setExpiresAt(expiresAt);
        return code;
    }

    private static AppUser vipUser(long id) {
        AppUser user = ordinaryUser(id);
        user.setPasswordHash(new BCryptPasswordEncoder().encode(PASSWORD));
        user.setVipStatus(2);
        user.setVipExpireTime(LocalDateTime.of(2099, 12, 31, 23, 59));
        return user;
    }

    private static AppUser ordinaryUser(long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setStatus(1);
        user.setVipStatus(0);
        return user;
    }
}
