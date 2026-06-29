SET @has_crawl_task_id := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'mini_novel_crawler'
    AND TABLE_NAME = 'crawl_book_raw'
    AND COLUMN_NAME = 'crawl_task_id'
);

SET @add_crawl_task_id := IF(
  @has_crawl_task_id = 0,
  'ALTER TABLE mini_novel_crawler.crawl_book_raw ADD COLUMN crawl_task_id BIGINT NULL COMMENT ''采集任务 ID，关联 crawl_task_v2.id，用于限定本次清洗范围'' AFTER id',
  'SELECT 1'
);
PREPARE stmt FROM @add_crawl_task_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_crawl_task_id := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'mini_novel_crawler'
    AND TABLE_NAME = 'crawl_book_raw'
    AND INDEX_NAME = 'idx_crawl_task_id'
);

SET @add_idx_crawl_task_id := IF(
  @has_idx_crawl_task_id = 0,
  'ALTER TABLE mini_novel_crawler.crawl_book_raw ADD KEY idx_crawl_task_id (crawl_task_id)',
  'SELECT 1'
);
PREPARE stmt FROM @add_idx_crawl_task_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
