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

    public String text(String path) {
        JsonNode node = node(path);
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    public int intValue(String path, int defaultValue) {
        JsonNode node = node(path);
        return node == null || node.isMissingNode() || node.isNull() ? defaultValue : node.asInt(defaultValue);
    }

    public List<String> list(String path) {
        JsonNode node = node(path);
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
