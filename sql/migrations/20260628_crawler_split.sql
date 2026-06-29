CREATE TABLE IF NOT EXISTS mini_novel.novel_identity (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  canonical_title VARCHAR(128) NOT NULL,
  canonical_author VARCHAR(64) NOT NULL DEFAULT '',
  normalized_title VARCHAR(128) NOT NULL,
  normalized_author VARCHAR(64) NOT NULL DEFAULT '',
  novel_id BIGINT,
  match_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  confidence_score INT NOT NULL DEFAULT 100,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_identity_norm (normalized_title, normalized_author),
  KEY idx_novel_id (novel_id),
  KEY idx_match_status (match_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel.novel_source_mapping (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  identity_id BIGINT NOT NULL,
  novel_id BIGINT,
  source_code VARCHAR(64) NOT NULL,
  source_book_id VARCHAR(128) NOT NULL,
  source_url VARCHAR(512),
  source_title VARCHAR(128) NOT NULL,
  source_author VARCHAR(64) NOT NULL DEFAULT '',
  content_status VARCHAR(32) NOT NULL DEFAULT 'META_ONLY',
  match_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  confidence_score INT NOT NULL DEFAULT 0,
  last_crawled_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_book (source_code, source_book_id),
  KEY idx_identity_id (identity_id),
  KEY idx_novel_id (novel_id),
  KEY idx_match_status (match_status),
  KEY idx_content_status (content_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel.chapter_source_mapping (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  novel_mapping_id BIGINT NOT NULL,
  chapter_id BIGINT,
  source_chapter_id VARCHAR(128) NOT NULL,
  source_url VARCHAR(512),
  source_title VARCHAR(255) NOT NULL,
  chapter_no INT NOT NULL,
  is_vip TINYINT(1) NOT NULL DEFAULT 0,
  content_hash CHAR(64),
  content_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_chapter (novel_mapping_id, source_chapter_id),
  KEY idx_chapter_id (chapter_id),
  KEY idx_content_status (content_status),
  KEY idx_chapter_no (novel_mapping_id, chapter_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS mini_novel_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel_crawler.crawl_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  base_url VARCHAR(512) NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
  auth_mode VARCHAR(32) NOT NULL DEFAULT 'NONE',
  rule_config_json JSON,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  priority INT NOT NULL DEFAULT 100,
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_code (source_code),
  KEY idx_enabled_priority (enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel_crawler.crawl_rank_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT NOT NULL,
  rank_name VARCHAR(64) NOT NULL,
  rank_type VARCHAR(32) NOT NULL,
  rank_url VARCHAR(512) NOT NULL,
  prefer_completed TINYINT(1) NOT NULL DEFAULT 1,
  max_books INT NOT NULL DEFAULT 50,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_source_enabled (source_id, enabled),
  KEY idx_rank_type (rank_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel_crawler.crawl_schedule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  source_id BIGINT,
  credential_id BIGINT,
  schedule_times VARCHAR(64) NOT NULL DEFAULT '00:00,08:00,14:00',
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  crawl_public TINYINT(1) NOT NULL DEFAULT 1,
  crawl_vip TINYINT(1) NOT NULL DEFAULT 0,
  auto_merge TINYINT(1) NOT NULL DEFAULT 1,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  last_run_at DATETIME,
  next_run_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_enabled_next_run (enabled, next_run_at),
  KEY idx_source_id (source_id),
  KEY idx_credential_id (credential_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel_crawler.crawl_source_credential (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  auth_mode VARCHAR(32) NOT NULL DEFAULT 'PASSWORD',
  username VARCHAR(128),
  password_cipher VARCHAR(1000),
  cookie_text TEXT,
  headers_json JSON,
  login_url VARCHAR(512),
  status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  last_check_status VARCHAR(32),
  last_check_at DATETIME,
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_source_enabled (source_id, enabled),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel_crawler.crawl_task_v2 (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_id BIGINT,
  source_id BIGINT,
  rank_source_id BIGINT,
  credential_id BIGINT,
  task_type VARCHAR(32) NOT NULL,
  trigger_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  target_url VARCHAR(512),
  total_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  fail_count INT NOT NULL DEFAULT 0,
  message VARCHAR(1000),
  started_at DATETIME,
  finished_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status_created (status, created_at),
  KEY idx_schedule_id (schedule_id),
  KEY idx_source_id (source_id),
  KEY idx_credential_id (credential_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel_crawler.crawl_book_raw (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  crawl_task_id BIGINT,
  source_code VARCHAR(64) NOT NULL,
  source_book_id VARCHAR(128) NOT NULL,
  source_url VARCHAR(512),
  title VARCHAR(128) NOT NULL,
  author VARCHAR(64) NOT NULL DEFAULT '',
  intro TEXT,
  cover_url VARCHAR(512),
  category_name VARCHAR(64),
  book_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
  word_count BIGINT NOT NULL DEFAULT 0,
  heat_score BIGINT NOT NULL DEFAULT 0,
  rank_type VARCHAR(32),
  content_status VARCHAR(32) NOT NULL DEFAULT 'META_ONLY',
  raw_json JSON,
  crawled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_book (source_code, source_book_id),
  KEY idx_crawl_task_id (crawl_task_id),
  KEY idx_title_author (title, author),
  KEY idx_content_status (content_status),
  KEY idx_rank_heat (rank_type, heat_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel_crawler.crawl_chapter_raw (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  book_raw_id BIGINT NOT NULL,
  source_chapter_id VARCHAR(128) NOT NULL,
  source_url VARCHAR(512),
  chapter_no INT NOT NULL,
  title VARCHAR(255) NOT NULL,
  is_vip TINYINT(1) NOT NULL DEFAULT 0,
  price_coin INT NOT NULL DEFAULT 0,
  content_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  content_hash CHAR(64),
  crawled_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_book_chapter (book_raw_id, source_chapter_id),
  KEY idx_book_no (book_raw_id, chapter_no),
  KEY idx_content_status (content_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel_crawler.crawl_content_raw (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  chapter_raw_id BIGINT NOT NULL,
  content LONGTEXT NOT NULL,
  content_hash CHAR(64) NOT NULL,
  content_length INT NOT NULL DEFAULT 0,
  storage_mode VARCHAR(32) NOT NULL DEFAULT 'MYSQL_LONGTEXT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chapter_content (chapter_raw_id),
  KEY idx_content_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel_crawler.crawl_merge_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  crawl_task_id BIGINT,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  total_count INT NOT NULL DEFAULT 0,
  merged_count INT NOT NULL DEFAULT 0,
  pending_review_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  message VARCHAR(1000),
  started_at DATETIME,
  finished_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status_created (status, created_at),
  KEY idx_crawl_task_id (crawl_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mini_novel_crawler.crawl_merge_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merge_task_id BIGINT NOT NULL,
  book_raw_id BIGINT NOT NULL,
  identity_id BIGINT,
  novel_id BIGINT,
  match_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  confidence_score INT NOT NULL DEFAULT 0,
  message VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_merge_status (merge_task_id, match_status),
  KEY idx_book_raw_id (book_raw_id),
  KEY idx_novel_id (novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
