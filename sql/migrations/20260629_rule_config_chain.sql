CREATE DATABASE IF NOT EXISTS mini_novel_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @add_rule_config_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE mini_novel_crawler.crawl_source ADD COLUMN rule_config_json JSON NULL COMMENT ''采集规则 JSON，定义榜单、详情、目录、章节分页、清洗和质量校验规则'' AFTER auth_mode',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'mini_novel_crawler'
    AND TABLE_NAME = 'crawl_source'
    AND COLUMN_NAME = 'rule_config_json'
);

PREPARE add_rule_config_stmt FROM @add_rule_config_sql;
EXECUTE add_rule_config_stmt;
DEALLOCATE PREPARE add_rule_config_stmt;
