USE mini_novel_crawler;

CREATE TABLE IF NOT EXISTS xbookcn_raw_repair_cursor (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  repair_key VARCHAR(64) NOT NULL,
  last_authorized_book_id BIGINT NOT NULL DEFAULT 0,
  duplicate_raw_books INT NOT NULL DEFAULT 0,
  repaired_books INT NOT NULL DEFAULT 0,
  merged_chapters INT NOT NULL DEFAULT 0,
  merged_contents INT NOT NULL DEFAULT 0,
  preserved_statuses INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_repair_key (repair_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @add_idx_source_url := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE crawl_book_raw ADD KEY idx_source_url (source_code, source_url)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'mini_novel_crawler'
    AND TABLE_NAME = 'crawl_book_raw'
    AND INDEX_NAME = 'idx_source_url');
PREPARE stmt FROM @add_idx_source_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_idx_source_status := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE crawl_book_raw ADD KEY idx_source_status (source_code, content_status)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'mini_novel_crawler'
    AND TABLE_NAME = 'crawl_book_raw'
    AND INDEX_NAME = 'idx_source_status');
PREPARE stmt FROM @add_idx_source_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_idx_book_chapter_url := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE crawl_chapter_raw ADD KEY idx_book_chapter_url (book_raw_id, source_url)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'mini_novel_crawler'
    AND TABLE_NAME = 'crawl_chapter_raw'
    AND INDEX_NAME = 'idx_book_chapter_url');
PREPARE stmt FROM @add_idx_book_chapter_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
