package com.mini.novel.crawlerservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
}
