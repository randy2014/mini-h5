package com.mini.novel.crawlerservice.service;

import java.util.Set;

public final class AuthorizedRawRepairPlanner {
    private static final Set<String> MANUAL_STATUSES = Set.of("CONTENT_READY", "REVIEW_REJECTED");
    private static final Set<String> READY_STATUSES = Set.of("CONTENT_READY", "PENDING_REVIEW");

    private AuthorizedRawRepairPlanner() {
    }

    public static String chooseStatus(String current, String incoming) {
        int currentPriority = statusPriority(current);
        int incomingPriority = statusPriority(incoming);
        return incomingPriority > currentPriority ? incoming : current;
    }

    public static boolean shouldPromoteStatus(String current, String incoming) {
        return statusPriority(incoming) > statusPriority(current);
    }

    public static boolean shouldMoveContent(boolean targetHasContent, boolean duplicateHasContent) {
        return !targetHasContent && duplicateHasContent;
    }

    public static String repairJsonPatch(long canonicalBookRawId, String reason) {
        return "{\"canonicalBookRawId\":" + canonicalBookRawId
                + ",\"repairStatus\":\"DUPLICATE_RAW\""
                + ",\"reason\":\"" + escape(reason) + "\"}";
    }

    private static int statusPriority(String status) {
        if (status == null) {
            return 0;
        }
        if (MANUAL_STATUSES.contains(status)) {
            return 30;
        }
        if (READY_STATUSES.contains(status)) {
            return 20;
        }
        if ("RISK_BLOCKED".equals(status)) {
            return 10;
        }
        return 5;
    }

    private static String escape(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
