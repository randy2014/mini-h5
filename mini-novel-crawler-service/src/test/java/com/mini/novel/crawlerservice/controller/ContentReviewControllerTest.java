package com.mini.novel.crawlerservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentReviewControllerTest {
    @Test void pendingWithoutContentIsMissing() {
        assertThat(ContentReviewController.reviewState("PENDING_REVIEW", false)).isEqualTo("MISSING");
    }
    @Test void pendingWithIsolatedContentIsReviewable() {
        assertThat(ContentReviewController.reviewState("PENDING_REVIEW", true)).isEqualTo("PENDING_REVIEW");
    }
    @Test void hardBlockCannotMasqueradeAsReviewable() {
        assertThat(ContentReviewController.reviewState("RISK_BLOCKED", true)).isEqualTo("EXPLICIT_MINOR_BLOCKED");
    }
    @Test void batchIdsPreserveOrderAndRemoveDuplicates() {
        assertThat(ContentReviewController.uniqueBatchIds(List.of(8L, 3L, 8L, 5L)))
                .containsExactly(8L, 3L, 5L);
    }
    @Test void batchReviewOnlyAllowsNamedAuthorizedAdultSources() {
        assertThat(ContentReviewController.isBatchReviewSource("h528_authorized")).isTrue();
        assertThat(ContentReviewController.isBatchReviewSource("novel69h_authorized")).isTrue();
        assertThat(ContentReviewController.isBatchReviewSource("xbookcn_authorized")).isFalse();
        assertThat(ContentReviewController.isBatchReviewSource("23qb_public")).isFalse();
    }
}
