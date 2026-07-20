package com.mini.novel.admin.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.vip.entity.VipInvitationCode;
import com.mini.novel.vip.mapper.VipInvitationCodeMapper;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationQrCodeServiceTest {
    @Mock
    private VipInvitationCodeMapper codeMapper;

    @Test
    void generatesPngWithTrustedLoginUrl() throws Exception {
        VipInvitationCode invitation = invitation("ENABLED", 2, LocalDateTime.now().plusDays(1));
        when(codeMapper.selectById(7L)).thenReturn(invitation);
        byte[] png = service().generate(7L);

        assertArrayEquals(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47},
                new byte[] {png[0], png[1], png[2], png[3]});
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(ImageIO.read(new ByteArrayInputStream(png)))));
        assertEquals("https://xs2026.site/h5/login?inviteCode=SAFE_CODE&redirect=%2Fh5%2Fvip",
                new MultiFormatReader().decode(bitmap).getText());
    }

    @Test
    void rejectsMissingDisabledExpiredAndExhaustedCodesWithoutDetailLeak() {
        for (VipInvitationCode invitation : new VipInvitationCode[] {
                null,
                invitation("DISABLED", 1, null),
                invitation("ENABLED", 0, null),
                invitation("ENABLED", 1, LocalDateTime.now().minusSeconds(1))}) {
            when(codeMapper.selectById(7L)).thenReturn(invitation);
            BusinessException error = assertThrows(BusinessException.class, () -> service().generate(7L));
            assertEquals("邀请码当前不可生成二维码", error.getMessage());
        }
    }

    @Test
    void rejectsUntrustedConfiguredHost() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvitationQrCodeService(codeMapper, "https://attacker.invalid"));
    }

    private InvitationQrCodeService service() {
        return new InvitationQrCodeService(codeMapper, "https://xs2026.site");
    }

    private static VipInvitationCode invitation(String status, int remaining, LocalDateTime expiresAt) {
        VipInvitationCode invitation = new VipInvitationCode();
        invitation.setCode("SAFE_CODE");
        invitation.setStatus(status);
        invitation.setRemainingQuota(remaining);
        invitation.setExpiresAt(expiresAt);
        return invitation;
    }
}
