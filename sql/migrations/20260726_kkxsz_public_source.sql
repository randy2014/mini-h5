CREATE DATABASE IF NOT EXISTS mini_novel_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mini_novel_crawler;

INSERT INTO crawl_source (
  source_code,
  name,
  base_url,
  source_type,
  auth_mode,
  rule_config_json,
  enabled,
  priority,
  remark
) VALUES (
  'kkxsz_public',
  '2k小说站授权免费源',
  'https://www.kkxsz.com',
  'PUBLIC',
  'NONE',
  JSON_OBJECT(
    'authorization', JSON_OBJECT(
      'proofRef', 'offline_company_agreement',
      'scope', 'metadata,catalog,chapter_content,storage,free_display'
    ),
    'rankRules', JSON_OBJECT(
      'maxPages', 1
    ),
    'chapterRules', JSON_OBJECT(
      'content', '#content || .content',
      'removeSelectors', JSON_ARRAY(
        'script',
        'style',
        '.top',
        '.headerW',
        '.navW',
        '.searchBoxM',
        '.readNav',
        '.page',
        '.recommend',
        '.chapterPages',
        'a[href*=\"rrssk.com\"]'
      ),
      'minContentLength', 80,
      'maxPages', 1,
      'rejectPatterns', JSON_ARRAY('请登录', '正在手打中', '本章未完')
    ),
    'qualityRules', JSON_OBJECT(
      'requireFullCatalog', true,
      'completeBeforeFreeMerge', true
    )
  ),
  0,
  35,
  '授权免费小说源，默认禁用；受控验证后才启用采集，完整正文才允许进入免费主站。'
) ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  base_url = VALUES(base_url),
  source_type = VALUES(source_type),
  auth_mode = VALUES(auth_mode),
  rule_config_json = VALUES(rule_config_json),
  enabled = 0,
  priority = VALUES(priority),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;

SET @kkxsz_source_id := (SELECT id FROM crawl_source WHERE source_code = 'kkxsz_public' LIMIT 1);

CREATE TEMPORARY TABLE IF NOT EXISTS tmp_kkxsz_rank_sources (
  rank_name VARCHAR(64) NOT NULL,
  rank_type VARCHAR(32) NOT NULL,
  rank_url VARCHAR(512) NOT NULL,
  sort_no INT NOT NULL
) ENGINE=Memory;

DELETE FROM tmp_kkxsz_rank_sources;

INSERT INTO tmp_kkxsz_rank_sources (rank_name, rank_type, rank_url, sort_no) VALUES
  ('玄幻魔法', 'KKXSZ_CATEGORY_04', 'https://www.kkxsz.com/list-4/', 10),
  ('修真武侠', 'KKXSZ_CATEGORY_07', 'https://www.kkxsz.com/list-7/', 20),
  ('都市小说', 'KKXSZ_CATEGORY_08', 'https://www.kkxsz.com/list-8/', 30),
  ('游戏竞技', 'KKXSZ_CATEGORY_11', 'https://www.kkxsz.com/list-11/', 40),
  ('修真武侠', 'KKXSZ_CATEGORY_17', 'https://www.kkxsz.com/list-17/', 50),
  ('言情小说', 'KKXSZ_CATEGORY_18', 'https://www.kkxsz.com/list-18/', 60),
  ('悬疑惊悚', 'KKXSZ_CATEGORY_21', 'https://www.kkxsz.com/list-21/', 70),
  ('科幻空间', 'KKXSZ_CATEGORY_22', 'https://www.kkxsz.com/list-22/', 80);

UPDATE crawl_rank_source r
JOIN tmp_kkxsz_rank_sources t ON r.rank_type = t.rank_type
SET r.rank_name = t.rank_name,
    r.rank_url = t.rank_url,
    r.prefer_completed = 0,
    r.max_books = 1,
    r.enabled = 0,
    r.updated_at = CURRENT_TIMESTAMP
WHERE r.source_id = @kkxsz_source_id;

INSERT INTO crawl_rank_source (source_id, rank_name, rank_type, rank_url, prefer_completed, max_books, enabled)
SELECT @kkxsz_source_id, t.rank_name, t.rank_type, t.rank_url, 0, 1, 0
FROM tmp_kkxsz_rank_sources t
WHERE @kkxsz_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM crawl_rank_source r
    WHERE r.source_id = @kkxsz_source_id
      AND r.rank_type = t.rank_type
  )
ORDER BY t.sort_no;

DROP TEMPORARY TABLE IF EXISTS tmp_kkxsz_rank_sources;
