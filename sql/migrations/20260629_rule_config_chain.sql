CREATE DATABASE IF NOT EXISTS mini_novel_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE mini_novel_crawler.crawl_source
  ADD COLUMN IF NOT EXISTS rule_config_json JSON NULL COMMENT '采集规则 JSON，定义榜单、详情、目录、章节分页、清洗和质量校验规则'
  AFTER auth_mode;
