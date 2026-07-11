package com.mini.novel.crawler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mini.novel.crawler.entity.CrawlerAuthorizedBook;
import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.time.LocalDate;
import org.springframework.util.StringUtils;

/** Source-level authorization for adult (AUTHORIZED_VIP) sources only. */
public final class CompanyAuthorization {
    private static final ObjectMapper JSON = new ObjectMapper();
    private CompanyAuthorization() {}

    public static ObjectNode read(CrawlerSourceConfig source) {
        try {
            JsonNode root = JSON.readTree(source.ruleConfigJson == null ? "{}" : source.ruleConfigJson);
            JsonNode value = root.path("companyAuthorization");
            return value.isObject() ? (ObjectNode) value : JSON.createObjectNode();
        } catch (Exception e) { throw new IllegalArgumentException("Invalid source rule_config_json.", e); }
    }

    public static boolean isActive(CrawlerSourceConfig source, JsonNode a, LocalDate today) {
        if (!"AUTHORIZED_VIP".equals(source.sourceType) || !StringUtils.hasText(a.path("proofRef").asText())) return false;
        LocalDate from = date(a, "validFrom"), until = date(a, "validUntil");
        return from != null && until != null && !today.isBefore(from) && !today.isAfter(until);
    }

    public static void apply(CrawlerAuthorizedBook book, JsonNode a) {
        boolean preserveManualClose = "AUTHORIZED".equals(book.authorizationStatus);
        book.authorizationStatus = "AUTHORIZED";
        book.reviewStatus = "APPROVED";
        book.proofRef = a.path("proofRef").asText();
        book.authorizationNote = "Source company authorization: " + book.proofRef;
        book.allowCrawlChapters = permission(book.allowCrawlChapters, preserveManualClose, a, "allowCrawlChapters");
        book.allowStore = permission(book.allowStore, preserveManualClose, a, "allowStore");
        book.allowDisplay = permission(book.allowDisplay, preserveManualClose, a, "allowDisplay");
        book.allowVipDisplay = permission(book.allowVipDisplay, preserveManualClose, a, "allowVipDisplay") && Boolean.TRUE.equals(book.allowDisplay);
    }

    // Existing false on an individually authorized book is an explicit manual close and remains closed.
    private static boolean permission(Boolean current, boolean preserveManualClose, JsonNode a, String key) {
        return !(preserveManualClose && Boolean.FALSE.equals(current)) && a.path(key).asBoolean(false);
    }
    private static LocalDate date(JsonNode n, String key) {
        try { return LocalDate.parse(n.path(key).asText()); } catch (Exception ignored) { return null; }
    }
}
