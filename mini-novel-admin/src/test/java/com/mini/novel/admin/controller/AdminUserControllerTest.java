package com.mini.novel.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mini.novel.admin.service.InvitationQrCodeService;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.vip.mapper.VipAdjustLogMapper;
import com.mini.novel.vip.service.VipInvitationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminUserControllerTest {
    @Test
    void qrRequiresAdminAndReturnsNoStorePng() {
        InvitationQrCodeService qrService = mock(InvitationQrCodeService.class);
        when(qrService.generate(9L)).thenReturn(new byte[] {1, 2, 3});
        AdminUserController controller = new AdminUserController(mock(AppUserMapper.class),
                mock(VipAdjustLogMapper.class), mock(VipInvitationService.class), qrService, "admin-secret");

        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> controller.invitationQrCode(9L, null));
        assertEquals(HttpStatus.UNAUTHORIZED, missing.getStatusCode());

        var response = controller.invitationQrCode(9L, "admin-secret");
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertEquals("no-store, must-revalidate", response.getHeaders().getCacheControl());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    }
}
