package com.mini.novel.vip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class VipInvitationPolicyTest {
    @Test void appliesSafeDefaults() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 14, 0, 0);
        assertThat(VipInvitationPolicy.maxUses(null)).isEqualTo(3);
        assertThat(VipInvitationPolicy.expiresAt(null, now)).isEqualTo(now.plusDays(30));
    }
    @Test void rejectsInvalidLimitsAndPastExpiry() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 14, 0, 0);
        assertThatThrownBy(() -> VipInvitationPolicy.maxUses(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VipInvitationPolicy.expiresAt("2026-07-13T23:59:59", now)).isInstanceOf(IllegalArgumentException.class);
    }
}
