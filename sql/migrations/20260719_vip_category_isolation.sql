USE mini_novel;

CREATE TABLE IF NOT EXISTS vip_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  normalized_name VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 100,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_vip_category_normalized_name (normalized_name),
  KEY idx_vip_category_enabled_sort (enabled, sort, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='独立VIP分类，不复用公共分类表';

CREATE TABLE IF NOT EXISTS novel_vip_category_mapping (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  novel_id BIGINT NOT NULL,
  vip_category_id BIGINT NOT NULL,
  source_code VARCHAR(64) NOT NULL,
  source_book_id VARCHAR(128) NOT NULL,
  source_category_name VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_novel_vip_category (novel_id),
  KEY idx_vip_category_novel (vip_category_id, novel_id),
  KEY idx_source_book (source_code, source_book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说与独立VIP分类映射';

CREATE TABLE IF NOT EXISTS vip_source_category_mapping (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_code VARCHAR(64) NOT NULL,
  source_category_name VARCHAR(64) NOT NULL,
  normalized_name VARCHAR(64) NOT NULL,
  vip_category_id BIGINT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_vip_source_category (source_code, normalized_name),
  KEY idx_vip_source_category_target (vip_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='来源分类到独立VIP分类映射';

INSERT INTO vip_category(name, normalized_name, sort, enabled)
VALUES ('其他', '其他', 999, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), sort=VALUES(sort), enabled=1;

INSERT INTO vip_category(name, normalized_name, sort, enabled)
SELECT source_category_name, normalized_name, 100, 1
FROM (
  SELECT DISTINCT
    CASE
      WHEN b.category_name IS NULL OR TRIM(b.category_name) = '' OR UPPER(TRIM(b.category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(TRIM(b.category_name), 64)
    END source_category_name,
    CASE
      WHEN b.category_name IS NULL OR TRIM(b.category_name) = '' OR UPPER(TRIM(b.category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(REPLACE(REPLACE(REPLACE(LOWER(TRIM(b.category_name)), ' ', ''), '-', ''), '_', ''), 64)
    END normalized_name
  FROM novel_source_mapping nsm
  JOIN mini_novel_crawler.crawl_book_raw b
    ON b.source_code = nsm.source_code
   AND b.source_book_id = nsm.source_book_id
  JOIN mini_novel_crawler.crawl_source s
    ON s.source_code = nsm.source_code
   AND s.source_type = 'AUTHORIZED_VIP'
  WHERE nsm.content_status = 'CONTENT_READY'
) seed
ON DUPLICATE KEY UPDATE name=VALUES(name), enabled=1, updated_at=NOW();

INSERT INTO vip_source_category_mapping(source_code, source_category_name, normalized_name, vip_category_id, enabled)
SELECT seed.source_code, seed.source_category_name, seed.normalized_name, vc.id, 1
FROM (
  SELECT DISTINCT
    b.source_code,
    CASE
      WHEN b.category_name IS NULL OR TRIM(b.category_name) = '' OR UPPER(TRIM(b.category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(TRIM(b.category_name), 64)
    END source_category_name,
    CASE
      WHEN b.category_name IS NULL OR TRIM(b.category_name) = '' OR UPPER(TRIM(b.category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(REPLACE(REPLACE(REPLACE(LOWER(TRIM(b.category_name)), ' ', ''), '-', ''), '_', ''), 64)
    END normalized_name
  FROM mini_novel_crawler.crawl_book_raw b
  JOIN mini_novel_crawler.crawl_source s
    ON s.source_code = b.source_code
   AND s.source_type = 'AUTHORIZED_VIP'
) seed
JOIN vip_category vc ON vc.normalized_name = seed.normalized_name
ON DUPLICATE KEY UPDATE
  source_category_name=VALUES(source_category_name),
  vip_category_id=VALUES(vip_category_id),
  enabled=1,
  updated_at=NOW();

INSERT INTO novel_vip_category_mapping(novel_id, vip_category_id, source_code, source_book_id, source_category_name)
SELECT nsm.novel_id, vc.id, nsm.source_code, nsm.source_book_id, seed.source_category_name
FROM novel_source_mapping nsm
JOIN mini_novel_crawler.crawl_book_raw b
  ON b.source_code = nsm.source_code
 AND b.source_book_id = nsm.source_book_id
JOIN mini_novel_crawler.crawl_source s
  ON s.source_code = nsm.source_code
 AND s.source_type = 'AUTHORIZED_VIP'
JOIN (
  SELECT
    source_code,
    source_book_id,
    CASE
      WHEN category_name IS NULL OR TRIM(category_name) = '' OR UPPER(TRIM(category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(TRIM(category_name), 64)
    END source_category_name,
    CASE
      WHEN category_name IS NULL OR TRIM(category_name) = '' OR UPPER(TRIM(category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(REPLACE(REPLACE(REPLACE(LOWER(TRIM(category_name)), ' ', ''), '-', ''), '_', ''), 64)
    END normalized_name
  FROM mini_novel_crawler.crawl_book_raw
) seed
  ON seed.source_code = nsm.source_code
 AND seed.source_book_id = nsm.source_book_id
JOIN vip_category vc ON vc.normalized_name = seed.normalized_name
WHERE nsm.content_status = 'CONTENT_READY'
ON DUPLICATE KEY UPDATE
  vip_category_id=VALUES(vip_category_id),
  source_code=VALUES(source_code),
  source_book_id=VALUES(source_book_id),
  source_category_name=VALUES(source_category_name),
  updated_at=NOW();

UPDATE mini_novel_crawler.crawl_book_raw
SET tags_json = CASE
  WHEN category_name IS NULL OR TRIM(category_name) = '' OR UPPER(TRIM(category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '[]'
  ELSE JSON_ARRAY(TRIM(category_name))
END,
updated_at = NOW()
WHERE source_code = 'h528_authorized';
