package com.mini.novel.api.service;

import com.mini.novel.api.model.CaptchaVo;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {
    private static final String CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final Duration RATE_TTL = Duration.ofMinutes(1);
    private static final int RATE_LIMIT = 20;
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;

    public CaptchaService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public CaptchaVo create(String clientIp) {
        enforceRateLimit(clientIp);
        String id = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode();
        redis.opsForValue().set("auth:captcha:" + id, code, CAPTCHA_TTL);
        return new CaptchaVo(id, "data:image/png;base64," + render(code));
    }

    public String verify(String id, String input) {
        String expected = id == null ? null : redis.opsForValue().getAndDelete("auth:captcha:" + id.trim());
        String actual = input == null ? "" : input.trim().toUpperCase();
        if (expected == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "验证码错误或已过期，请刷新后重试");
        }
        return expected;
    }

    public void restore(String id, String code) {
        if (id != null && code != null) {
            redis.opsForValue().set("auth:captcha:" + id.trim(), code, CAPTCHA_TTL);
        }
    }

    private void enforceRateLimit(String clientIp) {
        String key = "auth:captcha:rate:" + (clientIp == null ? "unknown" : clientIp);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key, RATE_TTL);
        if (count != null && count > RATE_LIMIT) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请求过于频繁，请稍后再试");
        }
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(4);
        for (int i = 0; i < 4; i++) code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        return code.toString();
    }

    private String render(String code) {
        BufferedImage image = new BufferedImage(120, 44, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 248, 247));
            graphics.fillRect(0, 0, 120, 44);
            graphics.setStroke(new BasicStroke(1.4f));
            for (int i = 0; i < 7; i++) {
                graphics.setColor(new Color(80 + random.nextInt(130), 80 + random.nextInt(130), 80 + random.nextInt(130)));
                graphics.drawLine(random.nextInt(120), random.nextInt(44), random.nextInt(120), random.nextInt(44));
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            for (int i = 0; i < code.length(); i++) {
                graphics.setColor(new Color(random.nextInt(80), random.nextInt(100), random.nextInt(100)));
                graphics.drawString(String.valueOf(code.charAt(i)), 10 + i * 27, 33 + random.nextInt(5) - 2);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("验证码生成失败", ex);
        } finally {
            graphics.dispose();
        }
    }
}
