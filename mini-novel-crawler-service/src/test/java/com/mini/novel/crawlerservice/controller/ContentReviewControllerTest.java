package com.mini.novel.crawlerservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentReviewControllerTest {
    @Test void pendingWithoutContentIsMissing() {
        assertThat(ContentReviewController.reviewState("4", false)).isEqualTo("MISSING");
    }
    @Test void pendingWithIsolatedContentIsReviewable() {
        assertThat(ContentReviewController.reviewState("4", true)).isEqualTo("PENDING_REVIEW");
    }
    @Test void rejectedChaptersStayRejected() {
        assertThat(ContentReviewController.reviewState("5", true)).isEqualTo("REVIEW_REJECTED");
    }
    @Test void batchIdsPreserveOrderAndRemoveDuplicates() {
        assertThat(ContentReviewController.uniqueBatchIds(List.of(8L, 3L, 8L, 5L)))
                .containsExactly(8L, 3L, 5L);
    }
}
