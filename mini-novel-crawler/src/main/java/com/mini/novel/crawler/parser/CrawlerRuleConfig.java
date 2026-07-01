package com.mini.novel.crawler.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.util.StringUtils;

public class CrawlerRuleConfig {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final JsonNode root;

    private CrawlerRuleConfig(JsonNode root) {
        this.root = root;
    }

    public static CrawlerRuleConfig from(CrawlerSourceConfig source) {
        if (source == null || !StringUtils.hasText(source.ruleConfigJson)) {
            return new CrawlerRuleConfig(null);
        }
        try {
            return new CrawlerRuleConfig(OBJECT_MAPPER.readTree(source.ruleConfigJson));
        } catch (Exception ex) {
            return new CrawlerRuleConfig(null);
        }
    }

    public boolean enabled() {
        return root != null && !root.isMissingNode() && !root.isNull();
    }

    public String text(String... paths) {
        JsonNode node = firstNode(paths);
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    public int intValue(int defaultValue, String... paths) {
        JsonNode node = firstNode(paths);
        return node == null || node.isMissingNode() || node.isNull() ? defaultValue : node.asInt(defaultValue);
    }

    public long longValue(long defaultValue, String... paths) {
        JsonNode node = firstNode(paths);
        return node == null || node.isMissingNode() || node.isNull() ? defaultValue : node.asLong(defaultValue);
    }

    public boolean boolValue(boolean defaultValue, String... paths) {
        JsonNode node = firstNode(paths);
        return node == null || node.isMissingNode() || node.isNull() ? defaultValue : node.asBoolean(defaultValue);
    }

    public List<String> list(String... paths) {
        JsonNode node = firstNode(paths);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("");
                if (StringUtils.hasText(value)) {
                    values.add(value);
                }
            }
        } else if (node.isTextual() && StringUtils.hasText(node.asText())) {
            values.add(node.asText());
        }
        return values;
    }

    private JsonNode firstNode(String... paths) {
        if (paths == null) {
            return null;
        }
        for (String path : paths) {
            JsonNode node = node(path);
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private JsonNode node(String path) {
        if (root == null || !StringUtils.hasText(path)) {
            return null;
        }
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            current = current == null ? null : current.path(part);
        }
        return current;
    }
}
