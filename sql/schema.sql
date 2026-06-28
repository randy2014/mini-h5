CREATE DATABASE IF NOT EXISTS mini_novel DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mini_novel;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS novel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(128) NOT NULL,
  author VARCHAR(64) NOT NULL DEFAULT '',
  cover_url VARCHAR(512),
  intro TEXT,
  category_id BIGINT,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 连载, 2 完结, 0 下架',
  word_count BIGINT NOT NULL DEFAULT 0,
  latest_chapter_id BIGINT,
  latest_chapter_title VARCHAR(255),
  source_url VARCHAR(512),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_category_id (category_id),
  KEY idx_updated_at (updated_at),
  KEY idx_title_author (title, author)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chapter (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  novel_id BIGINT NOT NULL,
  chapter_no INT NOT NULL,
  title VARCHAR(255) NOT NULL,
  content LONGTEXT,
  is_vip TINYINT(1) NOT NULL DEFAULT 0,
  price_coin INT NOT NULL DEFAULT 0,
  source_url VARCHAR(512),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_novel_chapter_no (novel_id, chapter_no),
  KEY idx_novel_id (novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  nickname VARCHAR(64) NOT NULL DEFAULT '',
  avatar VARCHAR(512),
  mobile VARCHAR(32),
  email VARCHAR(128),
  password_hash VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 1,
  vip_expire_time DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mobile (mobile),
  UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_bookshelf (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  novel_id BIGINT NOT NULL,
  last_chapter_id BIGINT,
  progress INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_novel (user_id, novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_read_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  novel_id BIGINT NOT NULL,
  chapter_id BIGINT NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_read_at (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vip_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  duration_days INT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  original_price DECIMAL(10,2),
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort INT NOT NULL DEFAULT 0,
  description VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_vip (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  vip_plan_id BIGINT,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  source_order_id BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_end_time (user_id, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vip_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  vip_plan_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  pay_status TINYINT NOT NULL DEFAULT 0,
  pay_channel VARCHAR(32),
  paid_at DATETIME,
  expire_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crawl_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  base_url VARCHAR(512) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  rule_config_json JSON,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crawl_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT NOT NULL,
  novel_id BIGINT,
  task_type VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待执行, 1 成功, 2 失败',
  message VARCHAR(1024),
  retry_count INT NOT NULL DEFAULT 0,
  started_at DATETIME,
  finished_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_source_created_at (source_id, created_at),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS novel_identity (
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

CREATE TABLE IF NOT EXISTS novel_source_mapping (
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

CREATE TABLE IF NOT EXISTS chapter_source_mapping (
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
USE mini_novel_crawler;

CREATE TABLE IF NOT EXISTS crawl_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  base_url VARCHAR(512) NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
  auth_mode VARCHAR(32) NOT NULL DEFAULT 'NONE',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  priority INT NOT NULL DEFAULT 100,
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_code (source_code),
  KEY idx_enabled_priority (enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crawl_rank_source (
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

CREATE TABLE IF NOT EXISTS crawl_schedule (
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

CREATE TABLE IF NOT EXISTS crawl_source_credential (
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

CREATE TABLE IF NOT EXISTS crawl_task_v2 (
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

CREATE TABLE IF NOT EXISTS crawl_book_raw (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
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
  KEY idx_title_author (title, author),
  KEY idx_content_status (content_status),
  KEY idx_rank_heat (rank_type, heat_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crawl_chapter_raw (
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

CREATE TABLE IF NOT EXISTS crawl_content_raw (
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

CREATE TABLE IF NOT EXISTS crawl_merge_task (
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

CREATE TABLE IF NOT EXISTS crawl_merge_item (
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

USE mini_novel;

ALTER TABLE novel ADD COLUMN vip_required TINYINT(1) NOT NULL DEFAULT 0 AFTER status;
ALTER TABLE novel ADD COLUMN free_chapter_count INT NOT NULL DEFAULT 0 AFTER vip_required;
ALTER TABLE novel ADD COLUMN offline_reason VARCHAR(255) AFTER source_url;
ALTER TABLE novel ADD COLUMN offline_at DATETIME AFTER offline_reason;
ALTER TABLE novel ADD COLUMN operator_id BIGINT AFTER offline_at;
ALTER TABLE app_user ADD COLUMN vip_status TINYINT NOT NULL DEFAULT 0 AFTER status;

CREATE TABLE IF NOT EXISTS chapter_content (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  chapter_id BIGINT NOT NULL,
  content LONGTEXT,
  content_hash VARCHAR(64),
  storage_type VARCHAR(16) NOT NULL DEFAULT 'MYSQL',
  content_path VARCHAR(512),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chapter_id (chapter_id),
  KEY idx_content_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chapter_segment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  chapter_id BIGINT NOT NULL,
  segment_no INT NOT NULL,
  content LONGTEXT,
  word_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chapter_segment (chapter_id, segment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vip_adjust_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  before_expire_time DATETIME,
  after_expire_time DATETIME,
  before_status VARCHAR(32),
  after_status VARCHAR(32),
  days INT,
  reason VARCHAR(255),
  operator_id BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_created (user_id, created_at),
  KEY idx_operator_created (operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO category (name, sort) VALUES
('玄幻', 10),
('都市', 20),
('仙侠', 30),
('悬疑', 40)
ON DUPLICATE KEY UPDATE sort = VALUES(sort);

INSERT INTO vip_plan (name, duration_days, price, original_price, enabled, sort, description) VALUES
('月度 VIP', 30, 19.90, 29.90, 1, 10, '30 天 VIP 阅读权益'),
('年度 VIP', 365, 198.00, 298.00, 1, 20, '365 天 VIP 阅读权益')
ON DUPLICATE KEY UPDATE enabled = VALUES(enabled);

INSERT INTO novel (id, title, author, cover_url, intro, category_id, status, word_count, latest_chapter_id, latest_chapter_title, source_url) VALUES
(1, '长夜书灯', '示例作者', 'https://dummyimage.com/300x420/20232a/ffffff&text=Mini+Novel', '这是用于本地 Docker 环境预览的示例小说。', 1, 1, 8200, 2, '第二章 雨声入梦', 'demo://novel/1')
ON DUPLICATE KEY UPDATE title = VALUES(title), latest_chapter_id = VALUES(latest_chapter_id), latest_chapter_title = VALUES(latest_chapter_title);

INSERT INTO chapter (id, novel_id, chapter_no, title, content, is_vip, price_coin, source_url) VALUES
(1, 1, 1, '第一章 灯下旧纸', '夜色压在窗外，书页却像刚醒来的河。少年翻开第一行字，忽然听见远处钟声轻轻一响。', 0, 0, 'demo://chapter/1'),
(2, 1, 2, '第二章 雨声入梦', '雨声从屋檐落下，也落进梦里。这一章被标记为 VIP，用来测试会员阅读权限。', 1, 0, 'demo://chapter/2')
ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content), is_vip = VALUES(is_vip);

INSERT INTO app_user (id, nickname, mobile, status, vip_expire_time) VALUES
(1, '普通读者', '13800000001', 1, NULL),
(2, 'VIP读者', '13800000002', 1, DATE_ADD(NOW(), INTERVAL 30 DAY))
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), vip_expire_time = VALUES(vip_expire_time);

INSERT INTO user_bookshelf (user_id, novel_id, last_chapter_id, progress) VALUES
(1, 1, 1, 35),
(2, 1, 2, 85)
ON DUPLICATE KEY UPDATE last_chapter_id = VALUES(last_chapter_id), progress = VALUES(progress);

INSERT INTO crawl_source (id, name, base_url, enabled, rule_config_json) VALUES
(1, '示例书源', 'https://example.com', 1, JSON_OBJECT('note', '后续接入具体站点解析规则'))
ON DUPLICATE KEY UPDATE name = VALUES(name), enabled = VALUES(enabled);
