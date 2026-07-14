package com.mini.novel.vip.service;

import java.time.LocalDateTime;

public final class VipInvitationPolicy {
    public static final int DEFAULT_MAX_USES = 3;
    private VipInvitationPolicy() {}
    public static int maxUses(Integer value) {
        int result = value == null ? DEFAULT_MAX_USES : value;
        if (result < 1 || result > 1000) throw new IllegalArgumentException("maxUses must be between 1 and 1000.");
        return result;
    }
    public static LocalDateTime expiresAt(String value, LocalDateTime now) {
        if (value == null || value.isBlank()) return now.plusDays(30);
        LocalDateTime result = LocalDateTime.parse(value);
        if (!result.isAfter(now)) throw new IllegalArgumentException("expiresAt must be in the future.");
        return result;
    }
    public static boolean expired(LocalDateTime expiresAt, LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
