package com.mini.novel.crawlerservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthorizedRawRepairPlannerTest {
    @Test
    void duplicateChapterContentMovesOnlyWhenTargetMissingContent() {
        assertThat(AuthorizedRawRepairPlanner.shouldMoveContent(false, true)).isTrue();
        assertThat(AuthorizedRawRepairPlanner.shouldMoveContent(true, true)).isFalse();
        assertThat(AuthorizedRawRepairPlanner.shouldMoveContent(false, false)).isFalse();
    }

    @Test
    void manualOrReadyStatusIsNotOverwrittenByRiskBlocked() {
        assertThat(AuthorizedRawRepairPlanner.chooseStatus("CONTENT_READY", "RISK_BLOCKED"))
                .isEqualTo("CONTENT_READY");
        assertThat(AuthorizedRawRepairPlanner.chooseStatus("REVIEW_REJECTED", "RISK_BLOCKED"))
                .isEqualTo("REVIEW_REJECTED");
        assertThat(AuthorizedRawRepairPlanner.chooseStatus("PENDING_REVIEW", "RISK_BLOCKED"))
                .isEqualTo("PENDING_REVIEW");
    }

    @Test
    void rerunPatchUsesSameCanonicalMarker() {
        assertThat(AuthorizedRawRepairPlanner.repairJsonPatch(42L, "merged"))
                .contains("\"canonicalBookRawId\":42")
                .contains("\"repairStatus\":\"DUPLICATE_RAW\"");
    }
}
