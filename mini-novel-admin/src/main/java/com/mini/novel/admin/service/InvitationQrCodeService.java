package com.mini.novel.admin.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.vip.entity.VipInvitationCode;
import com.mini.novel.vip.mapper.VipInvitationCodeMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InvitationQrCodeService {
    private static final int IMAGE_SIZE = 320;
    private final VipInvitationCodeMapper codeMapper;
    private final String publicH5BaseUrl;

    public InvitationQrCodeService(VipInvitationCodeMapper codeMapper,
            @Value("${app.public-h5-base-url:https://xs2026.site}") String publicH5BaseUrl) {
        this.codeMapper = codeMapper;
        this.publicH5BaseUrl = validateBaseUrl(publicH5BaseUrl);
    }

    public byte[] generate(Long codeId) {
        VipInvitationCode invitation = codeMapper.selectById(codeId);
        if (!isUsable(invitation)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "邀请码当前不可生成二维码");
        }
        try {
            BitMatrix matrix = new QRCodeWriter().encode(targetUrl(invitation), BarcodeFormat.QR_CODE,
                    IMAGE_SIZE, IMAGE_SIZE, Map.of(
                            EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (WriterException | IOException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "二维码生成失败，请稍后重试");
        }
    }

    String targetUrl(VipInvitationCode invitation) {
        return publicH5BaseUrl + "/h5/login?inviteCode="
                + URLEncoder.encode(invitation.getCode(), StandardCharsets.UTF_8)
                + "&redirect=%2Fh5%2Fvip";
    }

    private boolean isUsable(VipInvitationCode invitation) {
        return invitation != null && "ENABLED".equals(invitation.getStatus())
                && invitation.getRemainingQuota() != null && invitation.getRemainingQuota() > 0
                && (invitation.getExpiresAt() == null || invitation.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    private static String validateBaseUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("app.public-h5-base-url 配置无效", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"xs2026.site".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("app.public-h5-base-url 必须使用受信任的 HTTPS 站点");
        }
        String path = uri.getPath();
        return "https://xs2026.site" + (path == null || path.equals("/") ? "" : path.replaceAll("/+$", ""));
    }
}
